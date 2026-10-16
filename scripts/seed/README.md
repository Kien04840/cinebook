# CineBook — Historical Demo Data Seeding for Admin Reporting & Analytics

## 1. Tổng quan & Mục tiêu

Tài liệu này mô tả chi tiết phương pháp thiết kế, sinh dữ liệu và kiểm thử bộ dữ liệu lịch sử mô phỏng thực tế (**Realistic Historical Demo Dataset**) phục vụ module **Admin Reporting & Analytics Dashboard** của CineBook.

* **Khoảng thời gian (Target Window):** `2026-09-12 00:00:00` đến `2026-10-02 23:59:59` (**21 ngày lịch sử**).
* **Deterministic Random Seed:** `20261002` (Đảm bảo 100% khả năng tái lập và nhất quán).
* **Cơ chế thêm dữ liệu:** Hoàn toàn **NON-DESTRUCTIVE** (Chỉ thực hiện `INSERT`, bảo toàn 100% 626 suất chiếu và dữ liệu gốc có sẵn trước ngày 2026-09-17).

---

## 2. Nguyên tắc mô phỏng thực tế (Realistic Modeling Principles)

Hệ thống được thiết kế bám sát hành vi thực tế của chuỗi rạp chiếu phim hiện đại:

### 2.1. Phân tầng rạp chiếu (Cinema Tiers)
* **Tier 1 (Flagship HN/HCM):** CGV Landmark 81, BHD Star Bitexco, Trung tâm Chiếu phim Quốc gia, CGV Vincom Bà Triệu. Vận hành từ 2–3 phòng chiếu đồng thời mỗi ngày, tần suất suất chiếu cao, lấp đầy trung bình 30–50% (đỉnh điểm cuối tuần đạt 65–73%).
* **Tier 2 (Trung tâm tỉnh/thành phố lớn):** Lotte Đà Nẵng, Beta Thanh Xuân, CGV Đồng Khởi, BHD Phạm Ngọc Thạch... Vận hành 1–2 phòng chiếu.
* **Tier 3 (Khu vực ngoại thành & tỉnh lân cận):** Vận hành 1 phòng chiếu.
* **Tier 4 (Quy mô nhỏ):** Vận hành xoay vòng linh hoạt.

### 2.2. Lập lịch suất chiếu (Showtime Scheduling)
* Tuân thủ giờ mở cửa và đóng cửa: **08:30 – 23:30**.
* Tuân thủ thời gian dọn dẹp chuyển giao (**Turnaround = 15 phút**) và làm tròn khung giờ (**Snap Interval = 15 phút**).
* Tuyệt đối **không trùng lịch (Zero Overlap)** trong cùng một phòng chiếu.
* Trạng thái suất chiếu lịch sử: 100% chuyển sang `FINISHED`.

### 2.3. Công thức định giá vé động chuẩn CineBook (Dynamic Pricing)
Giá vé tuân thủ chính xác công thức của `PricingServiceImpl`:
$$\text{finalPrice} = (\text{basePrice} \times \text{capacity}) + \text{seatTypeModifier} + \text{dayModifier} + \text{timeSlotModifier}$$
* **basePrice:** 75,000 VND / 85,000 VND / 95,000 VND (tùy định dạng 2D, 3D, IMAX).
* **capacity:** STANDARD = 1, VIP = 1, COUPLE = 2.
* **seatTypeModifier:** STANDARD = 0 VND, VIP = +20,000 VND, COUPLE = +40,000 VND.
* **dayModifier:** Thứ 2–Thứ 3 = -5,000 VND; Thứ 4–Thứ 6 = 0 VND; Thứ 7–Chủ nhật = +10,000 VND.
* **timeSlotModifier:** Khung giờ vàng (17:00 – 23:59) = +10,000 VND; Ban ngày = 0 VND.

### 2.4. Phân phối trạng thái đơn đặt vé (Booking Status Distribution)
* **PAID:** ~85.1% (Thanh toán thành công qua VNPay).
* **REFUNDED:** ~3.9% (Khách hàng hủy/hoàn vé hợp lệ).
* **CANCELLED:** ~6.0% (Đơn bị hủy do khách chủ động hủy trước khi thanh toán).
* **EXPIRED:** ~5.0% (Đơn hết hạn giữ ghế sau 10–15 phút).

### 2.5. Tính toàn vẹn tài chính (Financial Integrity)
* `payment.amount = booking.total_amount = max(0, grossAmount - discountAmount) + foodTotal`.
* Với đơn hoàn vé (`REFUNDED`): `refund.amount = payment.amount`, `refund.refund_status = 'SUCCESS'`, `payment.payment_status = 'REFUNDED'`, `booking.booking_status = 'REFUNDED'`, toàn bộ `tickets.ticket_status = 'CANCELLED'`.
* **Zero orphan records:** 100% Payment, Refund, Ticket, Notification, Concession đều liên kết với Booking và Showtime hợp lệ.

