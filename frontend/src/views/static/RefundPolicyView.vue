<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from '@/composables/useI18n'

const { locale } = useI18n()

const activeSection = ref<string>('sec-1')

function scrollTo(id: string) {
  activeSection.value = id
  const el = document.getElementById(id)
  if (el) {
    el.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }
}
</script>

<template>
  <div class="min-h-screen bg-slate-950 py-10 sm:py-14">
    <div class="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8 space-y-8">
      <!-- Breadcrumb -->
      <nav class="flex items-center gap-2 text-xs sm:text-sm text-slate-400" aria-label="Breadcrumb">
        <router-link to="/" class="hover:text-amber-400 transition-colors">
          {{ locale === 'vi' ? 'Trang chủ' : 'Home' }}
        </router-link>
        <span>/</span>
        <span class="text-slate-200 font-medium">
          {{ locale === 'vi' ? 'Chính sách đổi trả & hoàn vé' : 'Refund Policy' }}
        </span>
      </nav>

      <!-- Header -->
      <div class="border-b border-slate-800/80 pb-6 space-y-3">
        <div class="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-indigo-950/60 border border-indigo-700/50 text-indigo-400 text-xs font-semibold">
          <span>💸</span>
          <span>{{ locale === 'vi' ? 'Quy Định Đổi Trả & Hoàn Tiền' : 'Ticket Return & Refund Policy' }}</span>
        </div>
        <h1 class="text-3xl sm:text-4xl font-extrabold text-white tracking-tight">
          {{ locale === 'vi' ? 'Chính Sách Hoàn Vé Trực Tuyến CineBook' : 'CineBook Online Ticket Refund Policy' }}
        </h1>
        <p class="text-sm sm:text-base text-slate-400">
          {{ locale === 'vi' ? 'Cập nhật lần cuối: Tháng 09/2026 • Áp dụng cho tất cả vé xem phim đặt qua nền tảng CineBook' : 'Last updated: September 2026 • Applicable to all cinema tickets purchased via CineBook' }}
        </p>
      </div>

      <!-- Golden Rule Highlight Card -->
      <div class="p-6 sm:p-8 rounded-3xl bg-gradient-to-r from-amber-950/60 via-amber-900/40 to-slate-900 border border-amber-500/60 shadow-xl space-y-3">
        <div class="flex items-center gap-2.5 text-amber-400 text-xs sm:text-sm font-bold uppercase tracking-wider">
          <span class="text-xl">⏰</span>
          <span>{{ locale === 'vi' ? 'Quy Tắc Thời Gian Bắt Buộc' : 'Mandatory Refund Time Window' }}</span>
        </div>
        <p class="text-base sm:text-xl font-bold text-white leading-snug">
          {{ locale === 'vi'
            ? 'Khách hàng chỉ có thể yêu cầu hoàn vé trước giờ suất chiếu bắt đầu ít nhất 2 giờ (120 phút).'
            : 'You may only submit an online refund request at least 2 hours (120 minutes) prior to the scheduled showtime.'
          }}
        </p>
        <p class="text-xs sm:text-sm text-amber-200/90 leading-relaxed">
          {{ locale === 'vi'
            ? 'Khi thời gian đến giờ chiếu còn dưới 2 giờ, hệ thống sẽ tự động khóa tính năng hoàn tiền trực tuyến nhằm đảm bảo công tác chuẩn bị phòng chiếu và tính công bằng cho khán giả khác.'
            : 'When less than 2 hours remain before the screening, the online refund option is automatically disabled to ensure auditorium operational scheduling and fair availability.'
          }}
        </p>
      </div>

      <!-- Quick Navigation Bar -->
      <div class="p-4 rounded-2xl bg-slate-900/90 border border-slate-800 shadow-sm space-y-2">
        <p class="text-xs font-bold uppercase tracking-wider text-slate-400">
          {{ locale === 'vi' ? 'Mục lục quy định:' : 'Policy Sections:' }}
        </p>
        <div class="flex flex-wrap gap-2 text-xs">
          <button
            type="button"
            class="px-2.5 py-1 rounded-lg bg-slate-800/80 hover:bg-indigo-600/30 hover:text-indigo-300 text-slate-300 transition-colors"
            @click="scrollTo('sec-1')"
          >
            1. {{ locale === 'vi' ? 'Điều kiện hoàn tiền' : 'Eligibility' }}
          </button>
          <button
            type="button"
            class="px-2.5 py-1 rounded-lg bg-slate-800/80 hover:bg-indigo-600/30 hover:text-indigo-300 text-slate-300 transition-colors"
            @click="scrollTo('sec-2')"
          >
            2. {{ locale === 'vi' ? 'Số tiền & Phương thức hoàn' : 'Amount & Method' }}
          </button>
          <button
            type="button"
            class="px-2.5 py-1 rounded-lg bg-slate-800/80 hover:bg-indigo-600/30 hover:text-indigo-300 text-slate-300 transition-colors"
            @click="scrollTo('sec-3')"
          >
            3. {{ locale === 'vi' ? 'Trường hợp không hỗ trợ' : 'Non-Refundable Cases' }}
          </button>
          <button
            type="button"
            class="px-2.5 py-1 rounded-lg bg-slate-800/80 hover:bg-indigo-600/30 hover:text-indigo-300 text-slate-300 transition-colors"
            @click="scrollTo('sec-4')"
          >
            4. {{ locale === 'vi' ? 'Quy trình thực hiện hoàn vé' : 'Step-by-Step Guide' }}
          </button>
          <button
            type="button"
            class="px-2.5 py-1 rounded-lg bg-slate-800/80 hover:bg-indigo-600/30 hover:text-indigo-300 text-slate-300 transition-colors"
            @click="scrollTo('sec-5')"
          >
            5. {{ locale === 'vi' ? 'Hỗ trợ khách hàng' : 'Support' }}
          </button>
        </div>
      </div>

      <!-- Main Policy Content -->
      <div class="rounded-3xl bg-slate-900/70 border border-slate-800/80 p-6 sm:p-10 space-y-10 text-sm sm:text-base text-slate-300 leading-relaxed shadow-xl">
        <!-- 1. Điều kiện hoàn tiền -->
        <section id="sec-1" class="space-y-4 scroll-mt-24">
          <h2 class="text-lg sm:text-xl font-bold text-white flex items-center gap-2.5 border-b border-slate-800/60 pb-2">
            <span class="text-indigo-400 font-mono text-base">1.</span>
            {{ locale === 'vi' ? 'Điều Kiện Hoàn Tiền Hợp Lệ' : 'Eligible Refund Conditions' }}
          </h2>
          <p>
            {{ locale === 'vi'
              ? 'Đơn đặt vé của quý khách đủ điều kiện hoàn trả khi đáp ứng đầy đủ đồng thời các tiêu chí sau:'
              : 'Your booking qualifies for an online refund only when satisfying all of the following conditions simultaneously:'
            }}
          </p>
          <ul class="list-disc list-inside space-y-2.5 text-slate-300">
            <li>
              <strong>{{ locale === 'vi' ? 'Khoảng thời gian yêu cầu:' : 'Timing Requirement:' }}</strong>
              {{ locale === 'vi' ? ' Yêu cầu hoàn vé phải được gửi trên hệ thống trước giờ suất chiếu bắt đầu ít nhất 120 phút (2 giờ).' : ' The refund request must be submitted at least 120 minutes (2 hours) prior to showtime.' }}
            </li>
            <li>
              <strong>{{ locale === 'vi' ? 'Trạng thái vé:' : 'Ticket Status:' }}</strong>
              {{ locale === 'vi' ? ' Toàn bộ các vé trong đơn hàng chưa từng qua cửa soát vé (chưa check-in/chưa quét mã QR vào phòng chiếu).' : ' All tickets in the booking must be unredeemed (not checked-in/scanned at the gate).' }}
            </li>
            <li>
              <strong>{{ locale === 'vi' ? 'Trạng thái đơn hàng:' : 'Order Status:' }}</strong>
              {{ locale === 'vi' ? ' Đơn đặt vé đã thanh toán thành công (trạng thái Đã thanh toán - PAID).' : ' The booking must have achieved successful payment status (PAID).' }}
            </li>
            <li>
              <strong>{{ locale === 'vi' ? 'Nguyên tắc hoàn trọn gói:' : 'Full Order Cancellation:' }}</strong>
              {{ locale === 'vi' ? ' Khi yêu cầu hoàn tiền được chấp thuận, toàn bộ các vé và combo bắp nước trong đơn hàng sẽ được hủy cùng lúc, và các ghế đã đặt sẽ được giải phóng ngay lập tức để phục vụ khán giả khác.' : ' Upon approved refund, all tickets and concession items in the booking are cancelled together, and seats are released immediately.' }}
            </li>
          </ul>
        </section>

        <!-- 2. Số tiền & Phương thức hoàn -->
        <section id="sec-2" class="space-y-4 scroll-mt-24">
          <h2 class="text-lg sm:text-xl font-bold text-white flex items-center gap-2.5 border-b border-slate-800/60 pb-2">
            <span class="text-indigo-400 font-mono text-base">2.</span>
            {{ locale === 'vi' ? 'Số Tiền & Phương Thức Hoàn Trả' : 'Refund Amount & Refund Method' }}
          </h2>
          <p>
            {{ locale === 'vi'
              ? 'Số tiền hoàn lại cho quý khách là số tiền thực tế đã thanh toán cho đơn hàng (sau khi đã trừ các khoản chiết khấu khuyến mãi nếu có áp dụng voucher).'
              : 'The amount credited back is the net amount actually paid for the booking (deducting any voucher discounts).'
            }}
          </p>
          <div class="p-4 rounded-2xl bg-slate-800/60 border border-slate-700/60 space-y-2 text-xs sm:text-sm">
            <p class="font-semibold text-indigo-300">
              💳 {{ locale === 'vi' ? 'Phương thức hoàn tiền:' : 'Refund Route:' }}
            </p>
            <p>
              {{ locale === 'vi'
                ? 'Tiền sẽ được hoàn trả tự động về đúng tài khoản ngân hàng hoặc thẻ thanh toán mà quý khách đã sử dụng để thanh toán qua cổng VNPay.'
                : 'Funds are returned directly to the original bank account or payment card used during the VNPay transaction.'
              }}
            </p>
            <p class="text-slate-400">
              {{ locale === 'vi'
                ? 'Thời gian nhận tiền phụ thuộc vào quy trình xử lý của ngân hàng phát hành thẻ (thông thường từ 1 - 3 ngày làm việc đối với thẻ ATM/tài khoản nội địa, và 7 - 15 ngày đối với thẻ tín dụng quốc tế).'
                : 'Credit turnaround depends on your issuing bank (typically 1–3 business days for domestic bank cards and 7–15 days for international credit cards).'
              }}
            </p>
          </div>
        </section>

        <!-- 3. Trường hợp không hỗ trợ -->
        <section id="sec-3" class="space-y-4 scroll-mt-24">
          <h2 class="text-lg sm:text-xl font-bold text-white flex items-center gap-2.5 border-b border-slate-800/60 pb-2">
            <span class="text-indigo-400 font-mono text-base">3.</span>
            {{ locale === 'vi' ? 'Các Trường Hợp Không Được Hoàn Vé' : 'Non-Refundable Circumstances' }}
          </h2>
          <ul class="list-disc list-inside space-y-2 text-rose-300/90">
            <li>{{ locale === 'vi' ? 'Thời gian yêu cầu gửi khi còn dưới 2 giờ (120 phút) đến giờ bắt đầu suất chiếu.' : 'Requests submitted less than 2 hours (120 minutes) prior to showtime.' }}</li>
            <li>{{ locale === 'vi' ? 'Bất kỳ vé nào trong đơn hàng đã quét mã QR check-in tại cửa rạp chiếu.' : 'Any ticket in the order has already been scanned at the admissions gate.' }}</li>
            <li>{{ locale === 'vi' ? 'Khán giả không đến xem phim (no-show) sau khi suất chiếu đã bắt đầu hoặc đã kết thúc.' : 'No-shows once the screening has commenced or finished.' }}</li>
            <li>{{ locale === 'vi' ? 'Đơn hàng mua trong các sự kiện chiếu phim đặc biệt có thông báo quy định không hoàn vé.' : 'Special event screenings explicitly designated as non-refundable upon purchase.' }}</li>
          </ul>
        </section>

        <!-- 4. Quy trình thực hiện hoàn vé -->
        <section id="sec-4" class="space-y-4 scroll-mt-24">
          <h2 class="text-lg sm:text-xl font-bold text-white flex items-center gap-2.5 border-b border-slate-800/60 pb-2">
            <span class="text-indigo-400 font-mono text-base">4.</span>
            {{ locale === 'vi' ? 'Hướng Dẫn Thao Tác Hoàn Vé Trên Website' : 'How to Request a Refund Online' }}
          </h2>
          <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div class="p-4 rounded-2xl bg-slate-800/50 border border-slate-700/50 space-y-2">
              <span class="text-xs font-bold text-indigo-400 uppercase tracking-wider">Bước 1 / Step 1</span>
              <p class="font-medium text-white text-sm">
                {{ locale === 'vi' ? 'Truy cập mục "Vé của tôi"' : 'Navigate to "My Bookings"' }}
              </p>
              <p class="text-xs text-slate-400">
                {{ locale === 'vi' ? 'Đăng nhập vào tài khoản và mở trang quản lý đơn vé đã đặt.' : 'Sign in to your CineBook account and open your ticket history.' }}
              </p>
            </div>
            <div class="p-4 rounded-2xl bg-slate-800/50 border border-slate-700/50 space-y-2">
              <span class="text-xs font-bold text-indigo-400 uppercase tracking-wider">Bước 2 / Step 2</span>
              <p class="font-medium text-white text-sm">
                {{ locale === 'vi' ? 'Chọn đơn vé hợp lệ' : 'Select Eligible Booking' }}
              </p>
              <p class="text-xs text-slate-400">
                {{ locale === 'vi' ? 'Kiểm tra đơn hàng còn trên 2 giờ trước giờ chiếu và bấm nút "Hoàn vé".' : 'Locate the booking (>2 hours remaining) and click "Request Refund".' }}
              </p>
            </div>
            <div class="p-4 rounded-2xl bg-slate-800/50 border border-slate-700/50 space-y-2">
              <span class="text-xs font-bold text-indigo-400 uppercase tracking-wider">Bước 3 / Step 3</span>
              <p class="font-medium text-white text-sm">
                {{ locale === 'vi' ? 'Xác nhận lý do hoàn vé' : 'Confirm Reason' }}
              </p>
              <p class="text-xs text-slate-400">
                {{ locale === 'vi' ? 'Nhập lý do hoàn vé trong hộp thoại xác nhận để hệ thống lưu trữ.' : 'Provide a brief reason in the confirmation modal.' }}
              </p>
            </div>
            <div class="p-4 rounded-2xl bg-slate-800/50 border border-slate-700/50 space-y-2">
              <span class="text-xs font-bold text-indigo-400 uppercase tracking-wider">Bước 4 / Step 4</span>
              <p class="font-medium text-white text-sm">
                {{ locale === 'vi' ? 'Nhận thông báo xác nhận' : 'Receive Notification' }}
              </p>
              <p class="text-xs text-slate-400">
                {{ locale === 'vi' ? 'Hệ thống gửi thông báo hoàn tiền thành công và cập nhật trạng thái đơn hàng.' : 'System marks the booking as Refunded and dispatches a confirmation notification.' }}
              </p>
            </div>
          </div>
        </section>

        <!-- 5. Hỗ trợ khách hàng -->
        <section id="sec-5" class="space-y-4 scroll-mt-24">
          <h2 class="text-lg sm:text-xl font-bold text-white flex items-center gap-2.5 border-b border-slate-800/60 pb-2">
            <span class="text-indigo-400 font-mono text-base">5.</span>
            {{ locale === 'vi' ? 'Hỗ Trợ & Khiếu Nại Hoàn Tiền' : 'Assistance & Inquiries' }}
          </h2>
          <p>
            {{ locale === 'vi'
              ? 'Trong trường hợp giao dịch hoàn tiền gặp sự cố hoặc quá thời hạn quy định của ngân hàng mà quý khách chưa nhận được tiền, vui lòng liên hệ ngay với đội ngũ hỗ trợ CineBook qua mục Câu hỏi thường gặp (FAQ) hoặc bộ phận quản trị để được hỗ trợ kiểm tra mã tra soát ngân hàng.'
              : 'If you encounter issues with a refund transaction or funds are delayed beyond standard banking timeframes, please contact our support team through the FAQ page or platform administrator to verify the bank trace reference.'
            }}
          </p>
        </section>
      </div>
    </div>
  </div>
</template>
