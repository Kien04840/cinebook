# Localization Implementation Report

> **Tài liệu tham chiếu:** `docs/implementation-reports/localization-implementation-plan.md`, `docs/implementation-reports/localization-audit.md`  
> **Trạng thái:** ✅ COMPLETE  
> **Thời điểm hoàn thành:** 2026-09-11

---

## 1. Summary

Đã triển khai đầy đủ task Localization toàn diện cho hệ thống CineBook:

- ✅ Chuẩn hóa mã lỗi máy đọc được (`ErrorCode`, `ErrorResponse.code`, `AppException`).
- ✅ Loại bỏ rò rỉ dữ liệu kỹ thuật và tăng cường bảo mật lỗi (500 sanitized, `HttpMessageNotReadableException`, loại bỏ lộ biến môi trường trong `TmdbAuthException`).
- ✅ Việt hóa dữ liệu Movie TMDB (`vi-VN`) và cơ chế 2-Step Smart Fetching Fallback (`vi-VN` → `en-US`).
- ✅ Cơ chế bảo vệ dữ liệu Admin biên tập độc lập ở cấp độ trường: `title_manual_override` và `overview_manual_override`.
- ✅ Việt hóa thể loại (Genre) từ TMDB.
- ✅ Frontend i18n: `errors.*` dictionary (30 codes) trong `vi.ts` và `en.ts`.
- ✅ `getErrorMessage()` helper — tra cứu `errors.<CODE>` → fallback backend message.
- ✅ `AdminLayout.vue`: tích hợp `useI18n` + language switcher pill (VI/EN).
- ✅ Tất cả admin views và customer views: tích hợp `useI18n`.
- ✅ `NotificationBell.vue`: tích hợp `useI18n`, relative time formatting locale-aware.
- ✅ `adminMenu.*` keys đầy đủ trong `vi.ts` và `en.ts`.
- ✅ `notifications.*` keys đầy đủ trong `vi.ts` và `en.ts`.
- ✅ `formatStatus()` locale-aware (đọc `localStorage cinebook_lang`).
- ✅ `ApiError.code?: string` field thêm vào `api.types.ts`.
- ✅ Toast arguments đúng thứ tự (`message, title`) trong tất cả callsites.

---

## 2. Initial State

- Baseline Backend: `.\mvnw.cmd test-compile` BUILD SUCCESS. 644/646 tests PASS (2 known local custom auditorium normalization failures).
- Baseline Frontend: `npm run typecheck` PASS, `npm run build` PASS.
- MySQL: Bảng `movies` có `overview` và `actors` dạng `TEXT`. Chưa có các cột `title_manual_override` và `overview_manual_override`.

---

## 3. Implementation Progress by Checkpoint

### Checkpoint 1 — Subagent A: Backend Error Contract + Security ✅ COMPLETE

**Files changed:**
- `src/main/java/com/cinebook/exception/ErrorCode.java` [NEW] — 30 error codes enum
- `src/main/java/com/cinebook/exception/ErrorResponse.java` — added `private String code;`
- `src/main/java/com/cinebook/exception/AppException.java` — added `private final String code;` + constructors
- `src/main/java/com/cinebook/exception/BadRequestException.java` — overloaded constructors
- `src/main/java/com/cinebook/exception/ConflictException.java` — overloaded constructors
- `src/main/java/com/cinebook/exception/ResourceNotFoundException.java` — overloaded constructors
- `src/main/java/com/cinebook/exception/UnauthorizedException.java` — overloaded constructors
- `src/main/java/com/cinebook/exception/ForbiddenException.java` — overloaded constructors
- `src/main/java/com/cinebook/exception/TmdbApiException.java` — overloaded constructors
- `src/main/java/com/cinebook/exception/TmdbAuthException.java` — removed TMDB_API_KEY leak; safe message + `TMDB_SERVICE_UNAVAILABLE`
- `src/main/java/com/cinebook/exception/TmdbResourceNotFoundException.java` — added ErrorCode
- `src/main/java/com/cinebook/exception/TmdbServiceException.java` — added ErrorCode
- `src/main/java/com/cinebook/exception/GlobalExceptionHandler.java` — `@Slf4j`, sanitized 500, `HttpMessageNotReadableException` handler
- `src/test/java/com/cinebook/exception/GlobalExceptionHandlerTest.java` [NEW] — 11/11 PASS

### Checkpoint 2 — Subagent B: TMDB + Movie Localization ✅ COMPLETE

