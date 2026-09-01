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
import MyBookingsView from '@/views/customer/MyBookingsView.vue'
import ProfileView from '@/views/customer/ProfileView.vue'
import CinemasView from '@/views/customer/CinemasView.vue'
import PromotionsView from '@/views/customer/PromotionsView.vue'

// Auth Views
import LoginView from '@/views/auth/LoginView.vue'
import RegisterView from '@/views/auth/RegisterView.vue'

// Admin Views
import AdminDashboardView from '@/views/admin/AdminDashboardView.vue'
import AdminMoviesView from '@/views/admin/AdminMoviesView.vue'
import AdminShowtimesView from '@/views/admin/AdminShowtimesView.vue'
import AdminCinemasView from '@/views/admin/AdminCinemasView.vue'
import AdminBookingsView from '@/views/admin/AdminBookingsView.vue'
import AdminPromotionsView from '@/views/admin/AdminPromotionsView.vue'
import AdminRefundsView from '@/views/admin/AdminRefundsView.vue'
import AdminPricingView from '@/views/admin/AdminPricingView.vue'
import AdminReportsView from '@/views/admin/AdminReportsView.vue'
import AdminUsersView from '@/views/admin/AdminUsersView.vue'
import AdminTicketsView from '@/views/admin/AdminTicketsView.vue'

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

// Route Guards for RBAC & Auth
router.beforeEach(async (to, _from, next) => {
  const authStore = useAuthStore()

  // Restore session on initial load if token exists
  if (!authStore.isInitialized && authStore.accessToken) {
    await authStore.restoreSession()
  }

  // Update Page Title
  if (to.meta.title) {
    document.title = `${to.meta.title} — CineBook`
  }

  // Requires Admin
  if (to.matched.some((record) => record.meta.requiresAdmin)) {
    if (!authStore.isAuthenticated) {
      return next({ name: 'login', query: { redirect: to.fullPath } })
    }
    if (!authStore.isAdmin) {
      return next({ name: 'forbidden' })
    }
  }

  // Requires Customer Auth
  if (to.matched.some((record) => record.meta.requiresAuth)) {
    if (!authStore.isAuthenticated) {
      return next({ name: 'login', query: { redirect: to.fullPath } })
    }
  }

  // Guest Only (e.g. Login / Register)
  if (to.matched.some((record) => record.meta.guestOnly)) {
    if (authStore.isAuthenticated) {
      return next(authStore.isAdmin ? { name: 'admin-dashboard' } : { name: 'home' })
    }
  }

  next()
})

export default router
