import { createRouter, createWebHistory, RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

// Layouts
import DefaultLayout from '@/layouts/DefaultLayout.vue'
import AdminLayout from '@/layouts/AdminLayout.vue'
import AuthLayout from '@/layouts/AuthLayout.vue'

// Customer Views
import HomeView from '@/views/customer/HomeView.vue'
import MoviesView from '@/views/customer/MoviesView.vue'
import MovieDetailView from '@/views/customer/MovieDetailView.vue'
import ShowtimesView from '@/views/customer/ShowtimesView.vue'
import BookingView from '@/views/customer/BookingView.vue'
import PaymentResultView from '@/views/customer/PaymentResultView.vue'
import DemoPaymentView from '@/views/customer/DemoPaymentView.vue'
import MyBookingsView from '@/views/customer/MyBookingsView.vue'
import ProfileView from '@/views/customer/ProfileView.vue'
import CinemasView from '@/views/customer/CinemasView.vue'
import PromotionsView from '@/views/customer/PromotionsView.vue'

// Static Policy Views
import TermsOfUseView from '@/views/static/TermsOfUseView.vue'
import PrivacyPolicyView from '@/views/static/PrivacyPolicyView.vue'
import RefundPolicyView from '@/views/static/RefundPolicyView.vue'
import FaqView from '@/views/static/FaqView.vue'
import AboutUsView from '@/views/static/AboutUsView.vue'

// Auth Views
import LoginView from '@/views/auth/LoginView.vue'
import RegisterView from '@/views/auth/RegisterView.vue'
import VerifyEmailView from '@/views/auth/VerifyEmailView.vue'

// Admin Views
import AdminDashboardView from '@/views/admin/AdminDashboardView.vue'
import AdminMoviesView from '@/views/admin/AdminMoviesView.vue'
import AdminGenresView from '@/views/admin/AdminGenresView.vue'
import AdminShowtimesView from '@/views/admin/AdminShowtimesView.vue'
import AdminCinemasView from '@/views/admin/AdminCinemasView.vue'
import AdminBookingsView from '@/views/admin/AdminBookingsView.vue'
import AdminPromotionsView from '@/views/admin/AdminPromotionsView.vue'
import AdminRefundsView from '@/views/admin/AdminRefundsView.vue'
import AdminPricingView from '@/views/admin/AdminPricingView.vue'
import AdminReportsView from '@/views/admin/AdminReportsView.vue'
import AdminUsersView from '@/views/admin/AdminUsersView.vue'
import AdminTicketsView from '@/views/admin/AdminTicketsView.vue'
import AdminFoodsView from '@/views/admin/AdminFoodsView.vue'

// Error Views
import NotFoundView from '@/views/NotFoundView.vue'
import ForbiddenView from '@/views/ForbiddenView.vue'

const routes: Array<RouteRecordRaw> = [
  // Customer Routes
  {
    path: '/',
    component: DefaultLayout,
    children: [
      {
        path: '',
        name: 'home',
        component: HomeView,
        meta: { title: 'Trang chủ' },
      },
      {
        path: 'movies',
        name: 'movies',
        component: MoviesView,
        meta: { title: 'Danh sách phim' },
      },
      {
        path: 'movies/:id',
        name: 'movie-detail',
        component: MovieDetailView,
        meta: { title: 'Chi tiết phim' },
      },
      {
        path: 'showtimes',
        name: 'showtimes',
        component: ShowtimesView,
        meta: { title: 'Lịch chiếu phim' },
      },
      {
        path: 'cinemas',
        name: 'cinemas',
        component: CinemasView,
      },
      {
        path: 'promotions',
        name: 'promotions',
        component: PromotionsView,
        meta: { title: 'Tin khuyến mãi' },
      },
      {
        path: 'booking',
        name: 'booking',
        component: BookingView,
        meta: { title: 'Đặt vé xem phim', requiresAuth: true },
      },
      {
        path: 'payment/demo',
        name: 'payment-demo',
        component: DemoPaymentView,
        meta: { title: 'Mô phỏng thanh toán VNPay', requiresAuth: true },
      },
      {
        path: 'payment/result',
        name: 'payment-result',
        component: PaymentResultView,
        meta: { title: 'Kết quả thanh toán' },
      },
      {
        path: 'profile',
        name: 'profile',
        component: ProfileView,
        meta: { title: 'Hồ sơ cá nhân', requiresAuth: true },
      },
      {
        path: 'my-bookings',
        name: 'my-bookings',
        component: MyBookingsView,
        meta: { title: 'Vé đã mua', requiresAuth: true },
      },
      // Static Policy Routes
      {
        path: 'terms',
        name: 'terms-of-use',
        component: TermsOfUseView,
        meta: { title: 'Điều khoản sử dụng' },
      },
      {
        path: 'privacy',
        name: 'privacy-policy',
        component: PrivacyPolicyView,
        meta: { title: 'Chính sách bảo mật' },
      },
      {
        path: 'refund',
        name: 'refund-policy',
        component: RefundPolicyView,
        meta: { title: 'Chính sách hoàn tiền' },
      },
      {
        path: 'faq',
        name: 'faq',
        component: FaqView,
        meta: { title: 'Câu hỏi thường gặp' },
      },
      {
        path: 'about',
        name: 'about-us',
        component: AboutUsView,
        meta: { title: 'Về chúng tôi' },
      },
    ],
  },

  // Auth Routes
  {
    path: '/',
    component: AuthLayout,
    children: [
      {
        path: 'login',
        name: 'login',
        component: LoginView,
        meta: { title: 'Đăng nhập', guestOnly: true },
      },
      {
        path: 'register',
        name: 'register',
        component: RegisterView,
        meta: { title: 'Đăng ký', guestOnly: true },
      },
      {
        path: 'verify-email',
        name: 'verify-email',
        component: VerifyEmailView,
        meta: { title: 'Xác thực Email' },
      },
    ],
  },

  // Admin Routes (Strictly requires ADMIN)
  {
    path: '/admin',
    component: AdminLayout,
    meta: { requiresAdmin: true },
    children: [
      {
        path: '',
        redirect: '/admin/dashboard',
      },
      {
        path: 'dashboard',
        name: 'admin-dashboard',
        component: AdminDashboardView,
        meta: { title: 'Bảng điều khiển', requiresAdmin: true },
      },
      {
        path: 'movies',
        name: 'admin-movies',
        component: AdminMoviesView,
        meta: { title: 'Quản lý Phim', requiresAdmin: true },
      },
      {
        path: 'genres',
        name: 'admin-genres',
        component: AdminGenresView,
        meta: { title: 'Quản lý Thể loại', requiresAdmin: true },
      },
      {
        path: 'foods',
        name: 'admin-foods',
        component: AdminFoodsView,
        meta: { title: 'Quản lý Bắp nước & Combo', requiresAdmin: true },
      },
      {
        path: 'showtimes',
        name: 'admin-showtimes',
        component: AdminShowtimesView,
        meta: { title: 'Quản lý Lịch chiếu', requiresAdmin: true },
      },
      {
        path: 'cinemas',
        name: 'admin-cinemas',
        component: AdminCinemasView,
        meta: { title: 'Quản lý Cụm rạp & Phòng', requiresAdmin: true },
      },
      {
        path: 'bookings',
        name: 'admin-bookings',
        component: AdminBookingsView,
        meta: { title: 'Quản lý Đặt vé & Vé', requiresAdmin: true },
      },
      {
        path: 'promotions',
        name: 'admin-promotions',
        component: AdminPromotionsView,
        meta: { title: 'Quản lý Khuyến mãi', requiresAdmin: true },
      },
      {
        path: 'refunds',
        name: 'admin-refunds',
        component: AdminRefundsView,
        meta: { title: 'Quản lý Hoàn tiền & Giao dịch', requiresAdmin: true },
      },
      {
        path: 'pricing',
        name: 'admin-pricing',
        component: AdminPricingView,
        meta: { title: 'Quản lý Bảng giá & Loại ghế', requiresAdmin: true },
      },
      {
        path: 'tickets',
        name: 'admin-tickets',
        component: AdminTicketsView,
        meta: { title: 'Soát vé (Box Office)', requiresAdmin: true },
      },
      {
        path: 'reports',
        name: 'admin-reports',
        component: AdminReportsView,
        meta: { title: 'Báo cáo & Thống kê', requiresAdmin: true },
      },
      {
        path: 'users',
        name: 'admin-users',
        component: AdminUsersView,
        meta: { title: 'Quản lý Người dùng', requiresAdmin: true },
      },
    ],
  },

  // Error Routes
  {
    path: '/403',
    name: 'forbidden',
    component: ForbiddenView,
    meta: { title: '403 - Không có quyền truy cập' },
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: NotFoundView,
    meta: { title: '404 - Không tìm thấy trang' },
  },
]

/**
 * Cấu hình khởi tạo Vue Router với chế độ HTML5 History (createWebHistory).
 * Tự động cuộn lên đầu trang (scrollBehavior) khi chuyển trang.
 */
export const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior(_to, _from, savedPosition) {
    if (savedPosition) {
      return savedPosition
    }
    return { top: 0 }
  },
})