**Database migration:**
- `src/main/resources/db/migration/V1_4__add_movie_manual_overrides.sql` [NEW] — đã chạy trực tiếp trên MySQL

**Files changed:**
- `src/main/java/com/cinebook/entity/Movie.java` — added `titleManualOverride`, `overviewManualOverride` Boolean fields
- `src/main/resources/application.yml` — `language: vi-VN` (was `en-US`)
- `src/main/java/com/cinebook/config/TmdbProperties.java` — default `language = "vi-VN"`
- `src/main/java/com/cinebook/service/impl/MovieServiceImpl.java` — `updateMovie()` sets override flags khi title/overview thực sự thay đổi
- `src/main/java/com/cinebook/service/impl/TmdbImportServiceImpl.java` — full rewrite: `resolveTitle()`, `resolveOverview()` (2-step fallback), `updateMovieFromTmdb()` (preserve if override=true), `createMovieFromTmdb()` (override=false)
- `src/test/java/com/cinebook/service/TmdbImportServiceTest.java` — updated mocks + 8 new localization tests
- `src/test/java/com/cinebook/service/TmdbImportServiceImplTest.java` [NEW]
- `src/test/java/com/cinebook/service/MovieServiceTest.java` — 3 manual override tests
- `src/test/java/com/cinebook/service/MovieServiceImplTest.java` [NEW]
- `docs/tmdb-import.md` — updated (§7 Smart Fallback + Curated Data Protection)

**Tests:** 112/112 PASS (Tmdb*, Movie*, AdminMovieControllerTest, GlobalExceptionHandlerTest)

### Checkpoint 3 — Subagent C: Frontend i18n + Notification Presentation ✅ COMPLETE

**Files changed:**
- `frontend/src/types/api.types.ts` — added `code?: string` to `ApiError`
- `frontend/src/utils/error.ts` [NEW] — `getErrorMessage()` helper + `getApiErrorMessage` alias
- `frontend/src/utils/formatters.ts` — `formatStatus()` locale-aware; re-exports `getErrorMessage`/`getApiErrorMessage`; `formatDuration()` locale-aware
- `frontend/src/locales/vi.ts` — added `adminMenu.*` (10 keys), `notifications.*` (13 keys + types), `errors.*` (30 codes)
- `frontend/src/locales/en.ts` — added `adminMenu.*` (10 keys), `notifications.*` (13 keys + types), `errors.*` (30 codes)
- `frontend/src/layouts/AdminLayout.vue` — `useI18n` + `{ t, locale, setLocale }` + language switcher pill VI/EN
- `frontend/src/components/common/NotificationBell.vue` — `useI18n` tích hợp đầy đủ; relative time locale-aware
- `frontend/src/components/booking/FoodSelection.vue` — `useI18n` tích hợp
- Tất cả admin views và customer views — `useI18n` tích hợp
- Toast callsites — `toast.error(message, title)` thứ tự đúng trong tất cả views

**Build:** `npm run typecheck` PASS, `npm run build` PASS (315 modules)

---

## 4. Error Contract (Chi tiết)

30 error codes chuẩn hóa trong `ErrorCode.java`:

**Auth & User:** `AUTH_FAILED`, `INVALID_CREDENTIALS`, `ACCOUNT_BLOCKED`, `UNAUTHORIZED`, `FORBIDDEN`, `TOKEN_EXPIRED`, `USER_NOT_FOUND`, `USER_ALREADY_EXISTS`, `CANNOT_MODIFY_PROTECTED_USER`

**Booking & Seat:** `SEAT_ALREADY_HELD`, `SEAT_NOT_AVAILABLE`, `BOOKING_NOT_FOUND`, `BOOKING_EXPIRED`, `BOOKING_ALREADY_PAID`, `BOOKING_ALREADY_CANCELLED`, `MAX_SEATS_EXCEEDED`, `SHOWTIME_ENDED`, `HOLD_EXPIRED`

**Payment & Refund:** `PAYMENT_FAILED`, `PAYMENT_INVALID_SIGNATURE`, `REFUND_NOT_ELIGIBLE`, `SHOWTIME_TOO_CLOSE`, `TICKET_ALREADY_REDEEMED`

**Generic & Infra:** `RESOURCE_NOT_FOUND`, `BAD_REQUEST`, `VALIDATION_FAILED`, `CONFLICT`, `INTERNAL_SERVER_ERROR`, `TMDB_SERVICE_UNAVAILABLE`, `TMDB_RESOURCE_NOT_FOUND`

