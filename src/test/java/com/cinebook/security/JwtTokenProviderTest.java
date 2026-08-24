package com.cinebook.security;

import com.cinebook.entity.User;
import com.cinebook.enums.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;
    private final String secret = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
    private final long expiration = 60000; // 1 minute

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(secret, expiration);
    }

    @Test
    void testGenerateAndValidateToken() {
        UserDetailsImpl userDetails = UserDetailsImpl.builder()
                .id("test-user-id")
                .email("user@example.com")
                .fullName("Test User")
                .password("hashed_pwd")
                .status(UserStatus.ACTIVE)
                .isDeleted(false)
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                .build();

        String token = jwtTokenProvider.generateAccessToken(userDetails);

        assertNotNull(token);
        assertTrue(jwtTokenProvider.validateToken(token));
        assertEquals("test-user-id", jwtTokenProvider.getUserIdFromToken(token));
        assertEquals("user@example.com", jwtTokenProvider.getEmailFromToken(token));
    }

    @Test
    void testValidateInvalidToken() {
        assertFalse(jwtTokenProvider.validateToken("invalid.token.here"));
        assertFalse(jwtTokenProvider.validateToken(""));
        assertFalse(jwtTokenProvider.validateToken(null));
    }
}

