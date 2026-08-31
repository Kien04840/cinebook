package com.cinebook.config;

import com.cinebook.entity.Role;
import com.cinebook.entity.SeatType;
import com.cinebook.entity.User;
import com.cinebook.entity.UserRole;
import com.cinebook.entity.UserRoleId;
import com.cinebook.enums.SeatTypeStatus;
import com.cinebook.enums.UserStatus;
import com.cinebook.repository.RoleRepository;
import com.cinebook.repository.SeatTypeRepository;
import com.cinebook.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final RoleRepository roleRepository;
    private final SeatTypeRepository seatTypeRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        Role customerRole = initRoleIfAbsent("CUSTOMER", "Default customer role");
        Role adminRole = initRoleIfAbsent("ADMIN", "System administrator role");

        initSeatTypeIfAbsent("STANDARD", BigDecimal.ZERO, "Standard comfortable cinema seat");
        initSeatTypeIfAbsent("VIP", new BigDecimal("20000.00"), "VIP premium cinema seat with extra legroom");

        initAdminUserIfAbsent(adminRole, customerRole);
    }

    private Role initRoleIfAbsent(String roleName, String description) {
        return roleRepository.findByName(roleName).orElseGet(() -> {
            Role role = new Role();
            role.setName(roleName);
            role.setDescription(description);
            Role saved = roleRepository.save(role);
            log.info("Initialized default role: {}", roleName);
            return saved;
        });
    }

    private void initSeatTypeIfAbsent(String name, BigDecimal priceModifier, String description) {
        if (!seatTypeRepository.existsByNameIgnoreCase(name)) {
            SeatType seatType = new SeatType();
            seatType.setName(name);
            seatType.setPriceModifier(priceModifier);
            seatType.setDescription(description);
            seatType.setStatus(SeatTypeStatus.ACTIVE);
            seatTypeRepository.save(seatType);
            log.info("Initialized default seat type: {}", name);
        }
    }

    private void initAdminUserIfAbsent(Role adminRole, Role customerRole) {
        User admin = userRepository.findByEmail("admin@cinebook.com").orElse(null);
        if (admin == null) {
            admin = new User();
            admin.setId(UUID.randomUUID().toString());
            admin.setEmail("admin@cinebook.com");
            admin.setPasswordHash(passwordEncoder.encode("Password123@"));
            admin.setFullName("System Administrator");
            admin.setPhone("0900000000");
            admin.setStatus(UserStatus.ACTIVE);
            admin.setEmailVerified(true);

            if (adminRole != null) {
                UserRole urAdmin = new UserRole();
                urAdmin.setId(new UserRoleId(admin.getId(), adminRole.getId()));
                urAdmin.setUser(admin);
                urAdmin.setRole(adminRole);
                admin.addUserRole(urAdmin);
            }

            if (customerRole != null) {
                UserRole urCustomer = new UserRole();
                urCustomer.setId(new UserRoleId(admin.getId(), customerRole.getId()));
                urCustomer.setUser(admin);
                urCustomer.setRole(customerRole);
                admin.addUserRole(urCustomer);
            }

            userRepository.save(admin);
            log.info("Initialized default admin user: admin@cinebook.com");
        } else {
            admin.setPasswordHash(passwordEncoder.encode("Password123@"));
            admin.setStatus(UserStatus.ACTIVE);
            userRepository.save(admin);
            log.info("Reset password for default admin user: admin@cinebook.com");
        }
    }
}