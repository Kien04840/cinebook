package com.cinebook.security;

import com.cinebook.service.ReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class ReportSecurityTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @MockitoBean
    private ReportService reportService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    @DisplayName("Security: Anonymous cannot access /api/v1/admin/reports/dashboard -> 401 Unauthorized")
    void anonymous_GetDashboard_Returns401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/reports/dashboard"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "customer@cinebook.com", roles = {"CUSTOMER"})
    @DisplayName("Security: Customer cannot access /api/v1/admin/reports/dashboard -> 403 Forbidden")
    void customer_GetDashboard_Returns403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/reports/dashboard"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin@cinebook.com", roles = {"ADMIN"})
    @DisplayName("Security: Admin can access /api/v1/admin/reports/dashboard -> 200 OK")
    void admin_GetDashboard_Returns200() throws Exception {
        mockMvc.perform(get("/api/v1/admin/reports/dashboard"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Security: Anonymous cannot access /api/v1/admin/reports/export -> 401 Unauthorized")
    void anonymous_Export_Returns401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/reports/export"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "customer@cinebook.com", roles = {"CUSTOMER"})
    @DisplayName("Security: Customer cannot access /api/v1/admin/reports/export -> 403 Forbidden")
    void customer_Export_Returns403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/reports/export"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin@cinebook.com", roles = {"ADMIN"})
    @DisplayName("Security: Admin can access /api/v1/admin/reports/export -> 200 OK")
    void admin_Export_Returns200() throws Exception {
        mockMvc.perform(get("/api/v1/admin/reports/export"))
                .andExpect(status().isOk());
    }
}

