package com.taxfiling.config;

import com.taxfiling.model.User;
import com.taxfiling.model.enums.UserRole;
import com.taxfiling.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@Profile("!test") // Don't run in test profile
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    // Default admin credentials - CHANGE IN PRODUCTION!
    private static final String ADMIN_EMAIL = "admin@taxfiling.com";
    private static final String ADMIN_PASSWORD = "admin123";

    @Override
    public void run(String... args) {
        createDefaultAdmin();
    }

    private void createDefaultAdmin() {
        if (userRepository.existsByEmail(ADMIN_EMAIL)) {
            log.info("Admin user already exists: {}", ADMIN_EMAIL);
            return;
        }

        User admin = User.builder()
                .email(ADMIN_EMAIL)
                .passwordHash(passwordEncoder.encode(ADMIN_PASSWORD))
                .role(UserRole.ADMIN)
                .firstName("System")
                .lastName("Admin")
                .build();

        userRepository.save(admin);
        log.info("Default admin user created: {}", ADMIN_EMAIL);
        log.warn(">>> DEFAULT ADMIN CREDENTIALS - CHANGE IN PRODUCTION! <<<");
        log.warn(">>> Email: {} | Password: {} <<<", ADMIN_EMAIL, ADMIN_PASSWORD);
    }
}
