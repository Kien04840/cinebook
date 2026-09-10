package com.cinebook.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SecurityException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

/**
 * Thành phần tiện ích chịu trách nhiệm tạo, phân tích cú pháp và xác thực JSON Web Token (JWT).
 * 
 * Đặc tả kỹ thuật:
 * - Chuẩn mã hóa: HMAC-SHA256 / SHA512 với khóa đối xứng bí mật (jwt.secret).
 * - Thời hạn sống Access Token: 15 phút (900,000 ms) theo chuẩn stateless ngắn hạn nhằm giảm thiểu
 *   rủi ro khi token bị lộ trên đường truyền.
 * - Nội dung Payload (Claims): Chứa thông tin định danh người dùng (userId làm Subject),
 *   email, họ tên (fullName) và danh sách vai trò (roles: ROLE_CUSTOMER, ROLE_ADMIN) để Spring Security
 *   tiến hành phân quyền mà không cần truy vấn lại cơ sở dữ liệu ở mọi request.
 */
@Slf4j
@Component
public class JwtTokenProvider {

    private final SecretKey key;
    private final long jwtExpiration;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration:900000}") long jwtExpiration
    ) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.jwtExpiration = jwtExpiration;
    }

    /**
     * Tạo Access Token mới từ thông tin chi tiết của người dùng đã xác thực thành công.
     * 
     * @param userDetails Đối tượng UserDetails chứa thông tin định danh và quyền hạn
     * @return Chuỗi JWT đã được ký số dạng chuỗi Base64 URL-safe (Header.Payload.Signature)
     */
    public String generateAccessToken(UserDetailsImpl userDetails) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpiration);

        List<String> roles = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        return Jwts.builder()
                .subject(userDetails.getId())
                .claim("email", userDetails.getEmail())
                .claim("fullName", userDetails.getFullName())
                .claim("roles", roles)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(key)
                .compact();
    }

    /**
     * Trích xuất mã định danh người dùng (userId) được lưu trữ trong trường Subject của JWT.
     * 
     * @param token Chuỗi Access Token hợp lệ
     * @return Chuỗi UUID của người dùng
     */
    public String getUserIdFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return claims.getSubject();
    }

    /**
     * Trích xuất địa chỉ email từ trường claims mở rộng của JWT.
     */
    public String getEmailFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return claims.get("email", String.class);
    }

    /**
     * Kiểm tra tính hợp lệ và toàn vẹn của chuỗi JWT:
     * - Kiểm tra chữ ký số có khớp với SecretKey của máy chủ không (chống giả mạo dữ liệu).
     * - Kiểm tra thời hạn hiệu lực (chống dùng lại token đã hết hạn ExpiredJwtException).
     * - Kiểm tra định dạng cấu trúc 3 phần (MalformedJwtException).
     *
     * @param token Chuỗi JWT cần kiểm tra
     * @return true nếu token hợp lệ và còn hạn; false nếu có bất kỳ lỗi nào
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (SecurityException | MalformedJwtException e) {
            log.error("Invalid JWT signature or format: {}", e.getMessage());
        } catch (ExpiredJwtException e) {
            log.error("Expired JWT token: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.error("Unsupported JWT token: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.error("JWT claims string is empty: {}", e.getMessage());
        } catch (JwtException e) {
            log.error("JWT validation error: {}", e.getMessage());
        }
        return false;
    }

    public long getExpirationInSeconds() {
        return jwtExpiration / 1000;
    }
}

