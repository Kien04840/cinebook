-- ========================================================================
-- PHẦN: MIGRATION V1_5 (Admin Reporting & Analytics Performance Indexes)
-- File: src/main/resources/db/migration/V1_5__add_reporting_performance_indexes.sql
-- ========================================================================

-- Composite index on payments for revenue reporting and KPI filtering
CREATE INDEX idx_payments_status_paid ON payments (payment_status, paid_at, amount);

-- Composite index on refunds for refund trend and KPI filtering
CREATE INDEX idx_refunds_status_processed ON refunds (refund_status, processed_at, amount);

-- Composite index on bookings for booking statistics and status counting
CREATE INDEX idx_bookings_created_status ON bookings (created_at, booking_status);

