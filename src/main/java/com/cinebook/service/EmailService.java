package com.cinebook.service;

import com.cinebook.entity.Booking;
import com.cinebook.entity.Refund;
import com.cinebook.entity.Ticket;

import java.util.List;

public interface EmailService {

    void sendPasswordResetEmail(String toEmail, String resetToken);

    void sendBookingConfirmationEmail(String toEmail, String customerName, Booking booking, List<Ticket> tickets);

    void sendRefundConfirmationEmail(String toEmail, String customerName, Booking booking, Refund refund);
}
