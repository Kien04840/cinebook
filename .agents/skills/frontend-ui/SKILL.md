---
name: frontend-ui
description: Comprehensive guidelines, architectural standards, and workflow procedures for implementing, styling, and verifying Vue 3 / Tailwind CSS Frontend UI in CineBook.
---

# Frontend UI Skill — CineBook

This skill defines the standardized architecture, component patterns, design system conventions, API integration rules, and verification procedures for building and modifying the frontend of CineBook.

---

## 1. When to Use This Skill

Activate and follow this skill whenever performing any frontend task, including:
- Creating new Vue views, pages, or routes.
- Modifying or refining existing UI screens.
- Designing and implementing reusable UI components (buttons, modals, tables, forms, cards).
- Implementing customer-facing user journeys (movie discovery, showtime selection, seat map selection, booking checkout, payment redirect, ticket display, profile, promotion preview).
- Implementing administrator management pages (movie catalog, cinema/auditorium management, showtime scheduling, booking administration, promotion management, TMDB import, analytics dashboard & reporting).
- Connecting frontend components to the CineBook REST API (`docs/api.md`).
- Reviewing, refactoring, or optimizing frontend components.
- Aligning and styling UI against design references, wireframes, screenshots, or Penpot designs.

---

## 2. Hierarchy of Truth & Conflict Resolution

When decisions, wireframes, or documentation conflict, adhere strictly to this precedence order:
1. **Explicit developer instruction** for the current task.
2. `AGENTS.md` (Project core principles & locked decisions).
3. `.agents/rules/frontend.md` (Mandatory frontend rules).
4. `docs/business-rules.md` (Domain invariants & business logic).
5. `docs/api.md` (Authoritative backend REST API contract).
6. Current implemented backend & frontend behavior.
7. Existing wireframe / design references (`docs/ui/screens/`).
8. `docs/ui/design-system.md` (Design system & visual conventions).
9. General Vue 3 / Tailwind CSS / Modern Web best practices.

### Current Functionality Precedence:
> **Current project functionality takes precedence over obsolete details in the original wireframes.**

When resolving discrepancies, preserve as much of the original screen's recognizable layout and information hierarchy as reasonably possible. If significant UI adaptations are made compared to the wireframe due to API requirements, business rules, or newly implemented features, briefly explain the rationale in the implementation report.

---

## 3. The 70/30 Design Principle

Thesis report wireframes and screenshots in `docs/ui/screens/` represent the initial design baseline developed during the graduation thesis phase. They are **living design references**, not immutable or pixel-perfect contracts.

> **Core Philosophy: 70% Preserve the established design direction + 30% Professional UI/UX judgment.**
> 
> **Motto: "Preserve the structure, improve the experience."**

### 3.1. Approximately 70% — Preserve the Established Design Direction
The implementation must maintain the recognizable foundation of the original screen:
- **Overall layout structure**: Page shell, container bounds, header/sidebar/content relationships.
- **Major content regions**: Grid sections, summary sidebars, filter toolbars, table placements.
- **Information hierarchy**: Primary headings, secondary metadata, content grouping, visual weight.
- **Primary navigation structure**: Main menu placement and conceptual route pathways.
- **Main user journey**: Core conversion flows (e.g. Movie $\rightarrow$ Showtime $\rightarrow$ Seat Selection $\rightarrow$ Voucher $\rightarrow$ Payment $\rightarrow$ Ticket).
- **Major functional blocks**: Essential interactive zones (e.g. movie details, seat matrix, countdown banner, checkout summary).
- **Relative conceptual placement of important elements**: Positioning of key call-to-action buttons and cards.
- **Overall visual composition**: General density, balance, and aesthetic feel of the screen.

### 3.2. Approximately 30% — Professional UI/UX Judgment
The AI agent and developer are explicitly authorized to use professional judgment to improve:
- **Usability & Information Clarity**: Clearer typographic hierarchy, readable formatting (VND currency, local date-time).
- **Responsive Behavior**: Fluid scaling across mobile, tablet, and desktop viewports.
- **Accessibility (a11y)**: Focus visible rings, keyboard navigation, semantic ARIA labels, minimum touch targets.
- **Complete UI States**: Loading skeletons, spinners, empty placeholders, error banners, and disabled button states.
- **Form UX & Validation Feedback**: Real-time inline field validation, error text association, input helper hints.
- **Navigation Completeness**: Naturally integrating newly implemented backend modules.
- **Search, Filter & Sort Controls**: Adding compact controls when supported by the backend API.
- **Feedback & Confirmation Flows**: Toast notifications, confirmation dialogs for destructive actions, retry triggers.
- **Micro-interactions & Visual Polish**: Smooth transitions, backdrop blur on modals, hover highlights.
- **Compatibility with Current Backend/API**: Aligning field names, status badges, and data schemas with `docs/api.md`.