### 2.6. Bắp nước (Concessions) & Khuyến mãi (Promotions)
* Tỷ lệ mua bắp nước kèm theo: ~40% trên tổng số booking thành công.
* Mặt hàng đa dạng: Combo Solo, Couple Combo, Family Popcorn, Pepsi, Coke, Nước suối... Số lượng từ 1–2 phần.
* Khuyến mãi: Áp dụng mã `KHAIGIANG123` (giảm 10%, tối đa 50,000 VND) cho khoảng 15% booking thỏa điều kiện tối thiểu.

### 2.7. Tính chu kỳ & Thời gian (Seasonality)
* **Cuối tuần vs Ngày thường:** Doanh thu thứ 7 và Chủ nhật cao gấp **3.13 lần** so với thứ 2 và thứ 3.
* **Khung giờ chiếu:** Phân bổ tỷ trọng theo giờ:
  * 08:30 – 12:00: 10%
  * 12:00 – 17:00: 25%
  * 17:00 – 21:30 (Prime-time): 45%
  * 21:30 – 23:30: 20%

---

## 3. Tổng hợp số liệu (Summary Metrics)

| Chỉ số / Thực thể | Giá trị tạo mới (Seed) | Tổng cộng trong DB (2026-09-12 → 2026-10-02) |
|---|---|---|
| **Suất chiếu (Showtimes)** | 2,369 | 2,995 (bao gồm 626 suất có sẵn) |
| **Đơn đặt vé (Bookings)** | 57,645 | 57,645 |
| - *PAID (Thanh toán)* | 49,065 (85.1%) | 49,065 (85.1%) |
| - *REFUNDED (Hoàn tiền)* | 2,238 (3.9%) | 2,238 (3.9%) |
| - *CANCELLED (Đã hủy)* | 3,449 (6.0%) | 3,449 (6.0%) |
| - *EXPIRED (Hết hạn)* | 2,893 (5.0%) | 2,893 (5.0%) |
| **Vé đã phát hành (Tickets)** | 109,438 | 109,438 |
| - *Vé hợp lệ / đã xem (Net)* | 104,620 | 104,620 |
| - *Vé hoàn / hủy (Refunded)* | 4,818 | 4,818 |
| **Giao dịch thanh toán (Payments)** | 51,303 | 51,303 |
| **Doanh thu gộp (Gross Revenue)** | 13,806,285,000 VND | 13,806,285,000 VND (~13.8 tỷ VND) |
| **Số tiền hoàn (Refund Amount)** | 600,019,000 VND | 600,019,000 VND (~4.35% doanh thu gộp) |
| **Doanh thu thuần (Net Revenue)** | 13,206,266,000 VND | 13,206,266,000 VND (~13.2 tỷ VND) |
| **Bắp nước đã bán (Booking Foods)** | 22,955 đơn hàng | 22,955 đơn hàng |
| **Áp dụng khuyến mãi (Promotions)** | 8,612 lượt | 8,612 lượt |
| **Thông báo hệ thống (Notifications)**| 40,137 | 40,137 |
| **Tổng sức chứa (Total Capacity)** | — | 518,714 chỗ ngồi |
| **Sức chứa đã bán (Occupied Capacity)**| — | 109,160 chỗ ngồi |
| **Tỷ lệ lấp đầy bình quân (Avg Occupancy)**| — | **21.04%** |

---

## 4. Kết quả kiểm tra tính toàn vẹn (Data Integrity Verification)

Tất cả 14 phép kiểm tra ràng buộc nghiệp vụ trong `scripts/seed/validate_seed_data.sql` đều đạt **0 vi phạm**:

1. **Kiểm tra Suất chiếu (Showtimes):**
   * Không có suất chiếu trùng giờ trong cùng phòng chiếu (`0 violations`).
   * Không có suất chiếu có thời gian kết thúc trước thời gian bắt đầu (`0 violations`).
   * Không có suất chiếu quá khứ mang trạng thái `SCHEDULED` (`0 violations`).
2. **Kiểm tra Đơn đặt vé (Bookings):**
   * Không có đơn đặt vé mồ côi (`0 violations`).
   * Không có đơn đặt vé có tổng tiền âm (`0 violations`).
   * Không có đơn đặt vé vượt quá 8 ghế (`0 violations`).
3. **Kiểm tra Vé (Tickets):**
   * Không trùng ghế trong cùng một suất chiếu (`0 violations`).
   * Không có vé mồ côi (`0 violations`).
   * Giá vé hoàn toàn dương và khớp công thức định giá (`0 violations`).
4. **Kiểm tra Thanh toán & Hoàn tiền (Payments & Refunds):**
   * Số tiền thanh toán khớp 100% với tổng tiền booking (`0 violations`).
   * Số tiền hoàn không bao giờ vượt quá số tiền thanh toán ban đầu (`0 violations`).
   * Không có giao dịch thanh toán hoặc hoàn tiền mồ côi (`0 violations`).
5. **Kiểm tra Khuyến mãi & Bắp nước (Promotions & Foods):**
   * Mỗi đơn đặt vé chỉ áp dụng tối đa 1 mã khuyến mãi (`0 violations`).
   * Tổng tiền bắp nước khớp chính xác số lượng $\times$ đơn giá snapshot (`0 violations`).
6. **Kiểm tra Thông báo (Notifications):**
   * Không có thông báo trùng lặp sự kiện cho cùng booking (`0 violations`).