/**
 * Bộ điều hướng bảo vệ tuyến đường toàn cục (Global Navigation Guard - beforeEach).
 * 
 * Luồng kiểm tra bảo mật:
 * 1. Khôi phục phiên: Nếu store chưa khởi tạo và có token trong localStorage, tự động gọi restoreSession().
 * 2. Cập nhật Title: Đổi tiêu đề tab trình duyệt theo thuộc tính meta.title của route.
 * 3. Kiểm tra quyền Quản trị (requiresAdmin):
 *    - Chưa đăng nhập: Chuyển hướng tới trang Login kèm query ?redirect=<url> để đăng nhập xong quay lại.
 *    - Đã đăng nhập nhưng không phải Admin: Chuyển hướng tới trang lỗi 403 Forbidden.
 * 4. Kiểm tra đăng nhập bắt buộc (requiresAuth):
 *    - Áp dụng cho các tính năng: Đặt vé (Booking), Xem vé cá nhân (My Bookings), Hồ sơ cá nhân (Profile).
 * 5. Chặn truy cập lại trang Auth (guestOnly):
 *    - Nếu đã đăng nhập mà cố vào lại trang /login hoặc /register thì tự động chuyển hướng về trang chủ hoặc dashboard.
 */
router.beforeEach(async (to, _from, next) => {
  const authStore = useAuthStore()

  // 1. Tự động phục hồi phiên làm việc khi tải lại trang nếu còn lưu token
  if (!authStore.isInitialized && authStore.accessToken) {
    await authStore.restoreSession()
  }

  // 2. Cập nhật tiêu đề trang hiển thị trên tab trình duyệt
  if (to.meta.title) {
    document.title = `${to.meta.title} — CineBook`
  }

  // 3. Bảo vệ các tuyến đường của Quản trị viên (Admin Routes)
  if (to.matched.some((record) => record.meta.requiresAdmin)) {
    if (!authStore.isAuthenticated) {
      return next({ name: 'login', query: { redirect: to.fullPath } })
    }
    if (!authStore.isAdmin) {
      return next({ name: 'forbidden' })
    }
  }

  // 4. Bảo vệ các tuyến đường yêu cầu khách hàng phải đăng nhập (Customer Routes)
  if (to.matched.some((record) => record.meta.requiresAuth)) {
    if (!authStore.isAuthenticated) {
      return next({ name: 'login', query: { redirect: to.fullPath } })
    }
  }

  // 5. Tuyến đường chỉ dành cho khách vãng lai chưa đăng nhập (Login/Register)
  if (to.matched.some((record) => record.meta.guestOnly)) {
    if (authStore.isAuthenticated) {
      return next(authStore.isAdmin ? { name: 'admin-dashboard' } : { name: 'home' })
    }
  }

  // Cho phép chuyển tiếp tới trang đích
  next()
})

export default router
