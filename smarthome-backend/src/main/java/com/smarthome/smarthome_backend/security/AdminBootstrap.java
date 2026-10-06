package com.smarthome.smarthome_backend.security;

import com.smarthome.smarthome_backend.entity.User;
import com.smarthome.smarthome_backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class AdminBootstrap {
    @Bean
    CommandLineRunner createConfiguredAdmin(
            UserRepository users,
            PasswordEncoder passwordEncoder,
            @Value("${app.bootstrap-admin.email}") String email,
            @Value("${app.bootstrap-admin.password}") String password) {
        return args -> {
            if (email.isBlank() && password.isBlank()) return;
            if (email.isBlank() || password.isBlank()) {
                throw new IllegalStateException("Set both ADMIN_EMAIL and ADMIN_PASSWORD to bootstrap an administrator");
            }
            var existing = users.findByEmailIgnoreCase(email.trim());
            var admin = existing.orElseGet(User::new);
            if (existing.isEmpty()) {
                admin.setFirstname("Admin");
                admin.setLastname("SmartHome");
                admin.setEmail(email.trim().toLowerCase());
                admin.setPassword(passwordEncoder.encode(password));
            }
            admin.setRole("ADMIN");
            users.save(admin);
        };
    }
}
