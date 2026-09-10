<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from '@/composables/useI18n'

const { locale } = useI18n()

const activeSection = ref<string>('intro')

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
          {{ locale === 'vi' ? 'Điều khoản sử dụng' : 'Terms of Use' }}
        </span>
      </nav>

      <!-- Header -->
      <div class="border-b border-slate-800/80 pb-6 space-y-3">
        <div class="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-indigo-950/60 border border-indigo-700/50 text-indigo-400 text-xs font-semibold">
          <span>📜</span>
          <span>{{ locale === 'vi' ? 'Quy Định & Điều Khoản Dịch Vụ' : 'Service Terms & Regulations' }}</span>
        </div>
        <h1 class="text-3xl sm:text-4xl font-extrabold text-white tracking-tight">
          {{ locale === 'vi' ? 'Điều Khoản Sử Dụng CineBook' : 'CineBook Terms of Use' }}
        </h1>
        <p class="text-sm sm:text-base text-slate-400">
          {{ locale === 'vi' ? 'Có hiệu lực từ: Tháng 09/2026 • Áp dụng cho mọi khách hàng sử dụng dịch vụ trên nền tảng CineBook' : 'Effective Date: September 2026 • Applicable to all customers across the CineBook platform' }}
        </p>
      </div>

      <!-- Quick Navigation Bar -->
      <div class="p-4 rounded-2xl bg-slate-900/90 border border-slate-800 shadow-sm space-y-2">
        <p class="text-xs font-bold uppercase tracking-wider text-slate-400">
          {{ locale === 'vi' ? 'Mục lục nhanh:' : 'Quick Navigation:' }}
        </p>
        <div class="flex flex-wrap gap-2 text-xs">
          <button
            type="button"
            class="px-2.5 py-1 rounded-lg bg-slate-800/80 hover:bg-indigo-600/30 hover:text-indigo-300 text-slate-300 transition-colors"
            @click="scrollTo('sec-1')"
          >
            1. {{ locale === 'vi' ? 'Giới thiệu' : 'Introduction' }}
          </button>
          <button
            type="button"
            class="px-2.5 py-1 rounded-lg bg-slate-800/80 hover:bg-indigo-600/30 hover:text-indigo-300 text-slate-300 transition-colors"
            @click="scrollTo('sec-2')"
          >
            2. {{ locale === 'vi' ? 'Điều kiện sử dụng' : 'Eligibility' }}
          </button>
          <button
            type="button"
            class="px-2.5 py-1 rounded-lg bg-slate-800/80 hover:bg-indigo-600/30 hover:text-indigo-300 text-slate-300 transition-colors"
            @click="scrollTo('sec-3')"
          >
            3. {{ locale === 'vi' ? 'Tài khoản' : 'Accounts' }}
          </button>
          <button
            type="button"
            class="px-2.5 py-1 rounded-lg bg-slate-800/80 hover:bg-indigo-600/30 hover:text-indigo-300 text-slate-300 transition-colors"
            @click="scrollTo('sec-4')"
          >
            4. {{ locale === 'vi' ? 'Đặt vé' : 'Booking' }}
          </button>
          <button
            type="button"
            class="px-2.5 py-1 rounded-lg bg-slate-800/80 hover:bg-indigo-600/30 hover:text-indigo-300 text-slate-300 transition-colors"
            @click="scrollTo('sec-5')"
          >
            5. {{ locale === 'vi' ? 'Giữ ghế 5 phút' : 'Seat Hold' }}
          </button>
          <button
            type="button"
            class="px-2.5 py-1 rounded-lg bg-slate-800/80 hover:bg-indigo-600/30 hover:text-indigo-300 text-slate-300 transition-colors"
            @click="scrollTo('sec-6')"
          >
            6. {{ locale === 'vi' ? 'Thanh toán' : 'Payment' }}
          </button>
          <button
            type="button"
            class="px-2.5 py-1 rounded-lg bg-slate-800/80 hover:bg-indigo-600/30 hover:text-indigo-300 text-slate-300 transition-colors"
            @click="scrollTo('sec-7')"
          >
            7. {{ locale === 'vi' ? 'Giá vé & Bắp nước' : 'Pricing & F&B' }}
          </button>
          <button
            type="button"
            class="px-2.5 py-1 rounded-lg bg-slate-800/80 hover:bg-indigo-600/30 hover:text-indigo-300 text-slate-300 transition-colors"
            @click="scrollTo('sec-8')"
          >
            8. {{ locale === 'vi' ? 'Vé điện tử' : 'E-Tickets' }}
          </button>
          <button
            type="button"
            class="px-2.5 py-1 rounded-lg bg-slate-800/80 hover:bg-indigo-600/30 hover:text-indigo-300 text-slate-300 transition-colors"
            @click="scrollTo('sec-9')"
          >
            9. {{ locale === 'vi' ? 'Hủy & Hoàn tiền' : 'Refunds' }}
          </button>
          <button
            type="button"
            class="px-2.5 py-1 rounded-lg bg-slate-800/80 hover:bg-indigo-600/30 hover:text-indigo-300 text-slate-300 transition-colors"
            @click="scrollTo('sec-10')"
          >
            10. {{ locale === 'vi' ? 'Quy tắc rạp chiếu' : 'Cinema Rules' }}
          </button>
          <button
            type="button"
            class="px-2.5 py-1 rounded-lg bg-slate-800/80 hover:bg-indigo-600/30 hover:text-indigo-300 text-slate-300 transition-colors"
            @click="scrollTo('sec-11')"
          >
            11. {{ locale === 'vi' ? 'Trách nhiệm' : 'Obligations' }}
          </button>
          <button
            type="button"
            class="px-2.5 py-1 rounded-lg bg-slate-800/80 hover:bg-indigo-600/30 hover:text-indigo-300 text-slate-300 transition-colors"
            @click="scrollTo('sec-12')"
          >
            12. {{ locale === 'vi' ? 'Thay đổi điều khoản' : 'Amendments' }}
          </button>
          <button
            type="button"
            class="px-2.5 py-1 rounded-lg bg-slate-800/80 hover:bg-indigo-600/30 hover:text-indigo-300 text-slate-300 transition-colors"
            @click="scrollTo('sec-13')"
          >
            13. {{ locale === 'vi' ? 'Liên hệ' : 'Contact' }}
          </button>
        </div>
      </div>

      <!-- Main Content Card -->
      <div class="rounded-3xl bg-slate-900/70 border border-slate-800/80 p-6 sm:p-10 space-y-10 text-sm sm:text-base text-slate-300 leading-relaxed shadow-xl">
        <!-- 1. Giới thiệu -->
        <section id="sec-1" class="space-y-3 scroll-mt-24">
          <h2 class="text-lg sm:text-xl font-bold text-white flex items-center gap-2.5 border-b border-slate-800/60 pb-2">
            <span class="text-indigo-400 font-mono text-base">1.</span>
            {{ locale === 'vi' ? 'Giới Thiệu & Phạm Vi Áp Dụng' : 'Introduction & Scope' }}
          </h2>
          <p>
            {{ locale === 'vi'
              ? 'Chào mừng bạn đến với CineBook — nền tảng đặt vé xem phim và combo bắp nước trực tuyến. Các điều khoản này quy định quyền và trách nhiệm pháp lý giữa bạn (khách hàng sử dụng dịch vụ) và CineBook khi truy cập, đăng ký tài khoản, tìm kiếm suất chiếu hoặc mua vé trên trang web.'
              : 'Welcome to CineBook — your online cinema ticketing and concession platform. These terms govern the relationship between you (the customer) and CineBook when accessing our website, creating an account, browsing showtimes, or purchasing movie tickets.'
            }}
          </p>
        </section>

        <!-- 2. Điều kiện sử dụng -->
        <section id="sec-2" class="space-y-3 scroll-mt-24">
          <h2 class="text-lg sm:text-xl font-bold text-white flex items-center gap-2.5 border-b border-slate-800/60 pb-2">
            <span class="text-indigo-400 font-mono text-base">2.</span>
            {{ locale === 'vi' ? 'Điều Kiện Sử Dụng Dịch Vụ' : 'Eligibility to Use the Service' }}
          </h2>
          <p>
            {{ locale === 'vi'
              ? 'Để sử dụng đầy đủ các tính năng như đặt vé trực tuyến, lưu lịch sử giao dịch và tích điểm, khách hàng cần tạo tài khoản CineBook hợp lệ. Khách hàng phải đủ độ tuổi luật định để thực hiện các giao dịch thanh toán trực tuyến hoặc có sự bảo hộ của cha mẹ hoặc người giám hộ hợp pháp.'
              : 'To access complete features including ticket reservations, order history, and promotional benefits, you must register a valid CineBook account. Users must possess legal capacity to engage in online payment transactions or have consent from a parent or legal guardian.'
            }}
          </p>
        </section>

        <!-- 3. Tài khoản -->
        <section id="sec-3" class="space-y-3 scroll-mt-24">
          <h2 class="text-lg sm:text-xl font-bold text-white flex items-center gap-2.5 border-b border-slate-800/60 pb-2">
            <span class="text-indigo-400 font-mono text-base">3.</span>
            {{ locale === 'vi' ? 'Tài Khoản & Bảo Mật Đăng Nhập' : 'Accounts & Login Security' }}
          </h2>
          <ul class="list-disc list-inside space-y-2 text-slate-300">
            <li>{{ locale === 'vi' ? 'Bạn có trách nhiệm cung cấp thông tin cá nhân chính xác (họ tên, email, số điện thoại) khi đăng ký và cập nhật khi có thay đổi.' : 'You agree to provide accurate, current personal information upon registration and keep it updated.' }}</li>
            <li>{{ locale === 'vi' ? 'Bạn có trách nhiệm bảo mật mật khẩu và thiết bị đăng nhập. Không chia sẻ tài khoản cho người khác để tránh rủi ro mất mát giao dịch.' : 'You are solely responsible for maintaining the confidentiality of your password and device access.' }}</li>
            <li>{{ locale === 'vi' ? 'CineBook có quyền tạm khóa hoặc chấm dứt tài khoản nếu phát hiện hành vi gian lận, đầu cơ gom vé, hoặc cố ý can thiệp vào hoạt động bình thường của hệ thống.' : 'CineBook reserves the right to suspend or terminate accounts in instances of fraud, ticket scalping, or malicious system interference.' }}</li>
          </ul>
        </section>

        <!-- 4. Đặt vé -->
        <section id="sec-4" class="space-y-3 scroll-mt-24">
          <h2 class="text-lg sm:text-xl font-bold text-white flex items-center gap-2.5 border-b border-slate-800/60 pb-2">
            <span class="text-indigo-400 font-mono text-base">4.</span>
            {{ locale === 'vi' ? 'Quy Trình Đặt Vé Trực Tuyến' : 'Online Booking Process' }}
          </h2>
          <p>
            {{ locale === 'vi'
              ? 'Khách hàng có thể tìm kiếm phim đang chiếu hoặc sắp chiếu, lựa chọn cụm rạp, phòng chiếu, khung giờ và vị trí ghế ngồi mong muốn trên sơ đồ phòng chiếu trực quan. Mỗi giao dịch cho phép đặt tối đa 8 ghế trong một lần thanh toán.'
              : 'You can discover currently showing or upcoming movies, select cinema locations, auditoriums, showtimes, and preferred seat positions via the visual seat layout. Up to 8 seats may be booked per transaction.'
            }}
          </p>
        </section>

        <!-- 5. Giữ ghế -->
        <section id="sec-5" class="space-y-3 scroll-mt-24">
          <h2 class="text-lg sm:text-xl font-bold text-white flex items-center gap-2.5 border-b border-slate-800/60 pb-2">
            <span class="text-indigo-400 font-mono text-base">5.</span>
            {{ locale === 'vi' ? 'Cơ Chế Giữ Chỗ Tạm Thời (5 Phút)' : '5-Minute Temporary Seat Hold Mechanism' }}
          </h2>
          <div class="p-4 rounded-2xl bg-amber-950/40 border border-amber-500/40 space-y-2 text-xs sm:text-sm text-amber-200">
            <p class="font-bold text-amber-300 flex items-center gap-2">
              <span>⏱️</span>
              <span>{{ locale === 'vi' ? 'Thời hạn giữ ghế:' : 'Seat Hold Expiration:' }}</span>
            </p>
            <p>
              {{ locale === 'vi'
                ? 'Khi bạn bấm tiếp tục từ bước chọn ghế, hệ thống sẽ tạm giữ các ghế bạn đã chọn trong đúng 5 phút (300 giây). Trong thời gian này, không ai khác có thể chọn hoặc đặt trùng các ghế này.'
                : 'Upon proceeding from the seat map, the system locks your selected seats for exactly 5 minutes (300 seconds). No other user may reserve or purchase these seats during this window.'
              }}
            </p>
            <p>
              {{ locale === 'vi'
                ? 'Nếu sau 5 phút bạn chưa hoàn tất thanh toán, thời hạn giữ ghế sẽ tự động kết thúc và các vị trí ghế sẽ được mở lại công khai cho các khán giả khác.'
                : 'If payment is not finalized within 5 minutes, the hold expires automatically and the seats are released back to the general audience.'
              }}
            </p>
          </div>
        </section>

        <!-- 6. Thanh toán -->
        <section id="sec-6" class="space-y-3 scroll-mt-24">
          <h2 class="text-lg sm:text-xl font-bold text-white flex items-center gap-2.5 border-b border-slate-800/60 pb-2">
            <span class="text-indigo-400 font-mono text-base">6.</span>
            {{ locale === 'vi' ? 'Phương Thức Thanh Toán & Xác Nhận Giao Dịch' : 'Payment Methods & Transaction Confirmation' }}
          </h2>
          <p>
            {{ locale === 'vi'
              ? 'Thanh toán trực tuyến được hỗ trợ thông qua cổng thanh toán VNPay, cho phép thanh toán qua ứng dụng ngân hàng quét mã QR, thẻ ATM nội địa, hoặc thẻ thanh toán quốc tế (Visa/Mastercard).'
              : 'Online payment is facilitated through VNPay, accepting mobile banking QR codes, domestic ATM cards, and international payment cards (Visa/Mastercard).'
            }}
          </p>
          <p class="text-xs sm:text-sm text-slate-400">
            {{ locale === 'vi'
              ? 'Giao dịch chỉ được coi là hoàn tất khi hệ thống máy chủ CineBook nhận được xác thực thành công từ cổng thanh toán và cập nhật trạng thái đơn hàng sang Đã thanh toán.'
              : 'A transaction is recognized as complete only after CineBook verifies the successful payment confirmation from the gateway and marks your booking as Paid.'
            }}
          </p>
        </section>

        <!-- 7. Giá vé & F&B -->
        <section id="sec-7" class="space-y-3 scroll-mt-24">
          <h2 class="text-lg sm:text-xl font-bold text-white flex items-center gap-2.5 border-b border-slate-800/60 pb-2">
            <span class="text-indigo-400 font-mono text-base">7.</span>
            {{ locale === 'vi' ? 'Giá Vé, Phụ Thu & Dịch Vụ Bắp Nước (F&B)' : 'Ticket Pricing, Surcharges & Concessions' }}
          </h2>
          <ul class="list-disc list-inside space-y-2 text-slate-300">
            <li>{{ locale === 'vi' ? 'Giá vé được niêm yết bằng Việt Nam Đồng (VND) và được tính dựa trên giá cơ sở của suất chiếu cộng với phụ thu loại ghế (Ghế Thường, VIP, Ghế đôi Couple) cùng chính sách phụ thu theo ngày cuối tuần hoặc khung giờ chiếu cao điểm.' : 'Ticket prices are in Vietnamese Dong (VND), computed from showtime base pricing plus seat tier adjustments (Standard, VIP, Couple) and weekend/peak-hour surcharges.' }}</li>
            <li>{{ locale === 'vi' ? 'Ghế đôi (Couple) dành cho 2 người ngồi, giá vé tương đương 2 vị trí ngồi cộng với phụ thu ghế đôi theo quy định của rạp.' : 'Couple seats accommodate 2 people, calculated based on 2 seat units plus the applicable couple surcharge.' }}</li>
            <li>{{ locale === 'vi' ? 'Combo bắp nước đặt kèm vé sẽ được xuất thành mã nhận tại quầy bắp nước của rạp chiếu tương ứng.' : 'Food and concession combos ordered with tickets are issued for collection at the corresponding cinema concessions counter.' }}</li>
          </ul>
        </section>

        <!-- 8. Vé điện tử -->
        <section id="sec-8" class="space-y-3 scroll-mt-24">
          <h2 class="text-lg sm:text-xl font-bold text-white flex items-center gap-2.5 border-b border-slate-800/60 pb-2">
            <span class="text-indigo-400 font-mono text-base">8.</span>
            {{ locale === 'vi' ? 'Vé Điện Tử & Quy Định Vào Rạp' : 'Electronic Ticket Passes & Cinema Admission' }}
          </h2>
          <p>
            {{ locale === 'vi'
              ? 'Sau khi thanh toán thành công, đơn hàng sẽ được cấp Vé điện tử chứa Mã QR duy nhất đại diện cho toàn bộ các ghế trong đơn. Khi đến rạp, bạn chỉ cần xuất trình mã QR này trên màn hình điện thoại cho nhân viên soát vé.'
              : 'Upon successful payment, an Electronic Ticket containing a unified QR code representing all seats in your booking is issued. Simply display this QR code on your mobile device at the cinema entrance.'
            }}
          </p>
          <p class="text-xs sm:text-sm text-slate-400">
            {{ locale === 'vi'
              ? 'Lưu ý bảo quản mã QR cẩn thận, không chia sẻ công khai lên mạng xã hội để tránh trường hợp người khác sử dụng mã vé trước khi bạn vào rạp.'
              : 'Keep your QR code confidential and do not share it on public forums to prevent unauthorized check-in.'
            }}
          </p>
        </section>

        <!-- 9. Hủy & Hoàn tiền -->
        <section id="sec-9" class="space-y-3 scroll-mt-24">
          <h2 class="text-lg sm:text-xl font-bold text-white flex items-center gap-2.5 border-b border-slate-800/60 pb-2">
            <span class="text-indigo-400 font-mono text-base">9.</span>
            {{ locale === 'vi' ? 'Chính Sách Hủy Vé & Hoàn Tiền' : 'Cancellation & Refund Terms' }}
          </h2>
          <p>
            {{ locale === 'vi'
              ? 'Khách hàng có thể thực hiện yêu cầu hoàn tiền trực tuyến cho các đơn vé đã thanh toán với điều kiện: suất chiếu chưa diễn ra và thời gian đến giờ chiếu còn ít nhất 2 giờ (120 phút), đồng thời vé chưa qua cửa kiểm soát. Chi tiết tham khảo tại trang Chính sách hoàn tiền của CineBook.'
              : 'Customers may request an online refund for paid tickets provided the showtime is at least 2 hours (120 minutes) away and tickets remain unredeemed. Please refer to our Refund Policy for complete details.'
            }}
          </p>
        </section>

        <!-- 10. Quy tắc rạp chiếu -->
        <section id="sec-10" class="space-y-3 scroll-mt-24">
          <h2 class="text-lg sm:text-xl font-bold text-white flex items-center gap-2.5 border-b border-slate-800/60 pb-2">
            <span class="text-indigo-400 font-mono text-base">10.</span>
            {{ locale === 'vi' ? 'Quy Tắc Sử Dụng Dịch Vụ & Văn Hóa Xem Phim' : 'Cinema Conduct & Age Classification' }}
          </h2>
          <ul class="list-disc list-inside space-y-2 text-slate-300">
            <li>{{ locale === 'vi' ? 'Tuân thủ nghiêm túc phân loại độ tuổi của phim theo quy định của Cục Điện ảnh (P, K, T13, T16, T18, C). Nhân viên rạp có quyền kiểm tra giấy tờ tùy thân và từ chối vào phòng chiếu nếu không đủ tuổi.' : 'Adhere strictly to official movie age classifications (P, K, T13, T16, T18, C). Cinema staff reserve the right to verify age credentials and refuse admission if conditions are unmet.' }}</li>
            <li>{{ locale === 'vi' ? 'Không quay phim, ghi âm hoặc phát trực tiếp nội dung bộ phim trong phòng chiếu dưới mọi hình thức.' : 'Recording, filming, or live-streaming movie content within auditoriums is strictly prohibited.' }}</li>
            <li>{{ locale === 'vi' ? 'Giữ trật tự, chuyển điện thoại sang chế độ rung hoặc im lặng khi xem phim.' : 'Maintain decorum and keep mobile devices on silent mode throughout the screening.' }}</li>
          </ul>
        </section>

        <!-- 11. Trách nhiệm -->
        <section id="sec-11" class="space-y-3 scroll-mt-24">
          <h2 class="text-lg sm:text-xl font-bold text-white flex items-center gap-2.5 border-b border-slate-800/60 pb-2">
            <span class="text-indigo-400 font-mono text-base">11.</span>
            {{ locale === 'vi' ? 'Quyền & Trách Nhiệm Của Người Dùng' : 'User Rights & Responsibilities' }}
          </h2>
          <p>
            {{ locale === 'vi'
              ? 'Khách hàng có quyền được hỗ trợ giải quyết các thắc mắc về đơn vé, lịch chiếu và thanh toán. Đồng thời, khách hàng cam kết không sử dụng bất kỳ công cụ can thiệp tự động (bot, script) để đặt gom chỗ hoặc làm gián đoạn hệ thống.'
              : 'You are entitled to prompt support regarding orders, showtimes, and payments. You agree not to deploy automated tools (bots, scrapers) to manipulate seat holds or disrupt platform availability.'
            }}
          </p>
        </section>

        <!-- 12. Thay đổi điều khoản -->
        <section id="sec-12" class="space-y-3 scroll-mt-24">
          <h2 class="text-lg sm:text-xl font-bold text-white flex items-center gap-2.5 border-b border-slate-800/60 pb-2">
            <span class="text-indigo-400 font-mono text-base">12.</span>
            {{ locale === 'vi' ? 'Thay Đổi Điều Khoản Sử Dụng' : 'Amendments to Terms' }}
          </h2>
          <p>
            {{ locale === 'vi'
              ? 'CineBook có thể cập nhật, sửa đổi các điều khoản này định kỳ nhằm phù hợp với quy định pháp luật và hoạt động thực tế. Phiên bản cập nhật sẽ được công bố công khai trên trang web với ngày hiệu lực mới nhất.'
              : 'CineBook may update these terms periodically to reflect legal and operational changes. Revised versions will be published on this page with an updated effective date.'
            }}
          </p>
        </section>

        <!-- 13. Liên hệ -->
        <section id="sec-13" class="space-y-3 scroll-mt-24">
          <h2 class="text-lg sm:text-xl font-bold text-white flex items-center gap-2.5 border-b border-slate-800/60 pb-2">
            <span class="text-indigo-400 font-mono text-base">13.</span>
            {{ locale === 'vi' ? 'Thông Tin Liên Hệ & Hỗ Trợ' : 'Contact & Support' }}
          </h2>
          <p>
            {{ locale === 'vi'
              ? 'Nếu bạn có bất kỳ câu hỏi hoặc cần hỗ trợ về đơn hàng và dịch vụ, vui lòng liên hệ với đội ngũ chăm sóc khách hàng CineBook qua mục Câu hỏi thường gặp (FAQ) hoặc kênh hỗ trợ trực tuyến trên hệ thống.'
              : 'If you have questions or require assistance regarding orders or services, please visit our FAQ page or reach out via our online customer support channels.'
            }}
          </p>
        </section>
      </div>
    </div>
  </div>
</template>
