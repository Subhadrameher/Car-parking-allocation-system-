package com.parkflow.api;

import com.parkflow.model.*;
import com.parkflow.repo.*;
import com.parkflow.security.*;
import org.springframework.http.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import java.time.*;
import java.util.*;

@RestController
@RequestMapping("/api")
public class ApiController {
    private final UserRepository users;
    private final SlotRepository slots;
    private final BookingRepository bookings;
    private final PaymentRepository payments;
    private final BCryptPasswordEncoder encoder;
    private final SessionStore sessions;

    public ApiController(UserRepository u, SlotRepository s, BookingRepository b, PaymentRepository p,
            BCryptPasswordEncoder e, SessionStore ss) {
        users = u;
        slots = s;
        bookings = b;
        payments = p;
        encoder = e;
        sessions = ss;
    }

    private SessionStore.Principal me(HttpServletRequest r) {
        var p = (SessionStore.Principal) r.getAttribute("principal");
        if (p == null)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please sign in again.");
        return p;
    }

    private void admin(HttpServletRequest r) {
        if (!"admin".equals(me(r).role()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required.");
    }

    @PostMapping("/auth/login")
    public Map<String, Object> login(@RequestBody Map<String, String> body) {
        String email = body.getOrDefault("email", "").trim().toLowerCase();
        AppUser u = users.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password."));
        if (!encoder.matches(body.getOrDefault("password", ""), u.password))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password.");
        String token = sessions.create(u);
        return Map.of("token", token, "email", u.email, "role", u.role, "name", u.name);
    }

    @PostMapping("/auth/logout")
    public Map<String, Boolean> logout(HttpServletRequest r) {
        String h = r.getHeader("Authorization");
        if (h != null && h.startsWith("Bearer "))
            sessions.remove(h.substring(7));
        return Map.of("ok", true);
    }

    @GetMapping("/auth/me")
    public Map<String, Object> profile(HttpServletRequest r) {
        var p = me(r);
        return Map.of("id", p.id(), "name", p.name(), "email", p.email(), "role", p.role());
    }

    @GetMapping("/slots")
    public List<Map<String, Object>> slotList() {
        List<Booking> all = bookings.findAll();
        return slots.findAll().stream().map(s -> {
            boolean occupied = all.stream()
                    .anyMatch(b -> b.slot.equals(s.id)
                            && List.of("pending_payment", "confirmed", "active").contains(b.status)
                            && b.startAt.plusHours(b.hours).isAfter(LocalDateTime.now()));
            return Map.<String, Object>of("id", s.id, "status", occupied ? "occupied" : "available");
        }).toList();
    }

    @PostMapping("/bookings")
    @Transactional
    public Booking createBooking(@RequestBody Map<String, Object> body, HttpServletRequest r) {
        var p = me(r);
        int slot = ((Number) body.get("slot")).intValue(), hours = ((Number) body.get("hours")).intValue();
        String plate = String.valueOf(body.getOrDefault("plate", "")).trim().toUpperCase(),
                type = String.valueOf(body.getOrDefault("type", "Car"));
        LocalDateTime start = LocalDateTime.parse(String.valueOf(body.get("startAt")));
        if (plate.isBlank() || hours < 1 || hours > 24 || start.isBefore(LocalDateTime.now().minusMinutes(1)))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter valid booking details.");
        if (!slots.existsById(slot))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid parking slot.");
        LocalDateTime end = start.plusHours(hours);
        boolean conflict = bookings.findAll().stream()
                .anyMatch(b -> b.slot == slot && List.of("pending_payment", "confirmed", "active").contains(b.status)
                        && b.startAt.isBefore(end) && b.startAt.plusHours(b.hours).isAfter(start));
        if (conflict)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This slot is no longer available for that time.");
        int rate = switch (type) {
            case "Bike" -> 15;
            case "SUV" -> 45;
            default -> 30;
        };
        Booking b = new Booking();
        b.id = "PF" + System.currentTimeMillis();
        b.email = p.email();
        b.plate = plate;
        b.type = type;
        b.slot = slot;
        b.startAt = start;
        b.hours = hours;
        b.amount = rate * hours;
        b.status = "pending_payment";
        b.createdAt = LocalDateTime.now();
        return bookings.save(b);
    }

    @GetMapping("/bookings/mine")
    public List<Booking> myBookings(HttpServletRequest r) {
        return bookings.findByEmailOrderByCreatedAtDesc(me(r).email());
    }

    @GetMapping("/admin/bookings")
    public List<Booking> allBookings(HttpServletRequest r) {
        admin(r);
        return bookings.findAllByOrderByCreatedAtDesc();
    }

    @GetMapping("/admin/payments")
    public List<PaymentRecord> allPayments(HttpServletRequest r) {
        admin(r);
        return payments.findAllByOrderByCreatedAtDesc();
    }

    @PostMapping("/payments/demo/{bookingId}")
    @Transactional
    public Map<String, Object> demoPay(@PathVariable String bookingId, HttpServletRequest r) {
        var p = me(r);
        Booking b = bookings.findById(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found."));
        if (!b.email.equals(p.email()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This booking belongs to another user.");
        if ("confirmed".equals(b.status))
            return Map.of("verified", true, "paymentId", b.paymentId == null ? "already_paid" : b.paymentId);
        if (!"pending_payment".equals(b.status))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Booking cannot be paid.");
        String paymentId = "demo_pay_" + UUID.randomUUID().toString().substring(0, 8);
        b.status = "confirmed";
        b.paymentId = paymentId;
        bookings.save(b);
        payments.save(new PaymentRecord(b.id, b.email, b.amount, paymentId, "paid"));
        return Map.of("verified", true, "paymentId", paymentId, "message",
                "Demo payment recorded. No real money was charged.");
    }

    @PostMapping("/admin/bookings")
    @Transactional
    public Booking createAdminBooking(
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {

        admin(request);

        String email = String.valueOf(body.getOrDefault("email", ""))
                .trim().toLowerCase();

        String plate = String.valueOf(body.getOrDefault("plate", ""))
                .trim().toUpperCase();

        String type = String.valueOf(body.getOrDefault("type", "Car")).trim();

        if (email.isBlank() || plate.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Customer email and vehicle number are required.");
        }

        int slot;
        int hours;

        try {
            slot = Integer.parseInt(String.valueOf(body.get("slot")));
            hours = Integer.parseInt(String.valueOf(body.get("hours")));
        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Invalid slot or duration.");
        }

        if (hours < 1 || hours > 24) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Duration must be between 1 and 24 hours.");
        }

        if (!List.of("Car", "Bike", "SUV").contains(type)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Invalid vehicle type.");
        }

        if (!slots.existsById(slot)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Parking slot does not exist.");
        }

        LocalDateTime startAt;

        try {
            startAt = LocalDateTime.parse(String.valueOf(body.get("startAt")));
        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Invalid booking date or time.");
        }

        if (startAt.isBefore(LocalDateTime.now().minusMinutes(1))) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Booking cannot start in the past.");
        }

        LocalDateTime endAt = startAt.plusHours(hours);

        boolean conflict = bookings.findAll().stream()
                .anyMatch(b -> Objects.equals(b.slot, slot)
                        && List.of("pending_payment", "confirmed", "active")
                                .contains(b.status)
                        && b.startAt.isBefore(endAt)
                        && b.startAt.plusHours(b.hours).isAfter(startAt));

        if (conflict) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This slot is already booked for the selected time.");
        }

        boolean paid = Boolean.parseBoolean(
                String.valueOf(body.getOrDefault("paid", "false")));

        String method = String.valueOf(
                body.getOrDefault("paymentMethod", "CASH"))
                .trim().toUpperCase();

        if (paid && !List.of("CASH", "UPI", "CARD", "DEMO").contains(method)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Invalid payment method.");
        }

        int rate = switch (type) {
            case "Bike" -> 15;
            case "SUV" -> 45;
            default -> 30;
        };

        Booking booking = new Booking();

        booking.id = "PF" + UUID.randomUUID()
                .toString().replace("-", "")
                .substring(0, 12).toUpperCase();

        booking.email = email;
        booking.plate = plate;
        booking.type = type;
        booking.slot = slot;
        booking.startAt = startAt;
        booking.hours = hours;
        booking.amount = rate * hours;
        booking.createdAt = LocalDateTime.now();

        if (paid) {
            String paymentId = "ADMIN_" + method + "_"
                    + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

            booking.status = "confirmed";
            booking.paymentId = paymentId;

            bookings.save(booking);

            payments.save(new PaymentRecord(
                    booking.id,
                    booking.email,
                    booking.amount,
                    paymentId,
                    method,
                    "paid"));
        } else {
            booking.status = "pending_payment";
            bookings.save(booking);
        }

        return booking;
    }

    @PostMapping("/admin/bookings/{bookingId}/pay")
    @Transactional
    public Map<String, Object> adminPayBooking(
            @PathVariable String bookingId,
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {

        admin(request);

        Booking booking = bookings.findById(bookingId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Booking not found."));

        if (!"pending_payment".equals(booking.status)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "This booking cannot be paid. Current status: " + booking.status);
        }

        String method = String.valueOf(
                body.getOrDefault("method", "CASH"))
                .trim().toUpperCase();

        if (!List.of("CASH", "UPI", "CARD", "DEMO").contains(method)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Invalid payment method.");
        }

        String paymentId = "ADMIN_" + method + "_"
                + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        booking.status = "confirmed";
        booking.paymentId = paymentId;

        bookings.save(booking);

        PaymentRecord payment = new PaymentRecord(
                booking.id,
                booking.email,
                booking.amount,
                paymentId,
                method,
                "paid");

        payments.save(payment);

        return Map.of(
                "success", true,
                "message", "Payment recorded successfully.",
                "bookingId", booking.id,
                "amount", booking.amount,
                "paymentMethod", method,
                "paymentId", paymentId,
                "status", "paid");
    }

}
