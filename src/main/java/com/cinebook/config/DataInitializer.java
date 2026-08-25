package com.cinebook.config;

import com.cinebook.entity.Role;
import com.cinebook.entity.SeatType;
import com.cinebook.enums.SeatTypeStatus;
import com.cinebook.repository.RoleRepository;
import com.cinebook.repository.SeatTypeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final RoleRepository roleRepository;
    private final SeatTypeRepository seatTypeRepository;

    @Override
    public void run(String... args) {
        initRoleIfAbsent("CUSTOMER", "Default customer role");
        initRoleIfAbsent("ADMIN", "System administrator role");

        initSeatTypeIfAbsent("STANDARD", BigDecimal.ZERO, "Standard comfortable cinema seat");
        initSeatTypeIfAbsent("VIP", new BigDecimal("20000.00"), "VIP premium cinema seat with extra legroom");
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
}