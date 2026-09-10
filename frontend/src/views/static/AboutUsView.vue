<script setup lang="ts">
import { useI18n } from '@/composables/useI18n'

const { locale } = useI18n()
</script>

<template>
  <div class="min-h-screen bg-slate-950 py-10 sm:py-16">
    <div class="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8 space-y-16">
      <!-- Breadcrumb -->
      <nav class="flex items-center gap-2 text-xs sm:text-sm text-slate-400" aria-label="Breadcrumb">
        <router-link to="/" class="hover:text-amber-400 transition-colors">
          {{ locale === 'vi' ? 'Trang chủ' : 'Home' }}
        </router-link>
        <span>/</span>
        <span class="text-slate-200 font-medium">
          {{ locale === 'vi' ? 'Về CineBook' : 'About Us' }}
        </span>
      </nav>

      <!-- Hero Header -->
      <div class="text-center space-y-4 max-w-3xl mx-auto">
        <div class="inline-flex items-center gap-2 px-3.5 py-1.5 rounded-full bg-indigo-950/70 border border-indigo-700/60 text-indigo-400 text-xs font-semibold shadow-sm">
          <span>🎬</span>
          <span>{{ locale === 'vi' ? 'Nền Tảng Đặt Vé Xem Phim Hiện Đại' : 'Modern Cinema Ticketing Platform' }}</span>
        </div>
        <h1 class="text-3xl sm:text-5xl font-black text-white tracking-tight leading-tight">
          {{ locale === 'vi' ? 'Trải Nghiệm Điện Ảnh Dễ Dàng Cùng CineBook' : 'Effortless Cinema Experience with CineBook' }}
        </h1>
        <p class="text-sm sm:text-base text-slate-400 leading-relaxed">
          {{ locale === 'vi'
            ? 'CineBook mang đến giải pháp đặt vé xem phim và combo bắp nước trực tuyến nhanh chóng, trực quan và tiện lợi, giúp bạn thưởng thức trọn vẹn những thước phim đỉnh cao cùng người thân và bạn bè.'
            : 'CineBook delivers an intuitive, fast, and seamless online movie ticketing and concession booking experience, connecting moviegoers with the magic of cinema.'
          }}
        </p>
        <div class="pt-2 flex justify-center gap-3">
          <router-link
            to="/movies"
            class="px-5 py-2.5 rounded-xl bg-indigo-600 hover:bg-indigo-500 text-white text-sm font-semibold shadow-lg transition-colors"
          >
            {{ locale === 'vi' ? 'Khám phá phim ngay' : 'Browse Movies' }}
          </router-link>
          <router-link
            to="/showtimes"
            class="px-5 py-2.5 rounded-xl bg-slate-900 hover:bg-slate-800 border border-slate-700 text-slate-300 text-sm font-semibold transition-colors"
          >
            {{ locale === 'vi' ? 'Xem lịch chiếu' : 'Check Showtimes' }}
          </router-link>
        </div>
      </div>

      <!-- Section: Về CineBook & Sứ Mệnh -->
      <div class="rounded-3xl bg-slate-900/80 border border-slate-800 p-6 sm:p-10 space-y-4 shadow-xl">
        <h2 class="text-xl sm:text-2xl font-bold text-white flex items-center gap-2.5">
          <span>🍿</span>
          {{ locale === 'vi' ? 'Về Nền Tảng CineBook' : 'About CineBook' }}
        </h2>
        <p class="text-slate-300 text-sm sm:text-base leading-relaxed">
          {{ locale === 'vi'
            ? 'Được thiết kế nhằm tối ưu hóa từng bước trong hành trình của khán giả mê điện ảnh, CineBook tập trung vào trải nghiệm người dùng mượt mà, thông tin lịch chiếu minh bạch theo từng cụm rạp, sơ đồ phòng chiếu trực quan và cơ chế thanh toán vé điện tử không cần tiếp xúc.'
            : 'Crafted to enhance every touchpoint of the moviegoer journey, CineBook focuses on seamless user experience, transparent cinema schedules, visual seat maps, and contactless digital ticketing.'
          }}
        </p>
      </div>

      <!-- Section: 5 Bước Đặt Vé Thuận Tiện -->
      <div class="space-y-6">
        <div class="text-center space-y-2">
          <h2 class="text-2xl sm:text-3xl font-black text-white tracking-tight">
            {{ locale === 'vi' ? 'Hành Trình Đặt Vé Thuận Tiện' : 'Your 5-Step Seamless Journey' }}
          </h2>
          <p class="text-xs sm:text-sm text-slate-400">
            {{ locale === 'vi' ? 'Từ lúc chọn phim đến khi ngồi vào phòng chiếu chỉ với vài thao tác đơn giản' : 'From browsing releases to settling into your seat in five easy steps' }}
          </p>
        </div>

        <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-4">
          <!-- Step 1 -->
          <div class="p-5 rounded-2xl bg-slate-900/80 border border-slate-800 space-y-2.5 relative">
            <span class="w-8 h-8 rounded-full bg-indigo-600/20 border border-indigo-500/40 text-indigo-400 font-bold text-xs flex items-center justify-center">1</span>
            <h3 class="font-bold text-sm text-white">
              {{ locale === 'vi' ? 'Khám phá phim' : 'Discover Movies' }}
            </h3>
            <p class="text-xs text-slate-400 leading-relaxed">
              {{ locale === 'vi' ? 'Xem danh sách phim đang chiếu, sắp chiếu, trailer và thông tin thể loại.' : 'Explore now-showing & coming-soon titles with trailers and genre details.' }}
            </p>
          </div>

          <!-- Step 2 -->
          <div class="p-5 rounded-2xl bg-slate-900/80 border border-slate-800 space-y-2.5 relative">
            <span class="w-8 h-8 rounded-full bg-indigo-600/20 border border-indigo-500/40 text-indigo-400 font-bold text-xs flex items-center justify-center">2</span>
            <h3 class="font-bold text-sm text-white">
              {{ locale === 'vi' ? 'Chọn suất chiếu' : 'Pick Showtimes' }}
            </h3>
            <p class="text-xs text-slate-400 leading-relaxed">
              {{ locale === 'vi' ? 'Tìm cụm rạp gần bạn nhất và lựa chọn khung giờ xem phim thuận tiện.' : 'Filter by cinema venue, date, and ideal screening times.' }}
            </p>
          </div>

          <!-- Step 3 -->
          <div class="p-5 rounded-2xl bg-slate-900/80 border border-slate-800 space-y-2.5 relative">
            <span class="w-8 h-8 rounded-full bg-indigo-600/20 border border-indigo-500/40 text-indigo-400 font-bold text-xs flex items-center justify-center">3</span>
            <h3 class="font-bold text-sm text-white">
              {{ locale === 'vi' ? 'Chọn ghế & Combo' : 'Seats & Concessions' }}
            </h3>
            <p class="text-xs text-slate-400 leading-relaxed">
              {{ locale === 'vi' ? 'Tự do chọn ghế Thường, VIP, Ghế đôi và chọn bắp nước kèm ưu đãi.' : 'Select Standard, VIP, or Couple seats plus snack combos.' }}
            </p>
          </div>

          <!-- Step 4 -->
          <div class="p-5 rounded-2xl bg-slate-900/80 border border-slate-800 space-y-2.5 relative">
            <span class="w-8 h-8 rounded-full bg-indigo-600/20 border border-indigo-500/40 text-indigo-400 font-bold text-xs flex items-center justify-center">4</span>
            <h3 class="font-bold text-sm text-white">
              {{ locale === 'vi' ? 'Thanh toán trực tuyến' : 'Online Payment' }}
            </h3>
            <p class="text-xs text-slate-400 leading-relaxed">
              {{ locale === 'vi' ? 'Thanh toán bảo mật nhanh chóng qua quét mã QR hoặc thẻ ngân hàng.' : 'Fast, secure checkout via QR banking or card payment.' }}
            </p>
          </div>

          <!-- Step 5 -->
          <div class="p-5 rounded-2xl bg-slate-900/80 border border-slate-800 space-y-2.5 relative">
            <span class="w-8 h-8 rounded-full bg-indigo-600/20 border border-indigo-500/40 text-indigo-400 font-bold text-xs flex items-center justify-center">5</span>
            <h3 class="font-bold text-sm text-white">
              {{ locale === 'vi' ? 'Nhận vé điện tử' : 'Get E-Ticket' }}
            </h3>
            <p class="text-xs text-slate-400 leading-relaxed">
              {{ locale === 'vi' ? 'Mã QR soát vé hiển thị ngay lập tức, sẵn sàng vào phòng chiếu.' : 'Instant unified QR pass ready for swift cinema gate check-in.' }}
            </p>
          </div>
        </div>
      </div>

      <!-- Section: Điểm Nổi Bật Của Dịch Vụ -->
      <div class="grid grid-cols-1 md:grid-cols-3 gap-6">
        <div class="p-6 rounded-3xl bg-slate-900/80 border border-slate-800 space-y-3 shadow-lg">
          <div class="w-12 h-12 rounded-2xl bg-indigo-600/20 border border-indigo-500/30 text-indigo-400 flex items-center justify-center text-2xl">
            ⚡
          </div>
          <h3 class="font-bold text-base text-white">
            {{ locale === 'vi' ? 'Giữ Chỗ 5 Phút Tức Thì' : '5-Minute Real-Time Seat Hold' }}
          </h3>
          <p class="text-xs text-slate-400 leading-relaxed">
            {{ locale === 'vi'
              ? 'Ghế của bạn được bảo lưu tuyệt đối trong 5 phút để bạn thong thả hoàn tất các bước thanh toán mà không lo bị người khác mua mất.'
              : 'Your chosen seats are reserved exclusively for 5 minutes, allowing you to complete checkout comfortably without contention.'
            }}
          </p>
        </div>

        <div class="p-6 rounded-3xl bg-slate-900/80 border border-slate-800 space-y-3 shadow-lg">
          <div class="w-12 h-12 rounded-2xl bg-emerald-600/20 border border-emerald-500/30 text-emerald-400 flex items-center justify-center text-2xl">
            🎟️
          </div>
          <h3 class="font-bold text-base text-white">
            {{ locale === 'vi' ? 'Vé Điện Tử & Mã QR Duy Nhất' : 'Unified QR E-Tickets' }}
          </h3>
          <p class="text-xs text-slate-400 leading-relaxed">
            {{ locale === 'vi'
              ? 'Một mã QR duy nhất cho toàn bộ nhóm bạn. Không cần in vé giấy, không lo thất lạc, chỉ cần xuất trình trên điện thoại tại cửa rạp.'
              : 'A single consolidated QR pass covers all seats in your order. Eco-friendly, paperless, and instantly verified at the gate.'
            }}
          </p>
        </div>

        <div class="p-6 rounded-3xl bg-slate-900/80 border border-slate-800 space-y-3 shadow-lg">
          <div class="w-12 h-12 rounded-2xl bg-amber-600/20 border border-amber-500/30 text-amber-400 flex items-center justify-center text-2xl">
            🛡️
          </div>
          <h3 class="font-bold text-base text-white">
            {{ locale === 'vi' ? 'Chính Sách Hoàn Tiền Rõ Ràng' : 'Transparent Refund Policy' }}
          </h3>
          <p class="text-xs text-slate-400 leading-relaxed">
            {{ locale === 'vi'
              ? 'Hỗ trợ hủy và hoàn vé trực tuyến trước giờ chiếu ít nhất 2 giờ cho các vé chưa sử dụng, bảo đảm quyền lợi tối đa cho khán giả.'
              : 'Automated online refunds available up to 2 hours before showtime for unused tickets, ensuring peace of mind.'
            }}
          </p>
        </div>
      </div>

      <!-- Section: Hỗ Trợ & Trợ Giúp -->
      <div class="rounded-3xl bg-slate-900/80 border border-slate-800 p-6 sm:p-10 space-y-4 text-center max-w-2xl mx-auto shadow-xl">
        <h2 class="text-xl sm:text-2xl font-bold text-white">
          {{ locale === 'vi' ? 'Bạn Cần Thêm Thông Tin?' : 'Need Additional Assistance?' }}
        </h2>
        <p class="text-slate-400 text-xs sm:text-sm leading-relaxed">
          {{ locale === 'vi'
            ? 'Đội ngũ CineBook luôn sẵn sàng giải đáp mọi thắc mắc của bạn về lịch chiếu, vé điện tử và phương thức thanh toán.'
            : 'Our customer care team is always here to assist you with showtimes, electronic passes, and ticketing inquiries.'
          }}
        </p>
        <div class="pt-2 flex justify-center gap-3">
          <router-link
            to="/faq"
            class="px-4 py-2 rounded-xl bg-slate-800 hover:bg-slate-700 text-white text-xs sm:text-sm font-semibold border border-slate-700 transition-colors"
          >
            {{ locale === 'vi' ? 'Xem câu hỏi thường gặp (FAQ)' : 'Visit FAQ' }}
          </router-link>
          <router-link
            to="/refund-policy"
            class="px-4 py-2 rounded-xl bg-slate-800 hover:bg-slate-700 text-white text-xs sm:text-sm font-semibold border border-slate-700 transition-colors"
          >
            {{ locale === 'vi' ? 'Chính sách hoàn vé' : 'Refund Policy' }}
          </router-link>
        </div>
      </div>
    </div>
  </div>
</template>