*The 70/30 ratio is a design guideline, not a literal percentage of pixels or code lines. The final screen must remain recognizably derived from the original design while being a complete, polished, and production-ready product.*

---

## 4. Controlled Creativity ("AI Should Think, Not Just Copy")

> **CineBook frontend implementation is not a screenshot-copying task.** The AI must understand what the screen is for, who uses it, what information matters, what actions are needed, what the current API supports, and what the original wireframe intended.

Proactive, context-aware reasoning is considered **desirable behavior**. Examples of expected AI reasoning:

* *"The original wireframe does not contain a filter, but the API supports filtering by status. A compact filter bar would improve usability without changing the page structure."*
* *"The original sidebar predates Promotion Management. I recommend adding it to the Catalog group."*
* *"The wireframe does not specify an empty state. I will add one following the Design System."*
* *"The current backend does not support drag-and-drop showtime movement, so I will keep the existing click/form interaction and treat drag-and-drop as a future enhancement."*

### 4.1. Acceptable Autonomous Improvements
- Adding search, filter, and sort controls supported by the API.
- Adding pagination to large administrative tables.
- Adding missing navigation items for newly implemented modules.
- Adding breadcrumbs on deep admin management views.
- Adding confirmation dialogs for destructive actions (e.g. cancelling a booking, deleting a showtime).
- Adding loading skeletons and spinners during asynchronous network requests.
- Adding empty states and error alerts with "Thử lại" (Retry) triggers.
- Adding responsive navigation (collapsible mobile sheets, horizontally scrollable seat maps).
- Adding useful status badges (`PAID`, `PENDING_PAYMENT`, `CANCELLED`).
- Improving form validation feedback and helper hints.
- Improving mobile checkout flow with sticky summary bars.
- Improving spacing and typography while preserving the original composition.

### 4.2. What Counts as a Major Redesign (Requires Stronger Justification)
- **Allowed**: *Improve the existing screen.*
- **Not allowed by default**: *Redesign the existing screen.*

The AI must prefer the smallest useful change that solves the problem. If two designs solve the same problem, **prefer the one that preserves more of the existing wireframe structure**.

Do NOT silently perform major redesigns such as:
- Completely replacing the page layout.
- Replacing a sidebar with a fundamentally different navigation model (e.g. top mega-menu or floating pill).
- Turning a single-screen checkout into an unrelated multi-step wizard.
- Moving major information groups to disconnected locations.
- Removing essential functional sections present in the wireframe.
- Changing the fundamental booking flow.
- Introducing an entirely different visual identity.

*If a major redesign appears genuinely valuable, explain the proposal and rationale before implementing it.*

---

## 5. Evolving Navigation Example

The original thesis Dashboard wireframe may have contained only 6 items:
- Dashboard
- Movie Management
- Showtime Management
- Cinema Management
- Pricing Management
- User Management

When the current CineBook product includes additional modules (Promotion Management, Booking Management, Reporting, TMDB Import), the AI should integrate them naturally into the existing sidebar hierarchy rather than artificially restricting the sidebar:

```text
Overview
  Dashboard
  Reports & Analytics

Operations
  Showtimes
  Cinemas & Auditoriums
  Bookings & Tickets

Catalog
  Movies
  Genres
  Promotions
  TMDB Sync

System
  Users
```

*This is an example of **natural evolution**, not permission to redesign the entire admin shell.*

---

## 6. Separate UI Improvement from Backend Feature Invention

The AI is encouraged to improve the UI, but it must **never invent unsupported backend functionality**.

Before implementing feature-dependent UI:
1. Check `docs/api.md`.
2. Check `docs/business-rules.md`.
3. Inspect existing backend endpoints if necessary.
4. Confirm the required data is actually available.

Strictly classify UI ideas into 3 tiers:

| Tier | Definition | Action |
|---|---|---|
| **Supported** | Functionality backed by existing endpoints and DTOs in `docs/api.md`. | **Implement directly.** |
| **UI-only Improvement** | Visual, styling, or client-side UX improvements requiring no backend change (animations, countdowns, tooltips, responsive collapses). | **Implement directly.** |
| **Proposed Future Feature** | Useful product ideas requiring new backend endpoints, DB columns, or rule changes. | **Do NOT silently implement fake frontend behavior. Document as a suggestion in the task report.** |

