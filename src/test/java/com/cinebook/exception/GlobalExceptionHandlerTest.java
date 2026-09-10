package com.cinebook.exception;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;
    private GlobalExceptionHandler exceptionHandler;

    @Getter
    @Setter
    public static class TestRequestBody {
        @NotNull(message = "Name cannot be null")
        private String name;
        private Integer count;
    }

    @RestController
    static class DummyTestController {
        @GetMapping("/test/app-exception")
        public void throwAppException() {
            throw new BadRequestException("Invalid seat requested", ErrorCode.SEAT_NOT_AVAILABLE);
        }

        @GetMapping("/test/app-exception-default-code")
        public void throwAppExceptionDefaultCode() {
            throw new AppException("Generic app error", HttpStatus.PAYMENT_REQUIRED);
        }

        @PostMapping("/test/validation")
        public void validateBody(@Valid @RequestBody TestRequestBody body) {
        }

        @GetMapping("/test/500")
        public void throwInternalError() {
            throw new RuntimeException("SELECT * FROM users WHERE password_hash = 'secret123'");
        }

        @GetMapping("/test/access-denied")
        public void throwAccessDenied() {
            throw new AccessDeniedException("Forbidden action");
        }

        @GetMapping("/test/bad-credentials")
        public void throwBadCredentials() {
            throw new BadCredentialsException("Bad user password");
        }

        @GetMapping("/test/illegal-argument")
        public void throwIllegalArgument() {
            throw new IllegalArgumentException("Illegal parameter value");
        }
    }

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
        mockMvc = MockMvcBuilders.standaloneSetup(new DummyTestController())
                .setControllerAdvice(exceptionHandler)
                .build();
    }

    @Test
    @DisplayName("AppException with explicit ErrorCode populates code and status")
    void handleAppException_WithErrorCode() throws Exception {
        mockMvc.perform(get("/test/app-exception"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("SEAT_NOT_AVAILABLE"))
                .andExpect(jsonPath("$.message").value("Invalid seat requested"));
    }

    @Test
    @DisplayName("AppException without code falls back to HttpStatus.name()")
    void handleAppException_FallbackToStatusName() throws Exception {
        mockMvc.perform(get("/test/app-exception-default-code"))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.status").value(402))
                .andExpect(jsonPath("$.code").value("PAYMENT_REQUIRED"))
                .andExpect(jsonPath("$.message").value("Generic app error"));
    }

    @Test
    @DisplayName("MethodArgumentNotValidException returns 400 with VALIDATION_FAILED code and field details")
    void handleValidationException_ReturnsValidationFailed() throws Exception {
        mockMvc.perform(post("/test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[0].field").value("name"))
                .andExpect(jsonPath("$.details[0].message").value("Name cannot be null"));
    }

    @Test
    @DisplayName("HttpMessageNotReadableException returns 400 with BAD_REQUEST code and does not leak class names")
    void handleHttpMessageNotReadableException_ReturnsBadRequestWithoutLeak() throws Exception {
        // Malformed JSON (unclosed brace)
        mockMvc.perform(post("/test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"count\": \"not-a-number\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("Dữ liệu yêu cầu không hợp lệ hoặc sai định dạng."))
                .andExpect(jsonPath("$.message", not(containsString("jackson"))))
                .andExpect(jsonPath("$.message", not(containsString("com.cinebook"))));
    }

    @Test
    @DisplayName("500 Internal Server Error hides ex.getMessage() and does not leak DB queries or secrets")
    void handleGenericException_DoesNotLeakSensitiveMessage() throws Exception {
        mockMvc.perform(get("/test/500"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.message").value("Đã xảy ra lỗi không mong muốn trên hệ thống. Vui lòng thử lại sau."))
                .andExpect(jsonPath("$.message", not(containsString("SELECT"))))
                .andExpect(jsonPath("$.message", not(containsString("password_hash"))));
    }

    @Test
    @DisplayName("BadCredentialsException returns 401 with AUTH_FAILED code")
    void handleBadCredentialsException_ReturnsAuthFailed() throws Exception {
        mockMvc.perform(get("/test/bad-credentials"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("AUTH_FAILED"))
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    @DisplayName("AuthenticationException returns 401 with UNAUTHORIZED code")
    void handleAuthenticationException_ReturnsUnauthorized() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/test/auth-fail");
        AuthenticationException authEx = new AuthenticationException("Full authentication is required") {};
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleAuthenticationException(authEx, request);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("UNAUTHORIZED", response.getBody().getCode());
        assertEquals("Full authentication is required", response.getBody().getMessage());
    }

    @Test
    @DisplayName("AccessDeniedException returns 403 with FORBIDDEN code")
    void handleAccessDeniedException_ReturnsForbidden() throws Exception {
        mockMvc.perform(get("/test/access-denied"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("Access is denied"));
    }

    @Test
    @DisplayName("IllegalArgumentException returns 400 with BAD_REQUEST code")
    void handleIllegalArgumentException_ReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/test/illegal-argument"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("Illegal parameter value"));
    }

    @Test
    @DisplayName("TmdbAuthException does not mention TMDB_API_KEY and sets TMDB_SERVICE_UNAVAILABLE code")
    void tmdbAuthException_DoesNotMentionTmdbApiKey() {
        TmdbAuthException ex = new TmdbAuthException("Invalid token 401");
        assertFalse(ex.getMessage().contains("TMDB_API_KEY"));
        assertEquals("TMDB authentication failed: Invalid token 401", ex.getMessage());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatus());
        assertEquals("TMDB_SERVICE_UNAVAILABLE", ex.getCode());
    }

    @Test
    @DisplayName("Exception subclasses support String code and ErrorCode enum constructors")
    void exceptionSubclasses_SupportErrorCodeConstructors() {
        BadRequestException badReq = new BadRequestException("Bad input", ErrorCode.BAD_REQUEST);
        assertEquals("BAD_REQUEST", badReq.getCode());
        assertEquals("Bad input", badReq.getMessage());
        assertEquals(HttpStatus.BAD_REQUEST, badReq.getStatus());

        ConflictException conflict = new ConflictException("Seat held", ErrorCode.SEAT_ALREADY_HELD);
        assertEquals("SEAT_ALREADY_HELD", conflict.getCode());
        assertEquals(HttpStatus.CONFLICT, conflict.getStatus());

        ResourceNotFoundException notFound = new ResourceNotFoundException("Not found", ErrorCode.BOOKING_NOT_FOUND);
        assertEquals("BOOKING_NOT_FOUND", notFound.getCode());
        assertEquals(HttpStatus.NOT_FOUND, notFound.getStatus());

        UnauthorizedException unauthorized = new UnauthorizedException("Session expired", ErrorCode.TOKEN_EXPIRED);
        assertEquals("TOKEN_EXPIRED", unauthorized.getCode());
        assertEquals(HttpStatus.UNAUTHORIZED, unauthorized.getStatus());

        ForbiddenException forbidden = new ForbiddenException("No access", ErrorCode.FORBIDDEN);
        assertEquals("FORBIDDEN", forbidden.getCode());
        assertEquals(HttpStatus.FORBIDDEN, forbidden.getStatus());
    }
}
