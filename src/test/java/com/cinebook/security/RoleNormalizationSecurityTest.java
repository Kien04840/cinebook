package com.cinebook.security;

import com.cinebook.dto.response.BookingSummaryResponse;
import com.cinebook.dto.response.PageResponse;
import com.cinebook.dto.response.UserProfileResponse;
import com.cinebook.dto.response.UserResponse;
import com.cinebook.entity.Role;
import com.cinebook.entity.User;
import com.cinebook.entity.UserRole;
import com.cinebook.entity.UserRoleId;
import com.cinebook.enums.UserStatus;
import com.cinebook.mapper.UserMapper;
import com.cinebook.service.BookingService;
import com.cinebook.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class RoleNormalizationSecurityTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserMapper userMapper;

    @MockitoBean
    private BookingService bookingService;

    @MockitoBean
    private UserService userService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    @DisplayName("DB Role 'ADMIN' maps to GrantedAuthority 'ROLE_ADMIN' and isAdmin() is true")
    void testAdminRoleMapping() {
        User admin = new User();
        admin.setId("admin-1");
        admin.setEmail("admin@cinebook.com");
        admin.setStatus(UserStatus.ACTIVE);

        Role role = new Role();
        role.setId("role-admin");
        role.setName("ADMIN");

        UserRole userRole = new UserRole();
        userRole.setId(new UserRoleId(admin.getId(), role.getId()));
        userRole.setUser(admin);
        userRole.setRole(role);
        admin.addUserRole(userRole);

        UserDetailsImpl userDetails = UserDetailsImpl.build(admin);

        assertThat(userDetails.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_ADMIN");

        assertThat(userDetails.isAdmin()).isTrue();
        assertThat(userDetails.isCustomer()).isFalse();

        // DTO mapping check
        UserResponse response = userMapper.toUserResponse(admin);
        assertThat(response.getRoles()).containsExactly("ADMIN");

        UserProfileResponse profileResponse = userMapper.toUserProfileResponse(admin);
        assertThat(profileResponse.getRoles()).containsExactly("ADMIN");
    }

    @Test
    @DisplayName("DB Role 'CUSTOMER' maps to GrantedAuthority 'ROLE_CUSTOMER' and isCustomer() is true")
    void testCustomerRoleMapping() {
        User customer = new User();
        customer.setId("cust-1");
        customer.setEmail("cust@cinebook.com");
        customer.setStatus(UserStatus.ACTIVE);

        Role role = new Role();
        role.setId("role-customer");
        role.setName("CUSTOMER");

        UserRole userRole = new UserRole();
        userRole.setId(new UserRoleId(customer.getId(), role.getId()));
        userRole.setUser(customer);
        userRole.setRole(role);
        customer.addUserRole(userRole);

        UserDetailsImpl userDetails = UserDetailsImpl.build(customer);

        assertThat(userDetails.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_CUSTOMER");

        assertThat(userDetails.isAdmin()).isFalse();
        assertThat(userDetails.isCustomer()).isTrue();

        UserResponse response = userMapper.toUserResponse(customer);
        assertThat(response.getRoles()).containsExactly("CUSTOMER");
    }

    @Test
    @DisplayName("Anonymous access to /api/v1/admin/bookings returns 401")
    void testAnonymousAdminAccessDenied() throws Exception {
        mockMvc.perform(get("/api/v1/admin/bookings"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = {"CUSTOMER"})
    @DisplayName("Customer access to /api/v1/admin/bookings returns 403 Forbidden")
    void testCustomerAdminAccessDenied() throws Exception {
        mockMvc.perform(get("/api/v1/admin/bookings"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    @DisplayName("Admin access to /api/v1/admin/bookings is ALLOWED (200 OK)")
    void testAdminAccessAllowed() throws Exception {
        when(bookingService.getAdminBookings(any(), any(), any(), any(Pageable.class)))
                .thenReturn(PageResponse.of(new PageImpl<BookingSummaryResponse>(List.of(), PageRequest.of(0, 10), 0), b -> b));

        mockMvc.perform(get("/api/v1/admin/bookings"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = {"CUSTOMER"})
    @DisplayName("Customer access to /api/v1/admin/users returns 403 Forbidden")
    void testCustomerUsersAccessDenied() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    @DisplayName("Admin access to /api/v1/admin/users is ALLOWED (200 OK)")
    void testAdminUsersAccessAllowed() throws Exception {
        when(userService.getAdminUsers(any(), any(), any(Pageable.class)))
                .thenReturn(PageResponse.of(new PageImpl<UserProfileResponse>(List.of(), PageRequest.of(0, 10), 0), u -> u));

        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isOk());
    }
}