*Example: "Drag-and-drop showtime scheduling would improve UX, but the current API does not provide the required batch repositioning endpoint. Treat this as a future enhancement rather than implementing fake frontend behavior."*

---

## 7. Lightweight Decision Framework

When considering any creative UI improvement, ask these 7 questions:

1. **Problem**: Does it solve a real usability problem?
2. **Context**: Does it fit naturally within the current screen?
3. **Hierarchy**: Does it preserve the original information hierarchy?
4. **Feasibility**: Is it supported by current API endpoints and business rules?
5. **Value**: Does it add meaningful value for the user?
6. **Simplicity**: Is it reasonably simple and maintainable to implement?
7. **Proportionality**: Would a simpler change solve the same problem just as well?

*Always prefer the smallest useful improvement that delivers the desired outcome.*

---

## 8. Living Design References (Terminology Guidance)

To avoid miscommunication, use accurate terminology:

| Preferred Terminology | Terminology to AVOID |
|---|---|
| **Design Reference** | ~~Immutable UI specification~~ |
| **Visual Reference** | ~~Pixel-perfect specification~~ |
| **Original Wireframe** | ~~Mandatory exact layout~~ |
| **Thesis Design Reference** | ~~Frozen UI contract~~ |

*The screenshots in `docs/ui/screens/` guide implementation without preventing reasonable product evolution.*

---

## 9. Locked Frontend Technology Stack

The CineBook frontend stack is strictly locked:

| Layer / Concern | Technology | Note |
|---|---|---|
| **Framework** | Vue 3 | Composition API with `<script setup>` syntax only |
| **Language** | TypeScript | Strict typing for DTOs, props, emits, and API responses |
| **Build Tool** | Vite | Fast HMR and optimized production bundling |
| **Routing** | Vue Router 4 | Route meta for RBAC, navigation guards, lazy-loading views |
| **State Management** | Pinia | Modular stores for shared cross-component state |
| **HTTP Client** | Axios | Centralized client with JWT request/response interceptors |
| **Styling Engine** | Tailwind CSS | Utility-first styling with consistent design tokens |
| **Icons** | Project-installed icon set | Use installed icon library if present; avoid adding dependencies purely for preference |

### Icon Policy:
- Utilize the icon library already installed in the project if one is present.
- If the project does not yet include an icon library, do not automatically install external dependencies based solely on preference.
- Propose or install an icon library only when technically necessary. When a specific library is officially adopted, update the Skill and Design System accordingly.
- Do not introduce alternative UI frameworks (e.g. Vuetify, Element Plus, Ant Design, Bootstrap) or unapproved state management libraries.

---

## 10. UI Architecture & Directory Structure

Organize the `frontend/` codebase following classic modular, feature-oriented Layered Frontend Architecture:

```text
frontend/src/
├── assets/             # Static images, logos, global CSS/Tailwind imports
├── components/
│   ├── common/         # Atomic & reusable UI components (Button, Modal, Input, Badge, Table, Spinner, Toast)
│   ├── layout/         # Shell components (AppHeader, AppFooter, AdminSidebar, Navbar, UserMenu)
│   └── domain/         # Domain-specific composite components
│       ├── movie/      # MovieCard, MoviePoster, GenreTag, MovieTrailerModal
│       ├── showtime/   # ShowtimePicker, DateSelector, ShowtimeSlot
│       ├── seat/       # SeatGrid, SeatItem, SeatLegend, SeatSummary
│       ├── booking/    # BookingSummaryCard, CountdownTimer, VoucherInput
│       ├── payment/    # PaymentMethodSelector, PaymentStatusBanner
│       └── report/     # KpiCard, RevenueChart, OccupancyBar, ExportButton
├── composables/        # Reusable stateful logic (useAuth, useSeatSelection, useCountdown, useToast, useDebounce)
├── layouts/            # Page layout wrappers (DefaultLayout.vue, AdminLayout.vue, AuthLayout.vue)
├── router/             # Vue Router configuration, route definitions, navigation guards
├── services/           # REST API client & service functions mapped 1-to-1 to docs/api.md
│   ├── api.ts          # Central Axios instance with auth interceptors
│   ├── auth.service.ts
│   ├── movie.service.ts
│   ├── cinema.service.ts
│   ├── showtime.service.ts
│   ├── booking.service.ts
│   ├── payment.service.ts
│   ├── promotion.service.ts
│   └── report.service.ts
├── stores/             # Pinia stores for shared global state (used when state spans routes/components)
│   ├── auth.ts         # User profile, JWT tokens, login/logout actions, permissions
│   ├── booking.ts      # Active seat reservation, hold countdown timer, selected showtime
│   └── ui.ts           # Global alerts, toasts, theme toggles, modal states
├── types/              # TypeScript interface & enum definitions mapped to backend DTOs
│   ├── auth.types.ts
│   ├── movie.types.ts
│   ├── booking.types.ts
│   ├── payment.types.ts
│   └── report.types.ts
├── utils/              # Pure utility functions (formatters for currency VND, date-time, error parsing)
└── views/              # Page-level route views
    ├── auth/           # LoginView, RegisterView, ForgotPasswordView
    ├── customer/       # HomeView, MovieDetailView, BookingView, PaymentResultView, UserProfileView
    └── admin/          # DashboardView, MovieManageView, ShowtimeScheduleView, ReportView
```

