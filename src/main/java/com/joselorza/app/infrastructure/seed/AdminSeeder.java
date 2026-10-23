package com.joselorza.app.infrastructure.seed;

import com.joselorza.app.application.port.out.UserRepository;
import com.joselorza.app.domain.model.Role;
import com.joselorza.app.domain.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AdminSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminSeedProperties adminSeedProperties;

    @Override
    public void run(String... args) {
        if (userRepository.existsByRole(Role.ADMIN)) {
            log.info("An ADMIN user already exists, the initial seed is skipped.");
            return;
        }

        User admin = User.builder()
                .name(adminSeedProperties.getName())
                .email(adminSeedProperties.getEmail())
                .passwordHash(passwordEncoder.encode(adminSeedProperties.getPassword()))
                .role(Role.ADMIN)
                .timezone(adminSeedProperties.getTimezone())
                .notifyEmail(true)
                .isActive(true)
                .build();

        userRepository.save(admin);
        log.info("Initial ADMIN user created: {}", admin.getEmail());
    }
}