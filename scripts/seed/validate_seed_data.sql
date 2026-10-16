-- =========================================================================
-- CineBook Data Seeding Validation Script
-- Target Period: 2026-09-12 to 2026-10-02
-- =========================================================================

SELECT '=== 1. SHOWTIMES INTEGRITY ===' as test_section;

-- 1.1 Overlapping showtimes in the same auditorium
SELECT 
    '1.1 Overlapping showtimes' as check_name,
    COUNT(*) as violation_count
FROM showtimes s1
JOIN showtimes s2 ON s1.auditorium_id = s2.auditorium_id 
    AND s1.id <> s2.id 
    AND s1.status <> 'CANCELLED' 
    AND s2.status <> 'CANCELLED'
    AND s1.start_time < s2.end_time 
    AND s1.end_time > s2.start_time
WHERE s1.start_time >= '2026-09-12 00:00:00' AND s1.start_time <= '2026-10-02 23:59:59';

-- 1.2 Invalid start/end times
SELECT 
    '1.2 Invalid start/end times' as check_name,
    COUNT(*) as violation_count
FROM showtimes
WHERE start_time >= '2026-09-12 00:00:00' AND start_time <= '2026-10-02 23:59:59'
  AND (end_time <= start_time OR start_time IS NULL OR end_time IS NULL);

-- 1.3 Historical showtimes not FINISHED (or CANCELLED)
SELECT 
    '1.3 Historical showtimes with SCHEDULED status in past' as check_name,
    COUNT(*) as violation_count
FROM showtimes
WHERE start_time >= '2026-09-12 00:00:00' AND start_time <= '2026-10-02 23:59:59'
  AND status NOT IN ('FINISHED', 'CANCELLED');


SELECT '=== 2. BOOKINGS INTEGRITY ===' as test_section;

-- 2.1 Orphan bookings (invalid user_id or showtime_id)
SELECT 
    '2.1 Orphan bookings' as check_name,
    COUNT(*) as violation_count
FROM bookings b
LEFT JOIN users u ON b.user_id = u.id
LEFT JOIN showtimes s ON b.showtime_id = s.id
WHERE b.created_at >= '2026-09-12 00:00:00'
  AND (u.id IS NULL OR s.id IS NULL);

-- 2.2 Negative booking total_amount
SELECT 
    '2.2 Negative booking total_amount' as check_name,
    COUNT(*) as violation_count
FROM bookings
WHERE created_at >= '2026-09-12 00:00:00'
  AND total_amount < 0;

-- 2.3 Bookings exceeding MAX_SEATS_PER_BOOKING (8 units)
SELECT 
    '2.3 Bookings exceeding 8 seats' as check_name,
    COUNT(*) as violation_count
FROM (
    SELECT booking_id, COUNT(*) as seat_count
    FROM tickets
    WHERE created_at >= '2026-09-12 00:00:00'
    GROUP BY booking_id
    HAVING seat_count > 8
) overbooked;


SELECT '=== 3. TICKETS INTEGRITY ===' as test_section;

-- 3.1 Duplicate seats in the same showtime
SELECT 
    '3.1 Duplicate seats in same showtime' as check_name,
    COUNT(*) as violation_count
FROM (
    SELECT b.showtime_id, t.seat_id, COUNT(*) as dup_cnt
    FROM tickets t
    JOIN bookings b ON t.booking_id = b.id
    WHERE t.ticket_status IN ('VALID', 'USED')
      AND t.created_at >= '2026-09-12 00:00:00'
    GROUP BY b.showtime_id, t.seat_id
    HAVING dup_cnt > 1
) dupes;

-- 3.2 Orphan tickets (invalid booking or seat)
SELECT 
    '3.2 Orphan tickets' as check_name,
    COUNT(*) as violation_count
FROM tickets t
LEFT JOIN bookings b ON t.booking_id = b.id
LEFT JOIN seats s ON t.seat_id = s.id
WHERE t.created_at >= '2026-09-12 00:00:00'
  AND (b.id IS NULL OR s.id IS NULL);

-- 3.3 Ticket price <= 0
SELECT 
    '3.3 Invalid ticket price' as check_name,
    COUNT(*) as violation_count
FROM tickets
WHERE created_at >= '2026-09-12 00:00:00'
  AND ticket_price <= 0;


