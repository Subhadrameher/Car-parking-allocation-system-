
package com.parkflow.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment_records")
public class PaymentRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    public String bookingId;
    public String email;
    public Integer amount;
    public String paymentId;
    public String paymentMethod;
    public String status;
    public LocalDateTime createdAt;

    protected PaymentRecord() {
    }

    // Backward-compatible constructor
    public PaymentRecord(String b, String e, Integer a, String p, String s) {
        this(b, e, a, p, "DEMO", s);
    }

    // Constructor with payment method
    public PaymentRecord(
            String b,
            String e,
            Integer a,
            String p,
            String method,
            String s) {

        bookingId = b;
        email = e;
        amount = a;
        paymentId = p;
        paymentMethod = method;
        status = s;
        createdAt = LocalDateTime.now();
    }
}
