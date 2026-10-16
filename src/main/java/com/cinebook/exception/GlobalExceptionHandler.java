package com.cinebook.exception;

import com.cinebook.security.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Bộ xử lý ngoại lệ tập trung toàn hệ thống (Global Exception Handler).
 * Sử dụng chú thích @RestControllerAdvice để bắt và chuẩn hóa định dạng phản hồi lỗi JSON
 * cho tất cả các Controller trong ứng dụng theo chuẩn RESTful API.
 * 
 * Các nhóm ngoại lệ được chuẩn hóa:
 * 1. AppException Hierarchy: Toàn bộ ngoại lệ nghiệp vụ tùy biến (BadRequest 400, Unauthorized 401,
 *    Forbidden 403, NotFound 404, Conflict 409).
 * 2. MethodArgumentNotValidException: Lỗi vi phạm ràng buộc dữ liệu đầu vào (Jakarta Bean Validation).
 * 3. HttpMessageNotReadableException: Lỗi định dạng payload JSON không đọc được (HTTP 400).
 * 4. Security Exceptions: Sai thông tin đăng nhập (BadCredentialsException) hoặc không đủ quyền (AccessDeniedException).
 * 5. Generic Exception: Bắt các lỗi không mong muốn (500 Internal Server Error) để tránh làm lộ stack trace hệ thống.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Bắt tất cả ngoại lệ kế thừa từ AppException (lớp cha của các lỗi nghiệp vụ trong hệ thống CineBook).
     */
    @ExceptionHandler(AppException.class)
    public ResponseEntity<ErrorResponse> handleAppException(
            AppException ex,
            HttpServletRequest request
    ) {
        HttpStatus status = ex.getStatus();
        String code = ex.getCode() != null ? ex.getCode() : status.name();
        return buildResponseEntity(status, code, ex.getMessage(), request, null);
    }

    /**
     * Bắt lỗi khi dữ liệu trong Request Body không vượt qua được kiểm tra Bean Validation (@Valid, @NotNull, @Size...).
     * Bóc tách chi tiết từng trường bị lỗi (field) kèm thông điệp giải thích cụ thể.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex,
            HttpServletRequest request
    ) {
        List<ErrorResponse.FieldErrorDetail> details = ex.getBindingResult()
                .getAllErrors()
                .stream()
                .map(error -> {
                    String fieldName = (error instanceof FieldError fieldError)
                            ? fieldError.getField()
                            : error.getObjectName();
                    return ErrorResponse.FieldErrorDetail.builder()
                            .field(fieldName)
                            .message(error.getDefaultMessage())
                            .build();
                })
                .toList();

        return buildResponseEntity(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED.name(), "Validation failed", request, details);
    }

    /**
     * Bắt lỗi khi dữ liệu JSON trong Request Body không thể deserialization hoặc sai định dạng.
     * Trả về HTTP 400 Bad Request, không làm rò rỉ tên package hay cấu trúc nội bộ của Jackson / Java.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException ex,
            HttpServletRequest request
    ) {
        log.warn("Malformed HTTP request body at {}: {}", request.getRequestURI(), ex.getMessage());
        return buildResponseEntity(HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST.name(), "Dữ liệu yêu cầu không hợp lệ hoặc sai định dạng.", request, null);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentialsException(
            BadCredentialsException ex,
            HttpServletRequest request
    ) {
        return buildResponseEntity(HttpStatus.UNAUTHORIZED, ErrorCode.AUTH_FAILED.name(), "Invalid email or password", request, null);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(
            AuthenticationException ex,
            HttpServletRequest request
    ) {
        return buildResponseEntity(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED.name(), ex.getMessage(), request, null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(
            AccessDeniedException ex,
            HttpServletRequest request
    ) {
        return buildResponseEntity(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN.name(), "Access is denied", request, null);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(
            IllegalArgumentException ex,
            HttpServletRequest request
    ) {
        return buildResponseEntity(HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST.name(), ex.getMessage(), request, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex,
            HttpServletRequest request
    ) {
        log.error("Internal server error: ", ex);
        return buildResponseEntity(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_SERVER_ERROR.name(), "Đã xảy ra lỗi không mong muốn trên hệ thống. Vui lòng thử lại sau.", request, null);
    }

    private ResponseEntity<ErrorResponse> buildResponseEntity(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request,
            List<ErrorResponse.FieldErrorDetail> details
    ) {
        String correlationId = MDC.get(CorrelationIdFilter.MDC_CORRELATION_ID_KEY);
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .code(code)
                .message(message)
                .path(request.getRequestURI())
                .correlationId(correlationId)
                .details(details)
                .build();

        return new ResponseEntity<>(errorResponse, status);
    }
}

