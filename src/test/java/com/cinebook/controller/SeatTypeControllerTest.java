package com.cinebook.controller;

import com.cinebook.dto.response.SeatTypeResponse;
import com.cinebook.enums.SeatTypeStatus;
import com.cinebook.exception.GlobalExceptionHandler;
import com.cinebook.service.SeatTypeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SeatTypeControllerTest {

    @Mock
    private SeatTypeService seatTypeService;

    @InjectMocks
    private SeatTypeController seatTypeController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(seatTypeController)
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getActiveSeatTypes_Returns200() throws Exception {
        SeatTypeResponse st = SeatTypeResponse.builder()
                .id("st-1")
                .name("STANDARD")
                .priceModifier(BigDecimal.ZERO)
                .status(SeatTypeStatus.ACTIVE)
                .build();

        when(seatTypeService.getAllActiveSeatTypes()).thenReturn(List.of(st));

        mockMvc.perform(get("/api/v1/seat-types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("STANDARD"));
    }
}