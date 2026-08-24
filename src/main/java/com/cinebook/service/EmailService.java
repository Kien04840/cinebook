package com.cinebook.service;

public interface EmailService {

    void sendPasswordResetEmail(String toEmail, String resetToken);
}

