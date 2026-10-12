package com.example.snjpro_backend.config;


import com.example.snjpro_backend.entity.User;
import com.example.snjpro_backend.enumeration.RoleEnum;
import com.example.snjpro_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (!userRepository.existsByUsername("admin")) {
            User admin = User.builder()
                    .username("admin")
                    .password(passwordEncoder.encode("Admin@2026"))
                    .email("admin@snj.ml")
                    .prenom("Administrateur")
                    .nom("Système")
                    .role(RoleEnum.ROLE_ADMIN_SYSTEME)
                    .actif(true)
                    .createdBy("system")
                    .build();
            userRepository.save(admin);
            log.info("Compte admin créé : admin / Admin@2026");
        }
    }
}
