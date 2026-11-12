package com.parkflow;

import com.parkflow.model.AppUser;
import com.parkflow.model.Booking;
import com.parkflow.model.PaymentRecord;
import com.parkflow.repo.BookingRepository;
import com.parkflow.repo.PaymentRepository;
import com.parkflow.repo.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Inserts realistic, clearly identified demo records once.
 * Existing users, bookings and payments are never overwritten.
 */
@Component
@Order(2)
public class DemoDataSeeder implements CommandLineRunner {

    private static final String FIRST_DEMO_BOOKING_ID = "PFDEMO001";
    private static final String DEMO_PASSWORD = "user123";

    private final UserRepository users;
    private final BookingRepository bookings;
    private final PaymentRepository payments;
    private final BCryptPasswordEncoder encoder;

    public DemoDataSeeder(
            UserRepository users,
            BookingRepository bookings,
            PaymentRepository payments,
            BCryptPasswordEncoder encoder) {
        this.users = users;
        this.bookings = bookings;
        this.payments = payments;
        this.encoder = encoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        // A fixed first ID makes the seeder idempotent across application restarts.
        if (bookings.existsById(FIRST_DEMO_BOOKING_ID)) {
            return;
        }

        seedCustomers();
        seedBookingsAndPayments();
    }

    private void seedCustomers() {
        addCustomer("Priya Sharma", "priya@parkflow.demo");
        addCustomer("Rahul Verma", "rahul@parkflow.demo");
        addCustomer("Ananya Reddy", "ananya@parkflow.demo");
        addCustomer("Arjun Mehta", "arjun@parkflow.demo");
    }

    private void addCustomer(String name, String email) {
        if (users.findByEmail(email).isEmpty()) {
            users.save(new AppUser(name, email, encoder.encode(DEMO_PASSWORD), "user"));
        }
    }

    private void seedBookingsAndPayments() {
        LocalDateTime now = LocalDateTime.now().withSecond(0).withNano(0);
        String[] customerEmails = {
                "user@parkflow.demo",
                "priya@parkflow.demo",
                "rahul@parkflow.demo",
                "ananya@parkflow.demo",
                "arjun@parkflow.demo"
        };
        String[] plates = {
                "TS09EA1024", "TS08FK2451", "TS10GH7812", "TS09AB4567",
                "TS07CD9081", "TS09MN3321", "TS08PQ7744", "TS10RS1290",
                "TS09TU5643", "TS07VW8821", "TS09XY3412", "TS08ZA6754",
                "TS10BC2198", "TS09DE7432", "TS07FG1560", "TS08HJ9043",
                "TS09KL6281", "TS10LM4375", "TS07NP8162", "TS08QR3509"
        };
        String[] types = {
                "Car", "Bike", "SUV", "Car", "Bike",
                "Car", "SUV", "Bike", "Car", "SUV",
                "Car", "Bike", "Car", "SUV", "Bike",
                "Car", "SUV", "Car", "Bike", "Car"
        };
        int[] hours = {3, 2, 2, 2, 4, 1, 3, 2, 2, 4, 1, 3, 2, 2, 4, 1, 3, 2, 2, 3};
        String[] methods = {"UPI", "CASH", "CARD", "DEMO"};

        for (int i = 0; i < 20; i++) {
            Booking booking = new Booking();
            booking.id = String.format("PFDEMO%03d", i + 1);
            booking.email = customerEmails[i % customerEmails.length];
            booking.plate = plates[i];
            booking.type = types[i];
            booking.slot = i + 1;
            booking.hours = hours[i];

            int rate = switch (booking.type) {
                case "Bike" -> 15;
                case "SUV" -> 45;
                default -> 30;
            };
            booking.amount = rate * booking.hours;

            LocalDateTime createdAt;
            boolean paid;

            if (i == 0 || i == 1) {
                // Two currently running, paid sessions.
                booking.status = "active";
                booking.startAt = now.minusMinutes(i == 0 ? 35 : 15);
                createdAt = booking.startAt.minusHours(1);
                paid = true;
            } else if (i == 2) {
                // One upcoming confirmed reservation.
                booking.status = "confirmed";
                booking.startAt = now.plusHours(2);
                createdAt = now.minusHours(2);
                paid = true;
            } else if (i >= 3 && i <= 6) {
                // Four unpaid reservations, including future reservations.
                booking.status = "pending_payment";
                booking.startAt = now.plusDays(i - 2).withHour(9 + (i % 5)).withMinute(0);
                createdAt = now.minusDays(i - 3).minusHours(2);
                paid = false;
            } else {
                // Historical completed bookings, spread across recent weeks.
                int daysAgo = 1 + (i % 14);
                createdAt = now.minusDays(daysAgo).withHour(9 + (i % 8)).withMinute(15);
                booking.startAt = createdAt.plusHours(1);
                booking.status = "completed";
                paid = true;
            }

            booking.createdAt = createdAt;

            if (paid) {
                String paymentId = String.format("DEMO-PAY-%03d", i + 1);
                booking.paymentId = paymentId;
                bookings.save(booking);

                PaymentRecord payment = new PaymentRecord(
                        booking.id,
                        booking.email,
                        booking.amount,
                        paymentId,
                        methods[i % methods.length],
                        "paid");
                // Keep payment dates aligned with the booking history for reports.
                payment.createdAt = createdAt;
                payments.save(payment);
            } else {
                bookings.save(booking);
            }
        }
    }
}
