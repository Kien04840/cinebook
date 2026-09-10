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
          {{ locale === 'vi' ? 'Chính sách bảo mật' : 'Privacy Policy' }}
        </span>
      </nav>

      <!-- Header -->
      <div class="border-b border-slate-800/80 pb-6 space-y-3">
        <div class="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-emerald-950/60 border border-emerald-700/50 text-emerald-400 text-xs font-semibold">
          <span>🔒</span>
          <span>{{ locale === 'vi' ? 'Bảo Vệ Quyền Riêng Tư' : 'Privacy & Data Protection' }}</span>
        </div>
        <h1 class="text-3xl sm:text-4xl font-extrabold text-white tracking-tight">
          {{ locale === 'vi' ? 'Chính Sách Bảo Mật CineBook' : 'CineBook Privacy Policy' }}
        </h1>
        <p class="text-sm sm:text-base text-slate-400">
          {{ locale === 'vi' ? 'Cập nhật lần cuối: Tháng 09/2026 • Cam kết bảo mật thông tin minh bạch và trung thực' : 'Last updated: September 2026 • Committed to transparent and truthful data protection' }}
        </p>
      </div>

      <!-- Quick Navigation Bar -->
      <div class="p-4 rounded-2xl bg-slate-900/90 border border-slate-800 shadow-sm space-y-2">
        <p class="text-xs font-bold uppercase tracking-wider text-slate-400">
          {{ locale === 'vi' ? 'Mục lục chính sách:' : 'Policy Contents:' }}
        </p>
        <div class="flex flex-wrap gap-2 text-xs">
          <button
            type="button"
            class="px-2.5 py-1 rounded-lg bg-slate-800/80 hover:bg-emerald-600/30 hover:text-emerald-300 text-slate-300 transition-colors"
            @click="scrollTo('sec-1')"
          >
            1. {{ locale === 'vi' ? 'Thông tin thu thập' : 'Data Collected' }}
          </button>
          <button
            type="button"
            class="px-2.5 py-1 rounded-lg bg-slate-800/80 hover:bg-emerald-600/30 hover:text-emerald-300 text-slate-300 transition-colors"
            @click="scrollTo('sec-2')"
          >
            2. {{ locale === 'vi' ? 'Mục đích sử dụng' : 'Purpose of Use' }}
          </button>
          <button
            type="button"
            class="px-2.5 py-1 rounded-lg bg-slate-800/80 hover:bg-emerald-600/30 hover:text-emerald-300 text-slate-300 transition-colors"
            @click="scrollTo('sec-3')"
          >
            3. {{ locale === 'vi' ? 'Dữ liệu đặt vé' : 'Booking Data' }}
          </button>
          <button
            type="button"
            class="px-2.5 py-1 rounded-lg bg-slate-800/80 hover:bg-emerald-600/30 hover:text-emerald-300 text-slate-300 transition-colors"
            @click="scrollTo('sec-4')"
          >
            4. {{ locale === 'vi' ? 'Xử lý thanh toán' : 'Payments' }}
          </button>
          <button
            type="button"
            class="px-2.5 py-1 rounded-lg bg-slate-800/80 hover:bg-emerald-600/30 hover:text-emerald-300 text-slate-300 transition-colors"
            @click="scrollTo('sec-5')"
          >
            5. {{ locale === 'vi' ? 'Bảo vệ dữ liệu' : 'Data Protection' }}
          </button>
          <button
            type="button"
            class="px-2.5 py-1 rounded-lg bg-slate-800/80 hover:bg-emerald-600/30 hover:text-emerald-300 text-slate-300 transition-colors"
            @click="scrollTo('sec-6')"
          >
            6. {{ locale === 'vi' ? 'Chia sẻ thông tin' : 'Data Sharing' }}
          </button>
          <button
            type="button"
            class="px-2.5 py-1 rounded-lg bg-slate-800/80 hover:bg-emerald-600/30 hover:text-emerald-300 text-slate-300 transition-colors"
            @click="scrollTo('sec-7')"
          >
            7. {{ locale === 'vi' ? 'Lưu trữ thông tin' : 'Retention' }}
          </button>
          <button
            type="button"
            class="px-2.5 py-1 rounded-lg bg-slate-800/80 hover:bg-emerald-600/30 hover:text-emerald-300 text-slate-300 transition-colors"
            @click="scrollTo('sec-8')"
          >
            8. {{ locale === 'vi' ? 'Quyền người dùng' : 'User Rights' }}
          </button>
          <button
            type="button"
            class="px-2.5 py-1 rounded-lg bg-slate-800/80 hover:bg-emerald-600/30 hover:text-emerald-300 text-slate-300 transition-colors"
            @click="scrollTo('sec-9')"
          >
            9. {{ locale === 'vi' ? 'Lưu trữ trình duyệt' : 'Local Storage' }}
          </button>
          <button
            type="button"
            class="px-2.5 py-1 rounded-lg bg-slate-800/80 hover:bg-emerald-600/30 hover:text-emerald-300 text-slate-300 transition-colors"
            @click="scrollTo('sec-10')"
          >
            10. {{ locale === 'vi' ? 'Thay đổi chính sách' : 'Updates' }}
          </button>
          <button
            type="button"
            class="px-2.5 py-1 rounded-lg bg-slate-800/80 hover:bg-emerald-600/30 hover:text-emerald-300 text-slate-300 transition-colors"
            @click="scrollTo('sec-11')"
          >
            11. {{ locale === 'vi' ? 'Liên hệ' : 'Contact' }}
          </button>
        </div>
      </div>

      <!-- Main Policy Content -->
      <div class="rounded-3xl bg-slate-900/70 border border-slate-800/80 p-6 sm:p-10 space-y-10 text-sm sm:text-base text-slate-300 leading-relaxed shadow-xl">
        <!-- 1. Thông tin thu thập -->
        <section id="sec-1" class="space-y-3 scroll-mt-24">
          <h2 class="text-lg sm:text-xl font-bold text-white flex items-center gap-2.5 border-b border-slate-800/60 pb-2">
            <span class="text-emerald-400 font-mono text-base">1.</span>
            {{ locale === 'vi' ? 'Thông Tin Chúng Tôi Thu Thập' : 'Information We Collect' }}
          </h2>
          <p>
            {{ locale === 'vi'
              ? 'CineBook chỉ thu thập những thông tin thực sự cần thiết nhằm phục vụ việc tạo tài khoản, xử lý đặt vé và gửi vé điện tử đến bạn:'
              : 'CineBook gathers solely the information strictly necessary to maintain your account, process cinema bookings, and deliver electronic tickets:'
            }}
          </p>
          <ul class="list-disc list-inside space-y-2 text-slate-300">
            <li><strong>{{ locale === 'vi' ? 'Thông tin đăng ký tài khoản:' : 'Account Registration:' }}</strong> {{ locale === 'vi' ? 'Họ tên, địa chỉ email, số điện thoại, mật khẩu (được băm bảo mật một chiều, hệ thống không lưu văn bản thô), và ảnh đại diện tùy chọn.' : 'Full name, email address, phone number, password (stored as a secure one-way hash, never in plain text), and optional avatar image.' }}</li>
            <li><strong>{{ locale === 'vi' ? 'Thông tin xác thực:' : 'Verification Data:' }}</strong> {{ locale === 'vi' ? 'Mã xác thực địa chỉ email và mã yêu cầu đặt lại mật khẩu khi bạn thực hiện yêu cầu.' : 'Email verification tokens and password reset request tokens.' }}</li>
          </ul>
        </section>

        <!-- 2. Mục đích sử dụng -->
        <section id="sec-2" class="space-y-3 scroll-mt-24">
          <h2 class="text-lg sm:text-xl font-bold text-white flex items-center gap-2.5 border-b border-slate-800/60 pb-2">
            <span class="text-emerald-400 font-mono text-base">2.</span>
            {{ locale === 'vi' ? 'Cách Chúng Tôi Sử Dụng Thông Tin' : 'How We Use Your Information' }}
          </h2>
          <ul class="list-disc list-inside space-y-2 text-slate-300">
            <li>{{ locale === 'vi' ? 'Xác thực tài khoản và duy trì phiên đăng nhập của bạn trên website.' : 'Authenticate your account and maintain active login sessions on the website.' }}</li>
            <li>{{ locale === 'vi' ? 'Xử lý đơn đặt vé xem phim, sắp xếp giữ chỗ, và khởi tạo vé điện tử kèm mã QR check-in.' : 'Process movie reservations, coordinate seat holds, and issue electronic QR ticket passes.' }}</li>
            <li>{{ locale === 'vi' ? 'Gửi thông báo trong ứng dụng về trạng thái thanh toán, nhắc nhở hủy vé hoặc xác nhận hoàn tiền.' : 'Send in-app notifications regarding payment confirmations, order cancellations, or refund status.' }}</li>
            <li>{{ locale === 'vi' ? 'Hỗ trợ khách hàng tra cứu và giải đáp thắc mắc liên quan đến đơn vé.' : 'Assist customer support inquiries regarding orders, showtimes, and ticket check-in.' }}</li>
          </ul>
        </section>

        <!-- 3. Dữ liệu đặt vé -->
        <section id="sec-3" class="space-y-3 scroll-mt-24">
          <h2 class="text-lg sm:text-xl font-bold text-white flex items-center gap-2.5 border-b border-slate-800/60 pb-2">
            <span class="text-emerald-400 font-mono text-base">3.</span>
            {{ locale === 'vi' ? 'Thông Tin Đặt Vé & Lịch Sử Đơn Hàng' : 'Booking Information & Order History' }}
          </h2>
          <p>
            {{ locale === 'vi'
              ? 'Khi bạn tiến hành đặt vé, hệ thống ghi nhận các thông tin gồm: bộ phim, cụm rạp, phòng chiếu, ngày và giờ chiếu, danh sách ghế ngồi đã chọn, các combo bắp nước đi kèm, mã khuyến mãi đã áp dụng (nếu có), và tổng số tiền thanh toán. Lịch sử này được lưu trữ trong mục "Vé của tôi" để bạn theo dõi và xuất trình khi vào rạp.'
              : 'When booking, the system records: movie title, cinema, auditorium, screening date and time, selected seat positions, ordered food & concession combos, applied vouchers (if any), and total order amount. This history is accessible in "My Bookings" for your reference and check-in presentation.'
            }}
          </p>
        </section>

        <!-- 4. Xử lý thanh toán -->
        <section id="sec-4" class="space-y-3 scroll-mt-24">
          <h2 class="text-lg sm:text-xl font-bold text-white flex items-center gap-2.5 border-b border-slate-800/60 pb-2">
            <span class="text-emerald-400 font-mono text-base">4.</span>
            {{ locale === 'vi' ? 'Thông Tin & Xử Lý Thanh Toán' : 'Payment Processing & Security' }}
          </h2>
          <div class="p-4 rounded-2xl bg-emerald-950/40 border border-emerald-600/40 space-y-2 text-xs sm:text-sm text-emerald-200">
            <p class="font-bold text-white flex items-center gap-2">
              <span>🛡️</span>
              <span>{{ locale === 'vi' ? 'Chính sách bảo mật thẻ thanh toán:' : 'Payment Card Security Policy:' }}</span>
            </p>
            <p>
              {{ locale === 'vi'
                ? 'CineBook KHÔNG thu thập và KHÔNG lưu trữ bất kỳ thông tin nhạy cảm nào liên quan đến số thẻ ngân hàng, ngày hết hạn hoặc mã số bảo mật CVV/CVC của bạn. Toàn bộ thao tác nhập thông tin thanh toán được thực hiện trực tiếp trên giao diện của Cổng thanh toán đối tác (VNPay).'
                : 'CineBook does NOT collect and does NOT store bank card numbers, expiration dates, or CVV/CVC security codes. Card entry takes place directly on the certified payment gateway interface (VNPay).'
              }}
            </p>
            <p>
              {{ locale === 'vi'
                ? 'Hệ thống của chúng tôi chỉ lưu mã tham chiếu giao dịch, thời điểm thanh toán, trạng thái thành công/thất bại và loại phương thức thanh toán để phục vụ việc đối soát vé và xử lý hoàn tiền khi có yêu cầu.'
                : 'Our platform stores solely transaction reference codes, payment timestamps, status indicators, and payment method categories for verification and refund reconciliation.'
              }}
            </p>
          </div>
        </section>

        <!-- 5. Bảo vệ thông tin -->
        <section id="sec-5" class="space-y-3 scroll-mt-24">
          <h2 class="text-lg sm:text-xl font-bold text-white flex items-center gap-2.5 border-b border-slate-800/60 pb-2">
            <span class="text-emerald-400 font-mono text-base">5.</span>
            {{ locale === 'vi' ? 'Biện Pháp Bảo Vệ Thông Tin' : 'How We Protect Your Data' }}
          </h2>
          <p>
            {{ locale === 'vi'
              ? 'CineBook áp dụng các biện pháp kỹ thuật và tổ chức phù hợp nhằm bảo vệ thông tin tài khoản và dữ liệu giao dịch khỏi truy cập, sử dụng hoặc tiết lộ trái phép. Mật khẩu tài khoản được bảo vệ bằng giải thuật băm một chiều an toàn; các luồng truyền dữ liệu giữa trình duyệt của bạn và máy chủ được mã hóa qua giao thức HTTPS bảo mật.'
              : 'CineBook employs appropriate technical and administrative safeguards to protect user accounts and transactional records against unauthorized access, loss, or disclosure. Passwords are irreversibly hashed; all communication between your browser and our servers is encrypted using HTTPS.'
            }}
          </p>
        </section>

        <!-- 6. Chia sẻ thông tin -->
        <section id="sec-6" class="space-y-3 scroll-mt-24">
          <h2 class="text-lg sm:text-xl font-bold text-white flex items-center gap-2.5 border-b border-slate-800/60 pb-2">
            <span class="text-emerald-400 font-mono text-base">6.</span>
            {{ locale === 'vi' ? 'Chia Sẻ Thông Tin Với Bên Thứ Ba' : 'Data Sharing with Third Parties' }}
          </h2>
          <p>
            {{ locale === 'vi'
              ? 'CineBook cam kết không bán, cho thuê hay thương mại hóa dữ liệu cá nhân của khách hàng cho bất kỳ bên thứ ba nào. Dữ liệu chỉ được chuyển tiếp trong các trường hợp cần thiết sau:'
              : 'We do not sell, rent, or trade your personal data. Information is shared strictly under the following necessary scenarios:'
            }}
          </p>
          <ul class="list-disc list-inside space-y-1.5 text-slate-300">
            <li>{{ locale === 'vi' ? 'Chuyển thông tin mã đơn hàng và số tiền sang Cổng thanh toán để xử lý giao dịch.' : 'Forwarding order codes and amounts to the payment gateway to process transactions.' }}</li>
            <li>{{ locale === 'vi' ? 'Cung cấp mã vé và thông tin suất chiếu cho cụm rạp liên quan để phục vụ việc soát vé vào rạp.' : 'Providing ticket check-in credentials to partner cinema locations for admissions verification.' }}</li>
            <li>{{ locale === 'vi' ? 'Khi có yêu cầu từ cơ quan có thẩm quyền theo quy định của pháp luật.' : 'When required by law enforcement or competent regulatory authorities.' }}</li>
          </ul>
        </section>

        <!-- 7. Lưu trữ thông tin -->
        <section id="sec-7" class="space-y-3 scroll-mt-24">
          <h2 class="text-lg sm:text-xl font-bold text-white flex items-center gap-2.5 border-b border-slate-800/60 pb-2">
            <span class="text-emerald-400 font-mono text-base">7.</span>
            {{ locale === 'vi' ? 'Lưu Trữ & Thời Hạn Bảo Lưu Dữ Liệu' : 'Data Retention Policy' }}
          </h2>
          <p>
            {{ locale === 'vi'
              ? 'Dữ liệu tài khoản của bạn được lưu giữ trong suốt thời gian tài khoản hoạt động. Lịch sử giao dịch đặt vé được lưu giữ nhằm phục vụ việc tra cứu của khách hàng, đối soát kế toán và giải quyết khiếu nại (nếu có). Bạn có quyền yêu cầu vô hiệu hóa tài khoản khi không còn nhu cầu sử dụng.'
              : 'Your account data is retained for the duration of your active registration. Booking histories are maintained for customer lookup, financial reconciliation, and dispute resolution. You may request account deactivation at any time.'
            }}
          </p>
        </section>

        <!-- 8. Quyền của người dùng -->
        <section id="sec-8" class="space-y-3 scroll-mt-24">
          <h2 class="text-lg sm:text-xl font-bold text-white flex items-center gap-2.5 border-b border-slate-800/60 pb-2">
            <span class="text-emerald-400 font-mono text-base">8.</span>
            {{ locale === 'vi' ? 'Quyền Lợi Của Người Dùng Đối Với Dữ Liệu' : 'Your Privacy Rights' }}
          </h2>
          <ul class="list-disc list-inside space-y-1.5 text-slate-300">
            <li>{{ locale === 'vi' ? 'Quyền xem và cập nhật thông tin cá nhân trong trang Hồ sơ cá nhân bất cứ lúc nào.' : 'Right to review and update your personal details in your Profile section anytime.' }}</li>
            <li>{{ locale === 'vi' ? 'Quyền đổi mật khẩu tài khoản hoặc yêu cầu gửi liên kết đặt lại mật khẩu qua email.' : 'Right to change your password or request password reset assistance via registered email.' }}</li>
            <li>{{ locale === 'vi' ? 'Quyền tra cứu toàn bộ lịch sử vé điện tử đã mua và hóa đơn thanh toán.' : 'Right to review all issued e-tickets and payment transaction histories.' }}</li>
          </ul>
        </section>

        <!-- 9. Lưu trữ trình duyệt -->
        <section id="sec-9" class="space-y-3 scroll-mt-24">
          <h2 class="text-lg sm:text-xl font-bold text-white flex items-center gap-2.5 border-b border-slate-800/60 pb-2">
            <span class="text-emerald-400 font-mono text-base">9.</span>
            {{ locale === 'vi' ? 'Lưu Trữ Trình Duyệt (Local Storage)' : 'Browser Storage & Preferences' }}
          </h2>
          <p>
            {{ locale === 'vi'
              ? 'CineBook sử dụng bộ nhớ cục bộ trên trình duyệt (Local Storage) để lưu trữ mã phiên đăng nhập an toàn và tùy chọn ngôn ngữ hiển thị (Tiếng Việt / English) của bạn. Hệ thống KHÔNG sử dụng cookie theo dõi quảng cáo của bên thứ ba.'
              : 'CineBook uses browser Local Storage to store your authenticated session tokens securely and your preferred interface language (Vietnamese / English). We do NOT deploy third-party advertising tracking cookies.'
            }}
          </p>
        </section>

        <!-- 10. Thay đổi chính sách -->
        <section id="sec-10" class="space-y-3 scroll-mt-24">
          <h2 class="text-lg sm:text-xl font-bold text-white flex items-center gap-2.5 border-b border-slate-800/60 pb-2">
            <span class="text-emerald-400 font-mono text-base">10.</span>
            {{ locale === 'vi' ? 'Thay Đổi Chính Sách Bảo Mật' : 'Changes to this Privacy Policy' }}
          </h2>
          <p>
            {{ locale === 'vi'
              ? 'Chúng tôi có thể cập nhật chính sách bảo mật này theo thời gian để phản ánh các cải tiến về tính năng dịch vụ. Mọi thay đổi quan trọng sẽ được thông báo rõ ràng trên giao diện website cùng ngày cập nhật mới nhất.'
              : 'We may revise this privacy policy from time to time to align with platform enhancements. Significant updates will be highlighted on our website alongside the revised effective date.'
            }}
          </p>
        </section>

        <!-- 11. Liên hệ -->
        <section id="sec-11" class="space-y-3 scroll-mt-24">
          <h2 class="text-lg sm:text-xl font-bold text-white flex items-center gap-2.5 border-b border-slate-800/60 pb-2">
            <span class="text-emerald-400 font-mono text-base">11.</span>
            {{ locale === 'vi' ? 'Thông Tin Liên Hệ Về Quyền Riêng Tư' : 'Contact Us Regarding Privacy' }}
          </h2>
          <p>
            {{ locale === 'vi'
              ? 'Nếu bạn có bất kỳ băn khoăn nào về cách CineBook bảo vệ thông tin cá nhân của bạn, vui lòng liên hệ với ban quản trị qua kênh hỗ trợ trực tuyến trên website CineBook.'
              : 'If you have questions or concerns regarding our privacy practices, please contact our support team through the official support channels on the CineBook platform.'
            }}
          </p>
        </section>
      </div>
    </div>
  </div>
</template>
