package com.cinebook.service.impl;

import com.cinebook.entity.Booking;
import com.cinebook.entity.Refund;
import com.cinebook.entity.Seat;
import com.cinebook.entity.Ticket;
import com.cinebook.service.EmailService;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Slf4j
@Service
public class EmailServiceImpl implements EmailService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm - dd/MM/yyyy");
    private static final Locale VI_LOCALE = Locale.of("vi", "VN");

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${cinebook.mail.enabled:false}")
    private boolean mailEnabled;

    @Value("${cinebook.mail.from:noreply@cinebook.com}")
    private String mailFrom;

    @Value("${cinebook.mail.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    @Override
    public void sendPasswordResetEmail(String toEmail, String resetToken) {
        try {
            String resetLink = frontendUrl + "/reset-password?token=" + resetToken;
            log.info("==================================================================");
            log.info("[EMAIL] PASSWORD RESET FOR: {}", toEmail);
            log.info("[EMAIL] RESET LINK: {}", resetLink);
            log.info("==================================================================");

            if (mailEnabled && mailSender != null) {
                MimeMessage message = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
                helper.setFrom(mailFrom, "CineBook Cinema");
                helper.setTo(toEmail);
                helper.setSubject("CineBook — Yêu cầu đặt lại mật khẩu");

                String htmlContent = """
                    <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #e5e7eb; border-radius: 8px;">
                        <h2 style="color: #dc2626; text-align: center;">CineBook Cinema</h2>
                        <p>Xin chào,</p>
                        <p>Chúng tôi nhận được yêu cầu đặt lại mật khẩu cho tài khoản <strong>%s</strong>.</p>
                        <p>Vui lòng nhấn vào nút bên dưới để tiến hành đặt mật khẩu mới (liên kết có hiệu lực trong 15 phút):</p>
                        <div style="text-align: center; margin: 30px 0;">
                            <a href="%s" style="background-color: #dc2626; color: white; padding: 12px 24px; text-decoration: none; border-radius: 6px; font-weight: bold; display: inline-block;">Đặt lại mật khẩu</a>
                        </div>
                        <p style="color: #6b7280; font-size: 13px;">Nếu bạn không yêu cầu hành động này, vui lòng bỏ qua email hoặc liên hệ hỗ trợ.</p>
                    </div>
                """.formatted(toEmail, resetLink);

                helper.setText(htmlContent, true);
                mailSender.send(message);
                log.info("Password reset email successfully dispatched to {}", toEmail);
            }
        } catch (Exception e) {
            log.error("Failed to send password reset email to {}: {}", toEmail, e.getMessage());
        }
    }

    @Override
    public void sendBookingConfirmationEmail(String toEmail, String customerName, Booking booking, List<Ticket> tickets) {
        try {
            String movieTitle = booking.getShowtime() != null && booking.getShowtime().getMovie() != null
                    ? booking.getShowtime().getMovie().getTitle() : "Phim";
            String cinemaName = booking.getShowtime() != null && booking.getShowtime().getAuditorium() != null && booking.getShowtime().getAuditorium().getCinema() != null
                    ? booking.getShowtime().getAuditorium().getCinema().getName() : "Rạp CineBook";
            String auditoriumName = booking.getShowtime() != null && booking.getShowtime().getAuditorium() != null
                    ? booking.getShowtime().getAuditorium().getName() : "Phòng chiếu";
            String startTime = booking.getShowtime() != null && booking.getShowtime().getStartTime() != null
                    ? booking.getShowtime().getStartTime().format(TIME_FORMATTER) : "N/A";

            String seatCodes = tickets != null && !tickets.isEmpty()
                    ? tickets.stream().map(Ticket::getSeat).map(Seat::getSeatCode).collect(Collectors.joining(", "))
                    : "N/A";

            NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(VI_LOCALE);
            String formattedTotal = currencyFormat.format(booking.getTotalAmount());
            String ticketViewLink = frontendUrl + "/my-bookings";

            log.info("==================================================================");
            log.info("[EMAIL] BOOKING CONFIRMATION: {} (Code: {})", toEmail, booking.getBookingCode());
            log.info("[EMAIL] Movie: {}, Seats: {}, Total: {}", movieTitle, seatCodes, formattedTotal);
            log.info("==================================================================");

            if (mailEnabled && mailSender != null) {
                MimeMessage message = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
                helper.setFrom(mailFrom, "CineBook Cinema");
                helper.setTo(toEmail);
                helper.setSubject("CineBook — Xác nhận đặt vé thành công [" + booking.getBookingCode() + "]");

                String htmlContent = """
                    <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #e5e7eb; border-radius: 8px;">
                        <h2 style="color: #dc2626; text-align: center;">CineBook Cinema</h2>
                        <h3 style="color: #111827; text-align: center; margin-top: 0;">Xác Nhận Đặt Vé Thành Công!</h3>
                        <p>Xin chào <strong>%s</strong>,</p>
                        <p>Đơn đặt vé của bạn đã được thanh toán thành công. Dưới đây là thông tin vé điện tử của bạn:</p>
                        <table style="width: 100%%; border-collapse: collapse; margin: 20px 0;">
                            <tr style="border-bottom: 1px solid #f3f4f6;">
                                <td style="padding: 8px 0; color: #6b7280;">Mã đơn hàng:</td>
                                <td style="padding: 8px 0; font-weight: bold; text-align: right; color: #dc2626;">%s</td>
                            </tr>
                            <tr style="border-bottom: 1px solid #f3f4f6;">
                                <td style="padding: 8px 0; color: #6b7280;">Phim:</td>
                                <td style="padding: 8px 0; font-weight: bold; text-align: right;">%s</td>
                            </tr>
                            <tr style="border-bottom: 1px solid #f3f4f6;">
                                <td style="padding: 8px 0; color: #6b7280;">Rạp chiếu:</td>
                                <td style="padding: 8px 0; text-align: right;">%s (%s)</td>
                            </tr>
                            <tr style="border-bottom: 1px solid #f3f4f6;">
                                <td style="padding: 8px 0; color: #6b7280;">Suất chiếu:</td>
                                <td style="padding: 8px 0; font-weight: bold; text-align: right; color: #2563eb;">%s</td>
                            </tr>
                            <tr style="border-bottom: 1px solid #f3f4f6;">
                                <td style="padding: 8px 0; color: #6b7280;">Ghế ngồi:</td>
                                <td style="padding: 8px 0; font-weight: bold; text-align: right; color: #16a34a;">%s</td>
                            </tr>
                            <tr>
                                <td style="padding: 12px 0; font-weight: bold;">Tổng thanh toán:</td>
                                <td style="padding: 12px 0; font-weight: bold; font-size: 18px; text-align: right; color: #dc2626;">%s</td>
                            </tr>
                        </table>
                        <div style="text-align: center; margin: 30px 0;">
                            <a href="%s" style="background-color: #dc2626; color: white; padding: 12px 24px; text-decoration: none; border-radius: 6px; font-weight: bold; display: inline-block;">Xem Vé & Mã QR</a>
                        </div>
                        <p style="color: #6b7280; font-size: 13px; text-align: center;">Vui lòng xuất trình mã QR trên vé điện tử tại quầy soát vé trước giờ chiếu 15 phút.</p>
                    </div>
                """.formatted(
                        customerName != null ? customerName : "Quý khách",
                        booking.getBookingCode(),
                        movieTitle,
                        cinemaName,
                        auditoriumName,
                        startTime,
                        seatCodes,
                        formattedTotal,
                        ticketViewLink
                );

                helper.setText(htmlContent, true);
                mailSender.send(message);
                log.info("Booking confirmation email dispatched to {}", toEmail);
            }
        } catch (Exception e) {
            log.error("Failed to send booking confirmation email for booking {}: {}", booking.getBookingCode(), e.getMessage());
        }
    }

    @Override
    public void sendRefundConfirmationEmail(String toEmail, String customerName, Booking booking, Refund refund) {
        try {
            String movieTitle = booking.getShowtime() != null && booking.getShowtime().getMovie() != null
                    ? booking.getShowtime().getMovie().getTitle() : "Phim";
            String startTime = booking.getShowtime() != null && booking.getShowtime().getStartTime() != null
                    ? booking.getShowtime().getStartTime().format(TIME_FORMATTER) : "N/A";
            String processedAt = refund.getProcessedAt() != null
                    ? refund.getProcessedAt().format(TIME_FORMATTER) : LocalDateTime.now().format(TIME_FORMATTER);

            NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(VI_LOCALE);
            String formattedRefundAmount = currencyFormat.format(refund.getAmount());

            log.info("==================================================================");
            log.info("[EMAIL] REFUND CONFIRMATION: {} (Refund: {}, Booking: {})", toEmail, refund.getRefundCode(), booking.getBookingCode());
            log.info("[EMAIL] Amount: {}, Reason: {}", formattedRefundAmount, refund.getRefundReason());
            log.info("==================================================================");

            if (mailEnabled && mailSender != null) {
                MimeMessage message = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
                helper.setFrom(mailFrom, "CineBook Cinema");
                helper.setTo(toEmail);
                helper.setSubject("CineBook — Xác nhận hoàn tiền đơn đặt vé [" + booking.getBookingCode() + "]");

                String htmlContent = """
                    <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #e5e7eb; border-radius: 8px;">
                        <h2 style="color: #dc2626; text-align: center;">CineBook Cinema</h2>
                        <h3 style="color: #111827; text-align: center; margin-top: 0;">Xác Nhận Hoàn Tiền Thành Công</h3>
                        <p>Xin chào <strong>%s</strong>,</p>
                        <p>Yêu cầu hoàn tiền của bạn đã được xử lý thành công. Chi tiết biên lai hoàn tiền:</p>
                        <table style="width: 100%%; border-collapse: collapse; margin: 20px 0;">
                            <tr style="border-bottom: 1px solid #f3f4f6;">
                                <td style="padding: 8px 0; color: #6b7280;">Mã hoàn tiền:</td>
                                <td style="padding: 8px 0; font-weight: bold; text-align: right; color: #dc2626;">%s</td>
                            </tr>
                            <tr style="border-bottom: 1px solid #f3f4f6;">
                                <td style="padding: 8px 0; color: #6b7280;">Mã đơn đặt vé:</td>
                                <td style="padding: 8px 0; font-weight: bold; text-align: right;">%s</td>
                            </tr>
                            <tr style="border-bottom: 1px solid #f3f4f6;">
                                <td style="padding: 8px 0; color: #6b7280;">Phim / Suất chiếu:</td>
                                <td style="padding: 8px 0; text-align: right;">%s (%s)</td>
                            </tr>
                            <tr style="border-bottom: 1px solid #f3f4f6;">
                                <td style="padding: 8px 0; color: #6b7280;">Thời gian xử lý:</td>
                                <td style="padding: 8px 0; text-align: right;">%s</td>
                            </tr>
                            <tr style="border-bottom: 1px solid #f3f4f6;">
                                <td style="padding: 8px 0; color: #6b7280;">Lý do hoàn tiền:</td>
                                <td style="padding: 8px 0; text-align: right;">%s</td>
                            </tr>
                            <tr>
                                <td style="padding: 12px 0; font-weight: bold;">Số tiền hoàn:</td>
                                <td style="padding: 12px 0; font-weight: bold; font-size: 18px; text-align: right; color: #16a34a;">%s</td>
                            </tr>
                        </table>
                        <p style="color: #6b7280; font-size: 13px;">Tiền hoàn sẽ được hoàn về tài khoản / phương thức thanh toán ban đầu theo quy định của ngân hàng phát hành.</p>
                    </div>
                """.formatted(
                        customerName != null ? customerName : "Quý khách",
                        refund.getRefundCode(),
                        booking.getBookingCode(),
                        movieTitle,
                        startTime,
                        processedAt,
                        refund.getRefundReason() != null ? refund.getRefundReason() : "Hoàn vé theo yêu cầu",
                        formattedRefundAmount
                );

                helper.setText(htmlContent, true);
                mailSender.send(message);
                log.info("Refund confirmation email dispatched to {}", toEmail);
            }
        } catch (Exception e) {
            log.error("Failed to send refund confirmation email for refund {}: {}", refund.getRefundCode(), e.getMessage());
        }
    }
}
