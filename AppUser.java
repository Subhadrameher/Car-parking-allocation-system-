package com.parkflow.model;

import jakarta.persistence.*;

@Entity
@Table(name = "app_users")
public class AppUser {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public String name;
    @Column(unique = true, nullable = false)
    public String email;
    @Column(nullable = false)
    public String password;
    @Column(nullable = false)
    public String role;

    protected AppUser() {
    }

    public AppUser(String n, String e, String p, String r) {
        name = n;
        email = e;
        password = p;
        role = r;
    }
}
