<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from '@/composables/useI18n'

const { locale } = useI18n()

type FaqCategory = 'all' | 'account' | 'booking' | 'payment' | 'ticket' | 'refund'

interface FaqItem {
  id: string
  category: 'account' | 'booking' | 'payment' | 'ticket' | 'refund'
  qVi: string
  qEn: string
  aVi: string
  aEn: string
}

const activeCategory = ref<FaqCategory>('all')
const searchQuery = ref('')
const openFaqIds = ref<Set<string>>(new Set(['booking-hold']))

const faqs: FaqItem[] = [
  // Tài khoản
  {
    id: 'account-register',
    category: 'account',
    qVi: 'Làm sao để đăng ký tài khoản thành viên CineBook?',
    qEn: 'How do I register a CineBook member account?',
    aVi: 'Bạn chỉ cần bấm vào nút "Đăng ký" ở góc trên bên phải trang web, nhập họ tên, địa chỉ email, số điện thoại và mật khẩu. Sau khi hoàn tất biểu mẫu, tài khoản của bạn sẽ được kích hoạt ngay để sử dụng dịch vụ.',
    aEn: 'Click the "Register" button in the top-right header, fill in your full name, email, phone number, and password. Your account is activated immediately upon submission.',
  },
  {
    id: 'account-verify',
    category: 'account',
    qVi: 'Làm sao để xác minh địa chỉ email tài khoản?',
    qEn: 'How do I verify my account email address?',
    aVi: 'Hệ thống gửi một email chứa liên kết xác thực đến hòm thư bạn đã đăng ký. Bạn chỉ cần mở hòm thư và nhấn vào liên kết xác nhận để hoàn tất việc xác minh tài khoản.',
    aEn: 'A verification link is dispatched to your registered email address. Simply open the email and click the confirmation link to verify your account.',
  },
  {
    id: 'account-forgot',
    category: 'account',
    qVi: 'Tôi quên mật khẩu đăng nhập thì phải làm gì?',
    qEn: 'What should I do if I forget my password?',
    aVi: 'Tại trang Đăng nhập, hãy bấm vào "Quên mật khẩu?", nhập địa chỉ email đã đăng ký của bạn. CineBook sẽ gửi một liên kết đặt lại mật khẩu an toàn qua email để bạn thiết lập mật khẩu mới.',
    aEn: 'On the Login page, click "Forgot Password?", enter your registered email address, and CineBook will send you a secure reset link to establish a new password.',
  },

  // Đặt vé
  {
    id: 'booking-select',
    category: 'booking',
    qVi: 'Quy trình chọn ghế và đặt vé diễn ra như thế nào?',
    qEn: 'How does the seat selection and booking process work?',
    aVi: 'Bạn chọn bộ phim muốn xem, chọn rạp chiếu, ngày và suất chiếu phù hợp. Tiếp đó, hệ thống sẽ mở sơ đồ ghế ngồi trực quan để bạn chọn vị trí mong muốn (ghế Thường, VIP hoặc ghế đôi Couple), chọn kèm combo bắp nước nếu muốn, rồi bấm tiếp tục thanh toán.',
    aEn: 'Select your preferred movie, cinema location, screening date, and showtime. You will be directed to the interactive seat map where you can pick your seats (Standard, VIP, or Couple), choose optional snack combos, and proceed to payment.',
  },
  {
    id: 'booking-hold',
    category: 'booking',
    qVi: 'Ghế được giữ trong bao lâu trước khi thanh toán?',
    qEn: 'How long are my selected seats held before payment?',
    aVi: 'Hệ thống CineBook giữ chỗ cho bạn trong đúng 5 phút (300 giây). Trong thời gian này, các ghế bạn đã chọn được khóa tạm thời và không ai khác có thể đặt trùng. Đồng hồ đếm ngược được hiển thị liên tục trên màn hình để bạn tiện theo dõi.',
    aEn: 'CineBook holds your selected seats for exactly 5 minutes (300 seconds). During this interval, your seats are temporarily locked and unavailable to others. A visible countdown timer assists your progress.',
  },
  {
    id: 'booking-hold-expired',
    category: 'booking',
    qVi: 'Điều gì xảy ra nếu thời gian giữ ghế 5 phút bị hết hạn?',
    qEn: 'What happens if the 5-minute seat hold expires?',
    aVi: 'Nếu hết thời gian 5 phút mà bạn chưa hoàn tất thanh toán, đơn giữ chỗ sẽ tự động hết hạn và các vị trí ghế sẽ được giải phóng ngay lập tức để khán giả khác có thể chọn mua.',
    aEn: 'If payment is not completed within 5 minutes, the reservation expires automatically, and the seats are released back to public availability.',
  },
  {
    id: 'booking-orphan-rule',
    category: 'booking',
    qVi: 'Quy tắc "Không để lại ghế trống đơn lẻ" (Orphan Seat) là gì?',
    qEn: 'What is the "No Single Orphan Seat" rule?',
    aVi: 'Để tối ưu hóa số lượng khán giả vào rạp, hệ thống không cho phép đặt ghế để lại duy nhất 1 ghế trống đơn độc ở giữa các ghế đã chọn hoặc cạnh lối đi. Bạn vui lòng chọn các ghế liền kề nhau để đảm bảo sự liền mạch cho hàng ghế.',
    aEn: 'To maximize auditorium seating efficiency, the system prohibits leaving a single isolated empty seat between booked seats or along aisle edges. Please select contiguous seats.',
  },
  {
    id: 'booking-couple',
    category: 'booking',
    qVi: 'Ghế đôi (Couple) hoạt động như thế nào?',
    qEn: 'How do Couple seats work?',
    aVi: 'Ghế đôi dành cho 2 người ngồi, chiếm 2 vị trí liền kề trên sơ đồ ghế. Khi bạn chọn một ghế đôi, hệ thống sẽ tự động chọn cả 2 vị trí. Giá vé ghế đôi được tính tương ứng cho 2 người kèm mức phụ thu ghế đôi theo quy định.',
    aEn: 'Couple seats are designed for 2 individuals and occupy 2 adjacent positions on the seat map. Selecting a couple seat automatically reserves both units. Pricing reflects two admissions plus the designated couple surcharge.',
  },

  // Thanh toán
  {
    id: 'payment-methods',
    category: 'payment',
    qVi: 'CineBook hỗ trợ những hình thức thanh toán trực tuyến nào?',
    qEn: 'What online payment methods does CineBook support?',
    aVi: 'CineBook hỗ trợ thanh toán trực tuyến qua cổng thanh toán VNPay, bao gồm: quét mã VNPAY-QR bằng ứng dụng ngân hàng/ví điện tử, thẻ ATM hoặc tài khoản ngân hàng nội địa, và thẻ thanh toán quốc tế Visa/Mastercard.',
    aEn: 'CineBook accepts online payments via the VNPay gateway, including: VNPAY-QR mobile banking app scan, domestic bank ATM cards/accounts, and international Visa/Mastercard payment cards.',
  },
  {
    id: 'payment-success',
    category: 'payment',
    qVi: 'Khi nào đơn hàng được tính là thanh toán thành công?',
    qEn: 'When is a payment confirmed as successful?',
    aVi: 'Ngay khi cổng thanh toán xác nhận trừ tiền thành công, máy chủ CineBook sẽ chuyển trạng thái đơn sang Đã thanh toán (PAID), tự động xuất vé điện tử kèm mã QR và gửi thông báo xác nhận trong ứng dụng cho bạn.',
    aEn: 'As soon as the payment gateway validates the completed charge, CineBook transitions the booking to Paid (PAID), generates your QR e-ticket pass, and dispatches an in-app confirmation notification.',
  },
  {
    id: 'payment-cancel-vnpay',
    category: 'payment',
    qVi: 'Nếu tôi hủy giao dịch hoặc quay lại từ cổng VNPay thì sao?',
    qEn: 'What happens if I cancel or exit during payment on the VNPay gateway?',
    aVi: 'Khi bạn bấm hủy hoặc quay lại trên cổng thanh toán, giao dịch thanh toán cụ thể đó sẽ chuyển sang trạng thái Hủy. Tuy nhiên, đơn đặt vé và các ghế đã chọn vẫn được giữ trong thời gian 5 phút ban đầu. Bạn có thể bấm "Thanh toán lại" để thử lại, hoặc bấm "Hủy đơn" để chủ động giải phóng ghế ngay lập tức.',
    aEn: 'If you cancel or navigate back from the gateway, that particular payment attempt is marked as Cancelled. However, your booking reservation and seat hold remain intact for the duration of the original 5-minute hold. You can choose "Retry Payment" or click "Cancel Booking" to release seats immediately.',
  },
  {
    id: 'payment-retry',
    category: 'payment',
    qVi: 'Tôi có thể thanh toán lại nếu lần đầu bị lỗi không?',
    qEn: 'Can I retry payment if my first attempt encounters an error?',
    aVi: 'Có. Miễn là thời gian giữ ghế 5 phút của bạn vẫn còn hiệu lực, bạn hoàn toàn có thể chọn lại phương thức và tiến hành thanh toán lại mà không cần phải chọn lại ghế từ đầu.',
    aEn: 'Yes. As long as your 5-minute seat hold timer remains active, you can initiate a fresh payment attempt without having to re-select your seats from scratch.',
  },

  // Vé & Soát vé
  {
    id: 'ticket-access',
    category: 'ticket',
    qVi: 'Tôi có thể xem vé điện tử đã mua ở đâu?',
    qEn: 'Where can I access my purchased e-tickets?',
    aVi: 'Vé điện tử hiển thị ngay trên màn hình kết quả thanh toán sau khi hoàn tất. Ngoài ra, bạn có thể xem lại bất cứ lúc nào bằng cách vào menu tài khoản và chọn mục "Vé của tôi" (My Bookings).',
    aEn: 'Your electronic ticket pass is displayed on the payment confirmation screen immediately upon success. You can also view it anytime by navigating to "My Bookings" in your user menu.',
  },
  {
    id: 'ticket-qr-code',
    category: 'ticket',
    qVi: 'Mã QR trên vé điện tử dùng để làm gì?',
    qEn: 'What is the QR code on the e-ticket used for?',
    aVi: 'Mã QR là mã định danh bảo mật dùng để quét soát vé tại cửa vào phòng chiếu. Mỗi đơn đặt vé chỉ có một mã QR duy nhất đại diện cho toàn bộ các ghế đã mua trong đơn đó, giúp cả nhóm bạn vào rạp nhanh chóng cùng nhau.',
    aEn: 'The QR code serves as your secure admissions pass scanned at the auditorium entrance. Each booking possesses a single unified QR code covering all booked seats, allowing your entire party to enter smoothly together.',
  },
  {
    id: 'ticket-print',
    category: 'ticket',
    qVi: 'Tôi có bắt buộc phải in vé ra giấy khi đến rạp không?',
    qEn: 'Do I need to print a physical paper ticket at the cinema?',
    aVi: 'Không. Bạn không cần in vé giấy. Chỉ cần mở điện thoại có kết nối mạng hoặc lưu ảnh vé điện tử chứa mã QR để nhân viên tại cửa rạp quét kiểm tra.',
    aEn: 'No physical printing is required. Simply present your electronic ticket pass with its QR code on your smartphone screen at the cinema entrance.',
  },

  // Hủy & Hoàn tiền
  {
    id: 'refund-eligibility',
    category: 'refund',
    qVi: 'Khi nào tôi được quyền yêu cầu hoàn tiền vé xem phim?',
    qEn: 'Under what conditions can I request a ticket refund?',
    aVi: 'Bạn có thể yêu cầu hoàn vé trực tuyến nếu đáp ứng đủ 2 điều kiện: suất chiếu còn ít nhất 2 giờ (120 phút) nữa mới bắt đầu, và vé chưa từng được quét mã QR để vào phòng chiếu. Khi còn dưới 2 giờ, nút hoàn tiền sẽ tự động bị khóa.',
    aEn: 'You may request an online refund if two criteria are met: the scheduled showtime is at least 2 hours (120 minutes) away, and tickets remain unredeemed. Under 2 hours, the refund option is automatically disabled.',
  },
  {
    id: 'refund-timeline',
    category: 'refund',
    qVi: 'Tiền hoàn lại sẽ được chuyển về đâu và mất bao lâu?',
    qEn: 'Where is the refunded money sent and how long does it take?',
    aVi: 'Số tiền thực tế bạn đã thanh toán sẽ được hoàn trả trực tiếp về tài khoản hoặc thẻ ngân hàng ban đầu. Thời gian nhận tiền phụ thuộc vào ngân hàng xử lý: thông thường từ 1 - 3 ngày làm việc đối với thẻ/tài khoản nội địa, và 7 - 15 ngày đối với thẻ quốc tế.',
    aEn: 'The net amount paid is refunded back to your original payment funding source. Bank processing takes 1–3 business days for domestic bank cards and 7–15 days for international credit cards.',
  },
]

