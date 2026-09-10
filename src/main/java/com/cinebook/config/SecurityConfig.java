package com.cinebook.config;

import com.cinebook.security.CustomAccessDeniedHandler;
import com.cinebook.security.JwtAuthenticationEntryPoint;
import com.cinebook.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Cấu hình bảo mật trung tâm của hệ thống CineBook sử dụng Spring Security 6.
 * 
 * Kiến trúc bảo mật:
 * 1. Stateless Authentication: Không lưu session trên Server (SessionCreationPolicy.STATELESS),
 *    mọi request được xác thực độc lập thông qua JSON Web Token (JWT) trong header Authorization.
 * 2. CSRF Disabled: Do hệ thống sử dụng stateless JWT thay vì Session Cookie của trình duyệt,
 *    nguy cơ CSRF (Cross-Site Request Forgery) bị loại bỏ, cho phép tắt bảo vệ CSRF để tối ưu hiệu năng REST API.
 * 3. Role-Based Access Control (RBAC):
 *    - Public: Đăng ký/đăng nhập, xem danh sách phim, lịch chiếu, sơ đồ rạp, callback thanh toán VNPay.
 *    - Customer/User: Đặt vé, giữ chỗ, xem lịch sử giao dịch, quản lý thông tin cá nhân.
 *    - Admin: Toàn quyền quản lý hệ thống chiếu phim (/api/v1/admin/**) thông qua vai trò ROLE_ADMIN.
 * 4. Filter Chain Pipeline: Đăng ký JwtAuthenticationFilter đứng trước UsernamePasswordAuthenticationFilter
 *    để chặn bắt token, giải mã và nạp UserDetails vào SecurityContextHolder trước khi đến Controller.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final CustomAccessDeniedHandler customAccessDeniedHandler;

    /**
     * Bean mã hóa mật khẩu sử dụng thuật toán băm BCrypt với salt ngẫu nhiên.
     * Đảm bảo mật khẩu lưu trong cơ sở dữ liệu không thể bị dịch ngược, chống tấn công Rainbow Table.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Quản lý tiến trình xác thực người dùng (AuthenticationManager) của Spring Security.
     * Sử dụng trong AuthServiceImpl để kiểm tra email và password khi người dùng đăng nhập.
     */
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration authenticationConfiguration
    ) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    /**
     * Định nghĩa chuỗi bộ lọc bảo mật (Security Filter Chain) cho các HTTP requests.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Cấu hình CORS cho phép giao tiếp an toàn giữa Frontend (Vue 3) và Backend
                .cors(Customizer.withDefaults())
                // Tắt CSRF vì hệ thống sử dụng Stateless Token-based Authentication
                .csrf(AbstractHttpConfigurer::disable)
                // Xử lý lỗi xác thực (401 Unauthorized) và phân quyền (403 Forbidden) chuẩn JSON
                .exceptionHandling(exceptionHandling -> exceptionHandling
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                        .accessDeniedHandler(customAccessDeniedHandler)
                )
                // Cấu hình không lưu trạng thái phiên làm việc trên server (Stateless)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                // Phân quyền chi tiết cho từng nhóm tài nguyên URI
                .authorizeHttpRequests(authorize -> authorize
                        // 1. Nhóm API xác thực công khai: Đăng ký, đăng nhập, làm mới token, quên mật khẩu
                        .requestMatchers(
                                "/api/v1/auth/register",
                                "/api/v1/auth/login",
                                "/api/v1/auth/refresh",
                                "/api/v1/auth/password-reset/**",
                                "/api/v1/auth/verify-email",
                                "/api/v1/auth/resend-verification"
                        ).permitAll()

                        // 2. Tài liệu API Swagger UI / OpenAPI (phục vụ kiểm thử và tài liệu hóa)
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**"
                        ).permitAll()

                        // 3. Nhóm API tra cứu thông tin công khai dành cho khách vãng lai và trang chủ:
                        // Cho phép xem danh sách phim, cụm rạp, phòng chiếu, loại ghế, lịch chiếu, thể loại, khuyến mãi
                        .requestMatchers(HttpMethod.GET, "/api/v1/movies/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/cinemas/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/auditoriums/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/seat-types/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/showtimes/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/genres/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/promotions/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/foods/**").permitAll()

                        // 4. Cổng thanh toán VNPay: Webhook IPN (Server-to-Server) và Return URL (Browser redirect)
                        // Bắt buộc mở public vì VNPay gọi trực tiếp không kèm Authorization token của người dùng
                        .requestMatchers("/api/v1/payments/vnpay/**").permitAll()

                        // 5. Khu vực Quản trị viên: Yêu cầu nghiêm ngặt người dùng phải có ROLE_ADMIN
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")

                        // 6. Toàn bộ các yêu cầu còn lại (Đặt vé, thanh toán, hồ sơ cá nhân) yêu cầu phải đăng nhập
                        .anyRequest().authenticated()
                );

        // Đặt JwtAuthenticationFilter trước UsernamePasswordAuthenticationFilter trong chuỗi lọc
        // để nạp Authentication vào SecurityContext trước khi các bộ lọc phân quyền kiểm tra
        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("Content-Disposition"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}

