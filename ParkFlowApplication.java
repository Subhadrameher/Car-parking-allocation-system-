package com.parkflow;

import com.parkflow.model.AppUser;
import com.parkflow.model.ParkingSlot;
import com.parkflow.repo.SlotRepository;
import com.parkflow.repo.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@SpringBootApplication
public class ParkFlowApplication {

    public static void main(String[] args) {
        SpringApplication.run(ParkFlowApplication.class, args);
    }

    @Bean
    BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    @Order(1)
    CommandLineRunner seedBaseData(
            UserRepository users,
            SlotRepository slots,
            BCryptPasswordEncoder encoder) {

        return args -> {
            if (users.findByEmail("admin@parkflow.demo").isEmpty()) {
                users.save(new AppUser(
                        "Administrator",
                        "admin@parkflow.demo",
                        encoder.encode("admin123"),
                        "admin"));
            }

            if (users.findByEmail("user@parkflow.demo").isEmpty()) {
                users.save(new AppUser(
                        "Demo User",
                        "user@parkflow.demo",
                        encoder.encode("user123"),
                        "user"));
            }

            if (slots.count() == 0) {
                for (int i = 1; i <= 48; i++) {
                    slots.save(new ParkingSlot(i, "available"));
                }
            }
        };
    }
}