---

## 5. Security Hardening (Chi tiết)

- `TmdbAuthException`: Removed hardcoded log message leaking `TMDB_API_KEY` env var name. Safe message: `"TMDB authentication failed: <reason>"`.
- `GlobalExceptionHandler`: `@Slf4j` added. Generic 500 handler logs full stack trace server-side, returns sanitized JSON without stack trace to client.
- `HttpMessageNotReadableException` handler: Returns HTTP 400 + code `BAD_REQUEST`.

---

## 6. TMDB Vietnamese Localization (Chi tiết)

- `application.yml`: `tmdb.language: ${TMDB_LANGUAGE:vi-VN}`
- `TmdbProperties.java`: `private String language = "vi-VN"`
- `TmdbImportServiceImpl`: All TMDB API calls use `tmdbProperties.getLanguage()` (vi-VN)
- `syncGenres()`: TMDB genre names fetched in `vi-VN`

---

## 7. Smart Fallback (Chi tiết)

**Title resolution** (`resolveTitle`):
1. Localized title (`vi-VN`) — if non-blank, use it
2. `original_title` — if non-blank, use it
3. Both blank → throw `BadRequestException(TMDB_RESOURCE_NOT_FOUND)`

**Overview resolution** (`resolveOverview`):
1. Localized overview (`vi-VN`) — if non-blank, use it
2. Query TMDB again with `en-US` — if non-blank, use English
3. Both blank → use `""` (empty string, never throw exception, never insert placeholder text)

---

## 8. Admin Curated Protection (Chi tiết)

- Two independent flags: `title_manual_override` (BOOLEAN NOT NULL DEFAULT FALSE) và `overview_manual_override` (BOOLEAN NOT NULL DEFAULT FALSE).
- `MovieServiceImpl.updateMovie()`: Sets `titleManualOverride=true` khi title thực sự thay đổi; Sets `overviewManualOverride=true` khi overview thực sự thay đổi.
- `TmdbImportServiceImpl.updateMovieFromTmdb()`: Bỏ qua cập nhật title nếu `titleManualOverride=true`; Bỏ qua cập nhật overview nếu `overviewManualOverride=true`.
- `createMovieFromTmdb()`: Luôn set cả hai flag = `false`.
- TMDB import/sync tuyệt đối KHÔNG set flag = `true`.

---

## 9. Genre Localization (Chi tiết)

Genre sync từ TMDB sử dụng `vi-VN` locale, bao gồm cả tên thể loại tiếng Việt (ví dụ: "Hành động", "Khoa học viễn tưởng").

---

## 10. Frontend i18n (Chi tiết)

### Error Message Flow
```
API Error response → error.response.data.code → t(`errors.${code}`)
  → fallback: error.response.data.message
  → fallback: error.message
  → fallback: provided string or t('common.errorTitle')
```

### Language Switcher
- `DefaultLayout.vue`: VI/EN pill buttons (pre-existing)
- `AdminLayout.vue`: VI/EN pill buttons thêm mới trong header

### formatStatus()
Sử dụng `getActiveLocale()` đọc `localStorage.cinebook_lang` để trả về tên trạng thái đúng ngôn ngữ.

---

## 11. Notification Localization (Chi tiết)

- Presentation-only — không thay đổi database.
- `NotificationBell.vue`: `useI18n` tích hợp; `formatRelativeTime()` dùng `t('notifications.timeMinutesAgo', {count})`.
- `notifications.types.*` keys trong `vi.ts` và `en.ts` để hiển thị loại thông báo theo ngôn ngữ.

---

## 12. Database Changes (Chi tiết)

```sql
-- V1_4__add_movie_manual_overrides.sql
ALTER TABLE movies
ADD COLUMN title_manual_override BOOLEAN NOT NULL DEFAULT FALSE,
ADD COLUMN overview_manual_override BOOLEAN NOT NULL DEFAULT FALSE;
```

Đã áp dụng trực tiếp trên MySQL. Non-destructive additive migration.

---

## 14. Tests

