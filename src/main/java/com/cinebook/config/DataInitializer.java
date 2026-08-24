package com.cinebook.config;

import com.cinebook.entity.Role;
import com.cinebook.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final RoleRepository roleRepository;

    @Override
    public void run(String... args) {
        initRoleIfAbsent("CUSTOMER", "Default customer role");
        initRoleIfAbsent("ADMIN", "System administrator role");
    }

    private void initRoleIfAbsent(String roleName, String description) {
        if (!roleRepository.existsByName(roleName)) {
            Role role = new Role();
            role.setName(roleName);
            role.setDescription(description);
            roleRepository.save(role);
            log.info("Initialized default role: {}", roleName);
        }
    }
}