const filteredFaqs = computed(() => {
  let list = faqs
  if (activeCategory.value !== 'all') {
    list = list.filter(item => item.category === activeCategory.value)
  }
  const query = searchQuery.value.trim().toLowerCase()
  if (query) {
    list = list.filter(item => {
      const q = locale.value === 'vi' ? item.qVi : item.qEn
      const a = locale.value === 'vi' ? item.aVi : item.aEn
      return q.toLowerCase().includes(query) || a.toLowerCase().includes(query)
    })
  }
  return list
})

function toggleFaq(id: string) {
  if (openFaqIds.value.has(id)) {
    openFaqIds.value.delete(id)
  } else {
    openFaqIds.value.add(id)
  }
}

function isOpen(id: string) {
  return openFaqIds.value.has(id)
}
</script>

<template>
  <div class="min-h-screen bg-slate-950 py-10 sm:py-14">
    <div class="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8 space-y-8">
      <!-- Breadcrumb -->
      <nav class="flex items-center gap-2 text-xs sm:text-sm text-slate-400" aria-label="Breadcrumb">
        <router-link to="/" class="hover:text-amber-400 transition-colors">
          {{ locale === 'vi' ? 'Trang chủ' : 'Home' }}
        </router-link>
        <span>/</span>
        <span class="text-slate-200 font-medium">
          {{ locale === 'vi' ? 'Câu hỏi thường gặp' : 'FAQs' }}
        </span>
      </nav>

      <!-- Header -->
      <div class="border-b border-slate-800/80 pb-6 space-y-3">
        <div class="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-indigo-950/60 border border-indigo-700/50 text-indigo-400 text-xs font-semibold">
          <span>❓</span>
          <span>{{ locale === 'vi' ? 'Trung Tâm Trợ Giúp Khách Hàng' : 'Help & Customer Support Center' }}</span>
        </div>
        <h1 class="text-3xl sm:text-4xl font-extrabold text-white tracking-tight">
          {{ locale === 'vi' ? 'Câu Hỏi Thường Gặp (FAQs)' : 'Frequently Asked Questions' }}
        </h1>
        <p class="text-sm sm:text-base text-slate-400">
          {{ locale === 'vi' ? 'Giải đáp tường tận về tài khoản, cách chọn ghế, thời gian giữ chỗ 5 phút, thanh toán và hoàn vé tại CineBook' : 'Detailed answers on accounts, seat selection, 5-minute hold rules, payment, and refunds at CineBook' }}
        </p>
      </div>

      <!-- Search Box -->
      <div class="relative">
        <input
          v-model="searchQuery"
          type="text"
          :placeholder="locale === 'vi' ? 'Tìm câu hỏi theo từ khóa (giữ ghế, hoàn vé, VNPay, QR code...)' : 'Search questions (seat hold, refund, payment, QR code...)'"
          class="w-full px-4 py-3 pl-11 rounded-2xl bg-slate-900 border border-slate-800 text-white placeholder-slate-500 text-sm focus:outline-none focus:border-indigo-500 focus:ring-1 focus:ring-indigo-500 transition-colors"
        />
        <span class="absolute left-4 top-1/2 -translate-y-1/2 text-slate-500 text-base">🔍</span>
      </div>

      <!-- Category Filter Pills -->
      <div class="flex items-center gap-2 overflow-x-auto pb-2 scrollbar-thin">
        <button
          type="button"
          class="px-3.5 py-1.5 rounded-full text-xs font-semibold shrink-0 transition-colors"
          :class="activeCategory === 'all' ? 'bg-indigo-600 text-white' : 'bg-slate-900 border border-slate-800 text-slate-400 hover:text-white'"
          @click="activeCategory = 'all'"
        >
          {{ locale === 'vi' ? 'Tất cả câu hỏi' : 'All Topics' }}
        </button>
        <button
          type="button"
          class="px-3.5 py-1.5 rounded-full text-xs font-semibold shrink-0 transition-colors"
          :class="activeCategory === 'account' ? 'bg-indigo-600 text-white' : 'bg-slate-900 border border-slate-800 text-slate-400 hover:text-white'"
          @click="activeCategory = 'account'"
        >
          {{ locale === 'vi' ? '👤 Tài khoản' : '👤 Account' }}
        </button>
        <button
          type="button"
          class="px-3.5 py-1.5 rounded-full text-xs font-semibold shrink-0 transition-colors"
          :class="activeCategory === 'booking' ? 'bg-indigo-600 text-white' : 'bg-slate-900 border border-slate-800 text-slate-400 hover:text-white'"
          @click="activeCategory = 'booking'"
        >
          {{ locale === 'vi' ? '💺 Đặt vé & Giữ ghế' : '💺 Booking & Hold' }}
        </button>
        <button
          type="button"
          class="px-3.5 py-1.5 rounded-full text-xs font-semibold shrink-0 transition-colors"
          :class="activeCategory === 'payment' ? 'bg-indigo-600 text-white' : 'bg-slate-900 border border-slate-800 text-slate-400 hover:text-white'"
          @click="activeCategory = 'payment'"
        >
          {{ locale === 'vi' ? '💳 Thanh toán' : '💳 Payments' }}
        </button>
        <button
          type="button"
          class="px-3.5 py-1.5 rounded-full text-xs font-semibold shrink-0 transition-colors"
          :class="activeCategory === 'ticket' ? 'bg-indigo-600 text-white' : 'bg-slate-900 border border-slate-800 text-slate-400 hover:text-white'"
          @click="activeCategory = 'ticket'"
        >
          {{ locale === 'vi' ? '🎟️ Vé & Mã QR' : '🎟️ E-Tickets & QR' }}
        </button>
        <button
          type="button"
          class="px-3.5 py-1.5 rounded-full text-xs font-semibold shrink-0 transition-colors"
          :class="activeCategory === 'refund' ? 'bg-indigo-600 text-white' : 'bg-slate-900 border border-slate-800 text-slate-400 hover:text-white'"
          @click="activeCategory = 'refund'"
        >
          {{ locale === 'vi' ? '💸 Hoàn tiền vé' : '💸 Refunds' }}
        </button>
      </div>

      <!-- FAQ Accordion List -->
      <div v-if="filteredFaqs.length > 0" class="space-y-3">
        <div
          v-for="item in filteredFaqs"
          :key="item.id"
          class="rounded-2xl bg-slate-900/80 border border-slate-800 transition-all duration-200 overflow-hidden shadow-sm"
          :class="{ 'border-indigo-600/60 ring-1 ring-indigo-600/30': isOpen(item.id) }"
        >
          <button
            type="button"
            class="w-full px-5 py-4 sm:px-6 sm:py-4.5 text-left flex items-center justify-between gap-4 select-none hover:bg-slate-800/40 transition-colors"
            @click="toggleFaq(item.id)"
          >
            <span class="font-semibold text-sm sm:text-base text-white">
              {{ locale === 'vi' ? item.qVi : item.qEn }}
            </span>
            <span
              class="text-indigo-400 text-base shrink-0 transition-transform duration-200"
              :class="{ 'rotate-180': isOpen(item.id) }"
            >
              ▼
            </span>
          </button>

          <div
            v-if="isOpen(item.id)"
            class="px-5 pb-5 pt-1 sm:px-6 sm:pb-5 text-xs sm:text-sm text-slate-300 border-t border-slate-800/60 leading-relaxed bg-slate-900/40"
          >
            {{ locale === 'vi' ? item.aVi : item.aEn }}
          </div>
        </div>
      </div>

      <!-- Empty Filter State -->
      <div v-else class="p-8 rounded-2xl bg-slate-900 border border-slate-800 text-center space-y-2">
        <p class="text-2xl">🔍</p>
        <p class="text-sm font-semibold text-white">
          {{ locale === 'vi' ? 'Không tìm thấy câu hỏi phù hợp' : 'No matching questions found' }}
        </p>
        <p class="text-xs text-slate-400">
          {{ locale === 'vi' ? 'Thử tìm với từ khóa khác hoặc chuyển sang danh mục Tất cả câu hỏi.' : 'Try different search keywords or switch to the All Topics tab.' }}
        </p>
      </div>
    </div>
  </div>
</template>