| Test Suite | Count | Result |
|---|---|---|
| `GlobalExceptionHandlerTest` | 11 | ✅ PASS |
| `TmdbImportServiceTest` | 25 | ✅ PASS |
| `TmdbImportServiceImplTest` | 25 | ✅ PASS |
| `MovieServiceTest` | 13 | ✅ PASS |
| `MovieServiceImplTest` | 13 | ✅ PASS |
| `AdminMovieControllerTest` | 5 | ✅ PASS |
| **Regression (Tmdb* + Movie*)** | **112** | **✅ ALL PASS** |
| **Full suite** | **712** | **710 PASS, 2 FAIL (known pre-existing)** |
| `npm run typecheck` | — | ✅ PASS |
| `npm run build` | — | ✅ PASS (315 modules) |

---

## 15. Regression Results

Full `.\mvnw.cmd clean test`:
- **712 tests total**
- **710 PASS** ✅
- **2 FAIL** — `AuditoriumNormalizationLiveIntegrationTest` (2 tests) — **pre-existing failures**, không liên quan đến task Localization

---

## 16. Known Limitations

- `errors.*` dictionary không có key `GENRE_NOT_FOUND` (chưa có error code này trong hệ thống).
- Notification `title` và `message` được lưu trong DB theo ngôn ngữ tại thời điểm tạo (backend side), không tự động dịch khi user đổi locale.

---

## 17. Discrepancies From Original Plan

1. Sử dụng 2 cờ độc lập: `title_manual_override` và `overview_manual_override` thay vì một cờ chung `is_manual_override`.
2. Overview missing không ném exception — fallback `en-US` → `""` nếu cả hai đều trống.
3. Không chèn placeholder text vào DB.

---

## 18. Documentation Updated

- `docs/tmdb-import.md` — §7 Smart Fallback + Curated Data Protection (đầy đủ)
- `docs/implementation-reports/localization-implementation.md` — báo cáo này

---

## 19. Files Changed

### Backend
- `src/main/java/com/cinebook/exception/ErrorCode.java` [NEW]
- `src/main/java/com/cinebook/exception/ErrorResponse.java`
- `src/main/java/com/cinebook/exception/AppException.java`
- `src/main/java/com/cinebook/exception/BadRequestException.java`
- `src/main/java/com/cinebook/exception/ConflictException.java`
- `src/main/java/com/cinebook/exception/ResourceNotFoundException.java`
- `src/main/java/com/cinebook/exception/UnauthorizedException.java`
- `src/main/java/com/cinebook/exception/ForbiddenException.java`
- `src/main/java/com/cinebook/exception/TmdbApiException.java`
- `src/main/java/com/cinebook/exception/TmdbAuthException.java`
- `src/main/java/com/cinebook/exception/TmdbResourceNotFoundException.java`
- `src/main/java/com/cinebook/exception/TmdbServiceException.java`
- `src/main/java/com/cinebook/exception/GlobalExceptionHandler.java`
- `src/main/java/com/cinebook/entity/Movie.java`
- `src/main/resources/application.yml`
- `src/main/java/com/cinebook/config/TmdbProperties.java`
- `src/main/java/com/cinebook/service/impl/MovieServiceImpl.java`
- `src/main/java/com/cinebook/service/impl/TmdbImportServiceImpl.java`
- `src/main/resources/db/migration/V1_4__add_movie_manual_overrides.sql` [NEW]
- `src/test/java/com/cinebook/exception/GlobalExceptionHandlerTest.java` [NEW]
- `src/test/java/com/cinebook/service/TmdbImportServiceTest.java`
- `src/test/java/com/cinebook/service/TmdbImportServiceImplTest.java` [NEW]
- `src/test/java/com/cinebook/service/MovieServiceTest.java`
- `src/test/java/com/cinebook/service/MovieServiceImplTest.java` [NEW]
- `docs/tmdb-import.md`

### Frontend
- `frontend/src/types/api.types.ts`
- `frontend/src/utils/error.ts` [NEW]
- `frontend/src/utils/formatters.ts`
- `frontend/src/locales/vi.ts`
- `frontend/src/locales/en.ts`
- `frontend/src/layouts/AdminLayout.vue`
- `frontend/src/components/common/NotificationBell.vue`
- `frontend/src/components/booking/FoodSelection.vue`
- All admin views (AdminDashboardView, AdminMoviesView, AdminGenresView, AdminFoodsView, AdminPricingView, AdminTicketsView, AdminShowtimesView, AdminCinemasView, AdminBookingsView, AdminRefundsView, AdminPromotionsView, AdminUsersView, AdminReportsView)
- All customer views (HomeView, MoviesView, MovieDetailView, ShowtimesView, CinemasView, BookingView, DemoPaymentView, PaymentResultView, MyBookingsView, ProfileView, PromotionsView)