### Component & State Guidelines:
- **Single Responsibility**: Decompose large pages into coherent domain components when template exceeds ~250 lines.
- **No Over-Fragmentation**: Avoid creating micro-components for trivial 2-line snippets that are only used once.
- **Thin Templates**: Keep templates declarative; move data manipulation and complex calculations into `<script setup>` computed properties or composables.
- **State Scope**:
  - Use Vue Composition API (`ref`, `reactive`) for local component/page state.
  - Use composables for reusable stateful logic across components.
  - Use Pinia stores strictly when state is shared across multiple routes/components (e.g. auth session, active booking session). Do not create a Pinia store for single-page local state.

---

## 11. API Integration & Contract Enforcement

Backend REST APIs defined in `docs/api.md` are the single source of truth for all data exchanges.

### Rules for API Calls:
1. **Contract Matching**: Before implementing a view, check `docs/api.md` for the exact HTTP method, path, path parameters, query parameters, request body shape, and response DTO structure.
2. **Centralized Service Layer**: Never invoke `axios.get(...)` directly inside `.vue` template/scripts. Always call dedicated service methods in `src/services/`.
3. **No Mock Data in Production**: Connect directly to real backend endpoints. Mocking is only acceptable for isolated unit tests or when the task explicitly requires offline frontend prototyping.
4. **Currency & Date Formatting**:
   - Money values are received in standard numeric VND (e.g. `100000.00`). Format using standard Vietnamese currency format: `new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(amount)`.
   - Dates and timestamps are ISO-8601 strings (`2026-09-02T19:00:00`). Format with standard local date/time utilities (e.g. `dd/MM/yyyy HH:mm`).
5. **Error Propagation**: Centralized Axios interceptor handles standard HTTP errors:
   - `401 Unauthorized`: Triggers token refresh flow; if refresh fails, clears session and redirects to `/login`.
   - `403 Forbidden`: Displays permission alert or redirects to access-denied page.
   - `400 / 409 / 422`: Extracts backend `ErrorResponse.message` or `details` to show contextual form validation errors or toast notifications.
   - `500 / 503`: Displays user-friendly fallback error message without exposing backend stack traces.

---

## 12. Authentication, RBAC & Security

1. **Token Storage**:
   - Access token (15 min expiry) held in memory / Pinia store.
   - Refresh token (7 day expiry) held in secure storage or HTTP-only cookies as supported.
2. **Authorization Header**: Interceptor automatically attaches `Authorization: Bearer <accessToken>` to every outbound API call when logged in.
3. **Route Guards**:
   - Public routes (`/`, `/movies`, `/movies/:id`, `/cinemas`, `/login`, `/register`) accessible to everyone.
   - Customer routes (`/profile`, `/my-bookings`, `/checkout/:id`) require authentication.
   - Admin routes (`/admin/**`) strictly require `ADMIN` in user's role array. Unauthorized attempts redirect to `/login` or `/403`.
4. **Zero Client Secrets**: Never include `JWT_SECRET`, database passwords, `TMDB_API_KEY`, or `VNPAY_HASH_SECRET` in frontend source code or `.env` files. Frontend only references `VITE_API_BASE_URL`.

---

## 13. Complete UI State Handling

Every data-driven screen or interactive component must handle the full spectrum of UI states:

```text
[Initial / Idle] ──► [Loading (Skeleton / Spinner)] ──► [Success (Populated Data)]
                                                   ├──► [Empty State (No Results)]
                                                   └──► [Error State (Retry CTA)]
```

