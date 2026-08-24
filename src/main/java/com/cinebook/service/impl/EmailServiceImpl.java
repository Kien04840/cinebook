package com.cinebook.service.impl;

import com.cinebook.service.EmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class EmailServiceImpl implements EmailService {

    @Override
    public void sendPasswordResetEmail(String toEmail, String resetToken) {
        // In development mode, log the reset token to console.
        // Can be easily swapped with JavaMailSender in production when SMTP is configured.
        log.info("==================================================================");
        log.info("PASSWORD RESET EMAIL FOR: {}", toEmail);
        log.info("RESET TOKEN: {}", resetToken);
        log.info("==================================================================");
    }
}