7. **Kiểm tra Tỷ lệ lấp đầy (Occupancy):**
   * 100% suất chiếu có tỷ lệ lấp đầy trong khoảng $0\% \le \text{Occupancy} \le 100\%$ (`0 violations`).

---

## 5. Kết quả kiểm thử Reporting Smoke Test

Tập lệnh `scripts/seed/smoke_test_reporting.py` mô phỏng trực tiếp 7 API của module Báo cáo quản trị:

### 5.1. Dashboard Summary (`GET /api/v1/admin/reports/dashboard`)
* Doanh thu gộp: `13,806,285,000 VND`
* Hoàn tiền: `600,019,000 VND` (4.35%)
* Doanh thu thuần: `13,206,266,000 VND`
* Tổng vé bán: `109,438` vé (Net: `104,620` vé)
* Suất chiếu: `2,995` suất; Tỷ lệ lấp đầy trung bình: `21.04%`

### 5.2. Doanh thu theo ngày & tháng (`GET /api/v1/admin/reports/revenue`)
* **Tháng 09/2026 (19 ngày):** Doanh thu thuần `12,434,353,000 VND`
* **Tháng 10/2026 (2 ngày):** Doanh thu thuần `771,913,000 VND`
* **Hiệu ứng cuối tuần:** Thứ 7 & Chủ nhật đạt bình quân `1,052,272,167 VND/ngày`, gấp **3.13 lần** so với Thứ 2 & Thứ 3 (`336,013,500 VND/ngày`).

### 5.3. Xếp hạng Top 10 Phim (`GET /api/v1/admin/reports/movies`)
1. *The Shawshank Redemption*: 927,876,000 VND (7,059 vé)
2. *Dune: Part Two*: 881,967,000 VND (6,808 vé)
3. *Spider-Man: Brand New Day*: 828,111,000 VND (6,482 vé)
4. *Toy Story 5*: 788,199,000 VND (5,985 vé)
5. *The Dark Knight*: 764,919,000 VND (5,925 vé)
6. *Avatar Aang: The Last Airbender*: 731,546,000 VND (5,629 vé)
7. *Demon Slayer: Kimetsu no Yaiba*: 683,366,000 VND (5,337 vé)
8. *Parasite*: 675,980,000 VND (5,130 vé)
9. *Your Name.*: 568,050,000 VND (4,564 vé)
10. *Schindler's List*: 494,637,000 VND (3,939 vé)

### 5.4. Xếp hạng Top 10 Rạp (`GET /api/v1/admin/reports/cinemas`)
1. *CGV Landmark 81* (TP. Hồ Chí Minh): 1,813,634,000 VND (323 suất)
2. *BHD Star Bitexco* (TP. Hồ Chí Minh): 1,714,332,000 VND (296 suất)
3. *Trung tâm Chiếu phim Quốc gia* (Hà Nội): 1,292,542,000 VND (205 suất)
4. *CGV Vincom Center Bà Triệu* (Hà Nội): 1,177,031,000 VND (201 suất)
5. *Lotte Cinema Đà Nẵng* (Đà Nẵng): 1,073,298,000 VND (239 suất)
6. *Lotte Cinema Nam Sài Gòn* (TP. Hồ Chí Minh): 958,844,000 VND (156 suất)
7. *CGV Vincom Đồng Khởi* (TP. Hồ Chí Minh): 951,413,000 VND (152 suất)
8. *Beta Cinemas Thanh Xuân* (Hà Nội): 875,809,000 VND (176 suất)
9. *BHD Star Phạm Ngọc Thạch* (Hà Nội): 732,336,000 VND (152 suất)
10. *CGV Vincom Đà Nẵng* (Đà Nẵng): 624,847,000 VND (127 suất)

### 5.5. Tỷ lệ lấp đầy suất chiếu (`GET /api/v1/admin/reports/showtimes/occupancy`)
* Các suất chiếu giờ vàng cuối tuần đạt đỉnh 67% – 73.23% (như suất chiếu *The Dark Knight* tại BHD Bitexco lúc 20:00 ngày 19/09 đạt 73.23%).
* Các suất sáng ngày thường đạt 8% – 18%.
* Không có suất chiếu nào âm hoặc vượt quá 100%.

---

## 6. Hướng dẫn sử dụng & Tái lập (Reproducibility)

### 6.1. Chạy lại Generator (nếu cần sinh lại file SQL)
```bash
python scripts/seed/generate_historical_demo_data.py
```
*Tạo ra file `scripts/seed/seed_data.sql` (~72 MB) và `scripts/seed/seed_metrics.json`.*

### 6.2. Nạp dữ liệu vào MySQL
```bash
mysql -u root --default-character-set=utf8mb4 cinebook < scripts/seed/seed_data.sql
```

### 6.3. Chạy kiểm tra toàn vẹn SQL (Integrity Validation)
```bash
mysql -u root cinebook < scripts/seed/validate_seed_data.sql
```

### 6.4. Chạy kiểm thử Smoke Test API Báo cáo
```bash
python scripts/seed/smoke_test_reporting.py
```