1. **Loading State**: Display clean Tailwind skeleton placeholders or centered loading spinners during asynchronous operations. Disable submit buttons with loading indicators to prevent duplicate clicks.
2. **Success State**: Display cleanly rendered, well-spaced content.
3. **Empty State**: When lists or search queries return 0 items (e.g. no movies found, no showtimes available, empty booking history), show a clear icon, informative description, and a call-to-action button (e.g. "Khám phá phim khác", "Xem lịch chiếu khác").
4. **Error State**: Render clear error alerts with a "Thử lại" (Retry) action button.
5. **Disabled State**: Visually distinguish non-interactive elements (`opacity-50 cursor-not-allowed`) such as sold seats, disabled dates, or inactive vouchers.
6. **Validation State**: Show real-time inline validation feedback on forms (red border, helper message underneath).

---

## 14. Responsive Layout & Mobile Usability

1. **Mobile-First Approach**: Design layouts to scale seamlessly across:
   - **Mobile** (`< 640px`): Single-column cards, collapsible navigation menu, full-width seat map with horizontal scroll/zoom indicator, sticky bottom checkout bar.
   - **Tablet** (`640px - 1024px`): Two-column grids, adaptive sidebars, compact tables.
   - **Desktop** (`> 1024px`): Full multi-column layouts, sticky order summary side panels, widescreen seat matrices.
2. **Tailwind Breakpoints**: Rely on standard Tailwind breakpoints (`sm:`, `md:`, `lg:`, `xl:`, `2xl:`). Do not create arbitrary custom pixel media queries unless strictly required.
3. **Touch Targets**: Ensure buttons, seat selectors, and interactive links maintain a minimum tap target of at least $44 \times 44\text{ px}$ on touch devices.

---

## 15. Styling Standards & Tailwind Conventions

1. **Design Consistency**:
   - **Colors**: Reference semantic tokens defined in `docs/ui/design-system.md` rather than hardcoding arbitrary color classes.
   - **Typography**: Clean hierarchy with consistent font weights (`font-medium`, `font-semibold`, `font-bold`) and tracking.
   - **Border Radius**: Standardize across all cards and controls (`rounded-lg` or `rounded-xl`).
   - **Buttons**: Consistent semantic variant system (Primary, Secondary, Danger, Ghost, Link) following Design System defaults.
   - **Tables**: Styled administrative data tables with alternating rows, crisp borders, sticky headers, and clear action badges.
   - **Modals / Dialogs**: Centered modal with backdrop blur, smooth enter/leave transitions, and ESC / click-outside dismissal.
2. **Reusability**: Always check `src/components/common/` for existing buttons, cards, modals, or inputs before writing new markup.

---

## 16. Screen Implementation Workflow

Follow this 12-step execution workflow when implementing any screen:

```text
1. Inspect existing frontend implementation.
2. Open the corresponding design reference if available (in docs/ui/screens/).
3. Understand the screen's purpose, user audience, and information hierarchy.
4. Check current business rules (docs/business-rules.md).
5. Check current API capabilities (docs/api.md).
6. Preserve the recognizable original structure (~70% baseline).
7. Identify missing states (loading/empty/error) and responsive behavior.
8. Identify valuable, low-risk UX improvements (~30% professional judgment).
9. Implement the screen using Vue 3 <script setup>, TypeScript, and Tailwind CSS.
10. Compare implementation against the design reference.
11. Verify that improvements did not unnecessarily redesign the screen.
12. Browser-verify meaningful frontend changes (layout, console, network, responsiveness).
```

### The 5 Verification Questions:
- **Recognition**: Does the screen still look immediately recognizable as the intended design?
- **Hierarchy**: Is the core information hierarchy preserved?
- **Action Placement**: Are the primary actions still in approximately the same conceptual locations?
- **Value Added**: Were any useful improvements (states, filters, pagination) added without bloating the screen?
- **Integrity**: Were any unsupported backend assumptions or fake endpoints introduced?

---

## 17. CineBook Core Delivery Principle

CineBook is a graduation project with a focused delivery timeline.

Prioritize in order:
1. **Correct Business Functionality** (Booking flow, seat hold, payment redirect, auth security).
2. **Visual Consistency & Clean UI** (Unified Tailwind styling, responsive layouts).
3. **Understandable & Maintainable Code** (Strict TypeScript, clean component hierarchy).
4. **Fast & Focused Implementation** (No unnecessary abstraction or framework churn).

**Prefer**:
- Clear, professional, consistent, maintainable, demonstrable, realistically implementable code.

**Avoid Over-Designing**:
- Excessive animation, complex experimental interaction patterns, heavy visual effects, unnecessary dependencies, or over-engineered component systems.

*The goal is a polished, reliable product, not an over-complicated design showcase at the expense of implementation stability.*
