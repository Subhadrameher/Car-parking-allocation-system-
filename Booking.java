
package com.parkflow.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "bookings")
public class Booking {

    @Id
    public String id;

    @Column(nullable = false)
    public String email;

    @Column(nullable = false)
    public String plate;

    public String type;

    @Column(nullable = false)
    public Integer slot;

    @Column(nullable = false)
    public LocalDateTime startAt;

    @Column(nullable = false)
    public Integer hours;

    @Column(nullable = false)
    public Integer amount;

    @Column(nullable = false)
    public String status;

    @Column(nullable = false)
    public LocalDateTime createdAt;

    public String paymentId;

    public Booking() {
    }
}
