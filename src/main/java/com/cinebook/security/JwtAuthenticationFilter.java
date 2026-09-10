package com.cinebook.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Bộ lọc bảo mật chặn bắt từng HTTP Request (OncePerRequestFilter) để xác thực người dùng.
 * 
 * Luồng hoạt động:
 * 1. Trích xuất Authorization Header từ Request, kiểm tra tiền tố "Bearer ".
 * 2. Xác thực tính hợp lệ của chuỗi JWT thông qua JwtTokenProvider.
 * 3. Nếu JWT hợp lệ:
 *    - Lấy userId từ payload của JWT.
 *    - Nạp thông tin UserDetails từ database/cache.
 *    - Kiểm tra tài khoản có bị khóa hoặc vô hiệu hóa hay không.
 *    - Tạo đối tượng UsernamePasswordAuthenticationToken và nạp vào SecurityContextHolder.
 * 4. Chuyển tiếp Request tới Filter tiếp theo trong Filter Chain (UsernamePasswordAuthenticationFilter).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsServiceImpl userDetailsService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            // Bước 1: Trích xuất JWT token từ Authorization Header
            String jwt = parseJwt(request);

            // Bước 2: Xác thực chữ ký và hạn dùng của token
            if (jwt != null && jwtTokenProvider.validateToken(jwt)) {
                String userId = jwtTokenProvider.getUserIdFromToken(jwt);
                UserDetails userDetails = userDetailsService.loadUserById(userId);

                // Bước 3: Kiểm tra trạng thái tài khoản và thiết lập Security Context
                if (userDetails.isEnabled() && userDetails.isAccountNonLocked()) {
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                     userDetails,
                                     null,
                                     userDetails.getAuthorities()
                            );
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    // Đưa đối tượng xác thực vào SecurityContextHolder để các Controller/Service sử dụng
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        } catch (Exception e) {
            log.error("Cannot set user authentication: {}", e.getMessage());
        }

        // Bước 4: Cho phép request tiếp tục đi qua chuỗi bộ lọc
        filterChain.doFilter(request, response);
    }

    /**
     * Phương thức phân tích chuỗi header Authorization để lấy ra Access Token thuần túy.
     * Định dạng chuẩn: "Bearer <token>" -> Cắt bỏ 7 ký tự đầu "Bearer ".
     */
    private String parseJwt(HttpServletRequest request) {
        String headerAuth = request.getHeader("Authorization");

        if (StringUtils.hasText(headerAuth) && headerAuth.startsWith("Bearer ")) {
            return headerAuth.substring(7);
        }

        return null;
    }
}