SELECT '=== 4. PAYMENTS & REFUNDS FINANCIAL INTEGRITY ===' as test_section;

-- 4.1 Payment amount mismatch with booking total_amount (for SUCCESS payments)
SELECT 
    '4.1 Payment amount mismatch with booking' as check_name,
    COUNT(*) as violation_count
FROM payments p
JOIN bookings b ON p.booking_id = b.id
WHERE p.created_at >= '2026-09-12 00:00:00'
  AND p.payment_status IN ('SUCCESS', 'REFUNDED')
  AND p.amount <> b.total_amount;

-- 4.2 Refund amount mismatch with payment amount
SELECT 
    '4.2 Refund amount exceeding payment' as check_name,
    COUNT(*) as violation_count
FROM refunds r
JOIN payments p ON r.payment_id = p.id
WHERE r.created_at >= '2026-09-12 00:00:00'
  AND r.amount > p.amount;

-- 4.3 Orphan payments / refunds
SELECT 
    '4.3 Orphan payments' as check_name,
    COUNT(*) as violation_count
FROM payments p
LEFT JOIN bookings b ON p.booking_id = b.id
WHERE p.created_at >= '2026-09-12 00:00:00' AND b.id IS NULL;

SELECT 
    '4.3 Orphan refunds' as check_name,
    COUNT(*) as violation_count
FROM refunds r
LEFT JOIN payments p ON r.payment_id = p.id
WHERE r.created_at >= '2026-09-12 00:00:00' AND p.id IS NULL;


SELECT '=== 5. PROMOTIONS & FOOD INTEGRITY ===' as test_section;

-- 5.1 Multiple promotions per booking (> 1)
SELECT 
    '5.1 Multiple promotions per booking' as check_name,
    COUNT(*) as violation_count
FROM (
    SELECT booking_id, COUNT(*) as promo_cnt
    FROM booking_promotions
    WHERE created_at >= '2026-09-12 00:00:00'
    GROUP BY booking_id
    HAVING promo_cnt > 1
) multi_promo;

-- 5.2 Invalid food subtotal (subtotal <> unit_price * quantity)
SELECT 
    '5.2 Food subtotal mismatch' as check_name,
    COUNT(*) as violation_count
FROM booking_foods
WHERE created_at >= '2026-09-12 00:00:00'
  AND subtotal <> (unit_price * quantity);


SELECT '=== 6. NOTIFICATIONS INTEGRITY ===' as test_section;

-- 6.1 Duplicate (booking_id, type) notifications
SELECT 
    '6.1 Duplicate booking notifications' as check_name,
    COUNT(*) as violation_count
FROM (
    SELECT booking_id, type, COUNT(*) as cnt
    FROM notifications
    WHERE created_at >= '2026-09-12 00:00:00'
    GROUP BY booking_id, type
    HAVING cnt > 1
) dup_notif;


SELECT '=== 7. OCCUPANCY BOUNDS ===' as test_section;

-- 7.1 Occupancy > 100% or < 0%
SELECT 
    '7.1 Occupancy exceeding 100%' as check_name,
    COUNT(*) as violation_count
FROM (
    SELECT s.id,
           SUM(CASE WHEN t.ticket_status IN ('VALID', 'USED') THEN st.capacity ELSE 0 END) as occupied_cap,
           aud_cap.total_capacity
    FROM showtimes s
    JOIN auditoriums a ON s.auditorium_id = a.id
    JOIN (
        SELECT auditorium_id, SUM(st2.capacity) as total_capacity
        FROM seats s2
        JOIN seat_types st2 ON s2.seat_type_id = st2.id
        WHERE s2.status = 'ACTIVE'
        GROUP BY auditorium_id
    ) aud_cap ON aud_cap.auditorium_id = a.id
    LEFT JOIN bookings b ON b.showtime_id = s.id
    LEFT JOIN tickets t ON t.booking_id = b.id
    LEFT JOIN seats st_seat ON t.seat_id = st_seat.id
    LEFT JOIN seat_types st ON st_seat.seat_type_id = st.id
    WHERE s.start_time >= '2026-09-12 00:00:00' AND s.start_time <= '2026-10-02 23:59:59'
    GROUP BY s.id, aud_cap.total_capacity
    HAVING occupied_cap > aud_cap.total_capacity
) over_cap;
