# CineBook Design System

---

## 1. Purpose

The **CineBook Design System** serves as the authoritative visual language and user interface specification for both the **Customer-facing** web application and the **Administrator Portal** of CineBook.

It bridges the project's graduation thesis design references (documented in the project thesis report and stored in `docs/ui/screens/`) and the production frontend implementation in **Vue 3 + TypeScript + Tailwind CSS**.

### Core Philosophy:
> **70% Preserve the established design direction + 30% Professional UI/UX judgment.**
> 
> **Motto: "Preserve the structure, improve the experience."**

### Key Objectives:
- **Visual & Structural Consistency**: Deliver unified hierarchy, spacing, typography, and interaction patterns across all modules.
- **Living Design References (~70% Baseline)**: Honor the recognizable layout structures, information hierarchy, and user journeys defined in the graduation thesis wireframes without treating them as frozen, immutable specifications.
- **Controlled Creativity (~30% Improvements)**: Empower developers and AI agents to introduce practical usability, responsiveness, accessibility, complete state handling, and feature completeness improvements.
- **Clarity of Status**: Explicitly distinguish between decisions that are **Confirmed** (grounded in project specifications and APIs), **Proposed** (recommended default UI conventions), **Suggested** (optional UX enhancements), and **To be finalized** (visual styling decisions pending confirmation).

---

## 2. Design Principles

1. **"Preserve the Structure, Improve the Experience"**: Maintain the visual identity, composition, and user journey of the original screen while proactively enhancing its usability and responsiveness.
2. **Clarity over Complexity**: Emphasize legible typography, crisp content containers, and unambiguous call-to-action buttons. Avoid cluttered visual decorations.
3. **Cinema-Oriented Experience**: Provide a modern movie-going experience with high-contrast movie posters, clear showtime slot groupings, intuitive auditorium seat layouts, and real-time reservation feedback.
4. **Frictionless Booking Flow**: Streamline the critical conversion funnel ($\text{Movie} \rightarrow \text{Showtime} \rightarrow \text{Seat Selection} \rightarrow \text{Voucher} \rightarrow \text{Payment} \rightarrow \text{Ticket}$). Minimize steps and cognitive load.
5. **Accessible by Default**: Ensure color contrast compliance (WCAG 2.1 AA), visible keyboard focus rings, accessible form labels, and multi-modal state indicators (combining icons, labels, and borders—not color alone).
6. **Responsive & Mobile-Ready**: Ensure fluid adaptation across mobile ($< 640\text{px}$), tablet ($640\text{px} - 1024\text{px}$), and desktop ($> 1024\text{px}$) viewports.
7. **Zero Over-Engineering**: Utilize native Tailwind CSS utility classes and reusable Vue 3 SFC components without introducing heavyweight component libraries or complex CSS abstraction layers.

---

## 3. Design Reference Rules & Precedence

When implementing or reviewing UI screens:

1. **Design References as Living Baselines**: Thesis report wireframes in `docs/ui/screens/` define the layout structure, information hierarchy, and functional component placement. They are **strong visual baselines, not pixel-perfect or permanently frozen specifications**.
2. **The 70/30 Design Rule**: Target approximately **70% visual/structural fidelity** while using professional judgment for **30% practical improvements** (filling missing states, responsive collapses, accessibility, modern feedback).
3. **Current Functionality Precedence**: Current backend APIs (`docs/api.md`) and business rules (`docs/business-rules.md`) strictly take precedence over obsolete or incomplete wireframe details.
4. **Precedence Hierarchy**:
   1. Explicit developer instruction
   2. `AGENTS.md`
   3. `.agents/rules/frontend.md`
   4. `docs/business-rules.md`
   5. `docs/api.md`
   6. Current implemented backend/frontend behavior
   7. Existing wireframe / design references (`docs/ui/screens/`)
   8. `docs/ui/design-system.md`
   9. General UI/UX conventions
5. **No Arbitrary Full Redesign**: The 70/30 rule is not an authorization to redesign screens. The core purpose, primary user flow, and essential functional blocks of the screen must remain immediately recognizable.
6. **Styling Defaults for Wireframe Gaps**: Wireframes in the graduation thesis are rendered in simple grayscale (white, gray, black). Detailed visual choices (hover animations, focus rings, badge variants, modal transitions) utilize the proposed semantic defaults herein.

---

## 4. Color System

> [!NOTE]
> The graduation thesis wireframes establish layout and hierarchy using neutral grayscale (white, gray, black). Specific brand accents (e.g., primary brand colors) are documented below as **Proposed / To be finalized** semantic tokens.

### 4.1. Semantic Color Tokens

All colors in CineBook are referenced via semantic roles rather than hardcoded hex values:

| Semantic Token | Purpose / Context | Proposed Tailwind Mapping | Status |
|---|---|---|---|
| `color-bg-base` | Main page background | `bg-slate-900` / `bg-gray-900` (Dark) or `bg-gray-50` (Light) | Proposed |
| `color-surface` | Card, container, and panel background | `bg-slate-800` / `bg-gray-800` (Dark) or `bg-white` (Light) | Proposed |
| `color-surface-elevated` | Modal dialog, dropdown menu, popover | `bg-slate-700` / `bg-gray-700` (Dark) or `bg-white` (Light) | Proposed |
| `color-text-primary` | Main headings, primary content text | `text-white` (Dark) / `text-gray-900` (Light) | Proposed |
| `color-text-secondary` | Subtitles, metadata, form labels | `text-slate-300` / `text-gray-600` | Proposed |
| `color-text-muted` | Hints, timestamps, placeholders | `text-slate-400` / `text-gray-400` | Proposed |
| `color-border` | Default card and divider border | `border-slate-700` / `border-gray-200` | Proposed |
| `color-border-focus` | Keyboard focus ring | `ring-indigo-500` / `ring-amber-500` | Proposed |
| `color-primary` | Primary CTA, active selections | `bg-indigo-600` / `bg-amber-500` / `bg-red-600` | **To be finalized** |
| `color-primary-hover` | Primary CTA hover state | `hover:bg-indigo-700` / `hover:bg-amber-600` | **To be finalized** |
| `color-secondary` | Secondary action buttons | `bg-slate-700 hover:bg-slate-600` | Proposed |
| `color-success` | Success alerts, `PAID` status, `ACTIVE` status | `text-emerald-400 bg-emerald-950/40 border-emerald-800` | Proposed |
| `color-warning` | Warning alerts, countdown urgency, `PENDING` | `text-amber-400 bg-amber-950/40 border-amber-800` | Proposed |
| `color-error` | Error alerts, `FAILED`, `CANCELLED` | `text-rose-400 bg-rose-950/40 border-rose-800` | Proposed |
| `color-info` | Info banners, metadata tags | `text-sky-400 bg-sky-950/40 border-sky-800` | Proposed |

### 4.2. Seat Matrix Color Tokens (Domain Specific)

Seat state visualization must use distinct semantic color tokens combined with border and symbol indicators:

| Seat State | Semantic Meaning | Proposed Styling | Indicator / Symbol | Status |
|---|---|---|:---:|---|
| **Available** | Seat ready to be selected | `bg-slate-700 border border-slate-500 text-slate-200 hover:border-white` | Standard label (e.g. `A1`) | **Confirmed** (State) |
| **Selected** | Selected by current user in session | `bg-indigo-600 border-indigo-400 text-white ring-2 ring-indigo-300` | Solid highlight + Checkmark | **Confirmed** (State) |
| **Held** | Temporarily locked by another customer | `bg-amber-800/60 border border-amber-600 text-amber-300 cursor-not-allowed` | Clock icon / Hash pattern | **Confirmed** (State) |
| **Sold** | Ticket issued (`VALID` / `USED`) | `bg-slate-900 border border-slate-800 text-slate-600 cursor-not-allowed` | Cross `✕` or filled dark | **Confirmed** (State) |
| **Broken** | Physically damaged seat (`BROKEN`) | `bg-rose-950/40 border border-rose-800 text-rose-500 cursor-not-allowed` | Wrench icon / Diagonal slash | **Confirmed** (State) |
| **VIP Type** | VIP seat with higher price modifier | Distinct border accent (e.g. `border-amber-400 text-amber-200`) | Star badge / Gold border | Proposed |

---

## 5. Typography

The design system utilizes the native system font stack for optimal cross-platform rendering and zero additional network payload:

```css
font-family: ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
```

### Type Scale & Hierarchy

| Role | Font Size | Line Height | Weight | Tailwind Classes | Typical Use |
|---|---|---|---|---|---|
| **Display / Hero** | $32\text{px} - 36\text{px}$ | $40\text{px}$ | Bold (`700`) | `text-3xl lg:text-4xl font-bold tracking-tight` | Landing banner, Movie hero title |
| **Heading 1 (H1)** | $24\text{px} - 28\text{px}$ | $32\text{px}$ | Bold (`700`) | `text-2xl lg:text-3xl font-bold` | Page titles, Admin view titles |
| **Heading 2 (H2)** | $20\text{px} - 22\text{px}$ | $28\text{px}$ | SemiBold (`600`) | `text-xl lg:text-2xl font-semibold` | Section headers, Modal titles |
| **Heading 3 (H3)** | $16\text{px} - 18\text{px}$ | $24\text{px}$ | SemiBold (`600`) | `text-base lg:text-lg font-semibold` | Card headers, Group titles |
| **Body (Default)** | $14\text{px} - 16\text{px}$ | $22\text{px} - 24\text{px}$ | Regular (`400`) | `text-sm sm:text-base font-normal` | Paragraphs, descriptions, overviews |
| **Small / Metadata** | $12\text{px} - 13\text{px}$ | $18\text{px}$ | Regular (`400`) | `text-xs sm:text-sm font-normal` | Timestamps, durations, genres, badges |
| **Button Text** | $14\text{px}$ | $20\text{px}$ | Medium (`500`) | `text-sm font-medium tracking-wide` | Action buttons, tabs |
| **Table Header** | $12\text{px}$ | $16\text{px}$ | SemiBold (`600`) | `text-xs font-semibold uppercase tracking-wider` | Admin data table column headers |

---

## 6. Spacing

Adhere strictly to the standard Tailwind 4-based spacing scale ($4\text{px}$ unit). Avoid arbitrary pixel values.

| Token | Pixels | Tailwind Utility | Common Usage |
|---|---|---|---|
| `space-1` | $4\text{px}$ | `p-1`, `gap-1`, `m-1` | Compact icon gaps, badge paddings |
| `space-2` | $8\text{px}$ | `p-2`, `gap-2`, `m-2` | Button inner padding (Y), form field spacing, seat gap |
| `space-3` | $12\text{px}$ | `p-3`, `gap-3`, `m-3` | List item spacing, table cell padding |
| `space-4` | $16\text{px}$ | `p-4`, `gap-4`, `m-4` | Card inner padding, form group gap |
| `space-6` | $24\text{px}$ | `p-6`, `gap-6`, `m-6` | Page section spacing, modal container padding |
| `space-8` | $32\text{px}$ | `p-8`, `gap-8`, `m-8` | Major section gaps on desktop |
| `space-12` | $48\text{px}$ | `p-12`, `gap-12`, `my-12` | Hero section top/bottom padding |

---

## 7. Layout & Container Conventions

### 7.1. Viewport Containers
- **Customer Main Container**: `max-w-7xl mx-auto px-4 sm:px-6 lg:px-8` (Centers content with adaptive gutters).
- **Admin Main Container**: `w-full px-4 sm:px-6 lg:px-8 py-6` (Maximizes workspace for data tables and scheduling boards).
- **Authentication Container**: `max-w-md mx-auto px-4 py-12` (Compact centered card).

### 7.2. Shell Layouts
1. **Customer Shell (`DefaultLayout.vue`)**:
   - Sticky Header (`h-16`, navigation links, search trigger, customer user menu).
   - Dynamic Content View (`min-h-[calc(100vh-4rem-12rem)]`).
   - Standard Footer (Cinema information, hotline, links, copyright).
2. **Admin Shell (`AdminLayout.vue`)**:
   - Fixed / Responsive Sidebar (`w-64`, collapsible on mobile).
   - Top Header (`h-16`, breadcrumbs, admin profile badge, quick logout).
   - Scrollable Main Workspace (`flex-1 overflow-y-auto bg-slate-900 p-6`).

---

## 8. Border Radius

| Component Type | Proposed Radius | Tailwind Class |
|---|---|---|
| **Buttons & Inputs** | $6\text{px} - 8\text{px}$ | `rounded-lg` |
| **Cards & Panels** | $12\text{px}$ | `rounded-xl` |
| **Modals & Dialogs** | $16\text{px}$ | `rounded-2xl` |
| **Badges & Tags** | $9999\text{px}$ (Pill) | `rounded-full` |
| **Seats in Map** | $4\text{px} - 6\text{px}$ | `rounded` or `rounded-md` |
| **Posters & Thumbnails** | $8\text{px}$ | `rounded-lg overflow-hidden` |

---

## 9. Shadows & Elevation

Keep elevation levels simple and functional:

- **Flat / Surface**: `shadow-none border border-slate-700` (Default cards and table containers).
- **Elevated / Dropdown**: `shadow-lg shadow-black/30 border border-slate-700` (Menus, tooltips, popovers).
- **Modal / Overlay**: `shadow-2xl shadow-black/50 border border-slate-600` (Dialog modals, floating alerts).

---

## 10. Buttons

Buttons communicate action hierarchy. Every button must support **Normal**, **Hover**, **Active**, **Focus**, **Disabled**, and **Loading** states.

```html
<!-- Base Button Template Structure -->
<button class="inline-flex items-center justify-center font-medium rounded-lg transition-colors duration-150 focus:outline-none focus:ring-2 focus:ring-offset-2 disabled:opacity-50 disabled:cursor-not-allowed">
  <Spinner v-if="loading" class="mr-2 h-4 w-4" />
  <slot />
</button>
```

### Semantic Variants

| Variant | Purpose | Base Styling (Proposed) | Focus Ring |
|---|---|---|---|
| **Primary** | Main action (Book Now, Pay, Confirm) | `bg-indigo-600 hover:bg-indigo-700 text-white` | `focus:ring-indigo-500` |
| **Secondary** | Supporting action (Cancel, Back, Filter) | `bg-slate-700 hover:bg-slate-600 text-slate-200 border border-slate-600` | `focus:ring-slate-400` |
| **Danger** | Destructive action (Cancel Booking, Delete, Block) | `bg-rose-600 hover:bg-rose-700 text-white` | `focus:ring-rose-500` |
| **Ghost** | Subtle actions, table row actions | `bg-transparent hover:bg-slate-800 text-slate-300 hover:text-white` | `focus:ring-slate-500` |
| **Link** | In-text navigation | `text-indigo-400 hover:underline p-0 bg-transparent` | `focus:ring-indigo-400` |

---

## 11. Form Controls

All form controls must include explicit `<label>`, accessible validation states, and placeholder texts.

### 11.1. Text Inputs & Selects
- **Default State**: `bg-slate-800 border border-slate-700 text-white placeholder-slate-400 rounded-lg px-3.5 py-2 text-sm focus:border-indigo-500 focus:ring-1 focus:ring-indigo-500 focus:outline-none`
- **Error State**: `border-rose-500 focus:border-rose-500 focus:ring-rose-500 text-rose-100` with an associated `<p class="mt-1 text-xs text-rose-400">` message.
- **Disabled State**: `bg-slate-900 border-slate-800 text-slate-500 cursor-not-allowed`

### 11.2. Checkboxes & Radios
- Custom styled using Tailwind `accent-indigo-600` with adequate touch targets ($20\text{px} \times 20\text{px}$).

---

## 12. Cards

Cards organize content into clear, distinct visual boundaries.

### 12.1. Base Card
`bg-slate-800 border border-slate-700 rounded-xl p-5 shadow-sm`

### 12.2. Domain Cards
- **Movie Card**: Poster image ($2:3$ ratio), age rating badge, title (truncate 2 lines), duration/genre tags, "Đặt vé" (Book) button.
- **Cinema Card**: Cinema name, city badge, address, hotline, active auditorium count.
- **Showtime Card / Slot**: Time pill (`19:00`), format badge (`2D`), language badge, price subtitle.
- **Booking Summary Card**: Sticky desktop card showing selected movie, cinema, auditorium, seat list, price breakdown, voucher discount line, total amount, and countdown timer.
- **Admin KPI Card**: Metric value (large bold), title, period subtitle, status icon, and trend indicator.

---

## 13. Navigation Structure & Evolution

1. **Customer Header Navbar** (Proposed):
   - Brand logo + title.
   - Primary links: *Trang chủ*, *Phim đang chiếu*, *Phim sắp chiếu*, *Cụm rạp*, *Khuyến mãi*.
   - Right side: Search bar / trigger, User Profile dropdown or Login/Register buttons.
2. **Admin Sidebar Evolution** (Proposed):
   - Integrates implemented backend modules naturally into clear logical groups:
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
   - Active link highlight: `bg-indigo-600/20 text-indigo-400 border-l-4 border-indigo-500 font-medium`.
3. **Mobile Drawer Navigation** (Proposed):
   - Slide-over sheet from left with backdrop blur and touch-friendly vertical menu items.

---

## 14. Data Tables (Admin)

Administrative data tables must be clean, dense, and responsive:

- **Container**: `overflow-x-auto rounded-xl border border-slate-700 bg-slate-800`
- **Header**: `bg-slate-850 px-4 py-3 text-left text-xs font-semibold uppercase text-slate-400 tracking-wider border-b border-slate-700`
- **Row**: `border-b border-slate-700/60 hover:bg-slate-700/40 transition-colors px-4 py-3 text-sm text-slate-200`
- **Status Badges**: Small rounded pill (`px-2.5 py-0.5 text-xs font-medium rounded-full`).
  - `PAID` / `ACTIVE` / `VALID` $\rightarrow$ `bg-emerald-900/40 text-emerald-300 border border-emerald-700`
  - `PENDING_PAYMENT` / `SCHEDULED` $\rightarrow$ `bg-amber-900/40 text-amber-300 border border-amber-700`
  - `REFUNDED` / `CANCELLED` / `BLOCKED` $\rightarrow$ `bg-rose-900/40 text-rose-300 border border-rose-700`
- **Pagination** (Proposed): Bottom toolbar with row count ("Hiển thị 1-20 của 150"), page number buttons, and next/prev controls.

---

## 15. Modals & Dialogs

- **Backdrop**: `fixed inset-0 bg-black/70 backdrop-blur-sm z-50 flex items-center justify-center p-4`
- **Dialog Container**: `bg-slate-800 border border-slate-700 rounded-2xl w-full max-w-lg shadow-2xl overflow-hidden`
- **Header**: `px-6 py-4 border-b border-slate-700 flex justify-between items-center` with title and `✕` close button.
- **Body**: `p-6 max-h-[75vh] overflow-y-auto`
- **Footer**: `px-6 py-4 border-t border-slate-700 flex justify-end gap-3 bg-slate-850`
- **Dismissal Rules**: Supports `Esc` key press, backdrop click (unless operation is in progress), and explicit "Hủy" (Cancel) button.

---

## 16. Feedback Components

- **Toast Notifications**: Floating notifications at top-right (`fixed top-4 right-4 z-50`). Auto-dismiss after 4 seconds. Supports `success`, `error`, `warning`, `info`.
- **Alert Banners**: Inline banners with icon, title, description, and optional dismiss button.
- **Confirmation Dialog**: Modal prompt with explicit Danger/Confirm action (e.g. "Xác nhận hủy vé", "Xác nhận hoàn tiền").

---

## 17. UI State Handling (Loading, Empty, Error)

Every data-driven view must implement standardized state components:

1. **Loading Skeleton**: `animate-pulse bg-slate-700 rounded` mimicking the shape of the upcoming content (movie card, table rows, seat grid).
2. **Empty State**: Centered placeholder with an illustrative icon, title ("Không tìm thấy kết quả"), description, and action CTA.
3. **Error State**: Centered error alert with clear Vietnamese error message and a "Thử lại" (Retry) action button that re-triggers the query.

---

## 18. Movie Domain UI

- **Poster Component**: Standard $2:3$ aspect ratio container (`aspect-[2/3]`). Includes fallback placeholder image if URL fails to load.
- **Age Rating Badges** (Proposed): High-contrast indicator pills based on Vietnamese film standards (`P`, `K`, `T13`, `T16`, `T18`).
- **Trailer Action** (Suggested): Play button on poster or detail view that opens a responsive trailer modal (when `trailerUrl` is available).

---

## 19. Showtime Domain UI

- **Date Selector Bar**: Horizontal scrollable bar with day buttons showing day-of-week, date, and month (e.g. "T2, 02/09"). Highlight active date.
- **Cinema Grouping**: Showtimes grouped under Cinema $\rightarrow$ Format (2D/3D/IMAX).
- **Showtime Slot Button**: Compact card displaying start time (`19:00`), format, auditorium name, and base price. Disabled if past or finished.

---

## 20. Seat Map Domain UI (Critical)

The seat map is the central interaction point of CineBook:

```text
               ┌───────────────────────────────┐
               │         MÀN HÌNH CHÍNH        │
               │   (Curved Projection Screen)  │
               └───────────────────────────────┘

     Row A  [A1] [A2] [A3] [A4]       [A5] [A6] [A7] [A8]
     Row B  [B1] [B2] [B3] [B4]       [B5] [B6] [B7] [B8]
     Row C  [C1] [C2] [C3] [C4] (VIP) [C5] [C6] [C7] [C8]

     Legend:  ( ) Trống   (✓) Đang chọn   (⏱) Đang giữ   (✕) Đã bán   (⚠) Hỏng
```

### Key Seat Map Conventions:
1. **Screen Indicator**: Prominent curved top banner ("Màn hình").
2. **Row & Seat Number Labels**: Row letters (A, B, C...) displayed on both flanks.
3. **Seat Matrix Visual Indicators**:
   - **Available**: Distinct clickable button with seat code.
   - **Selected**: Accent highlight with checkmark symbol.
   - **Held (by others)**: Subdued clock icon with tooltip "Ghế đang được giữ chỗ".
   - **Sold**: Subdued dark container with `✕` mark.
   - **Broken**: Warning outline with tooltip "Ghế đang bảo trì".
4. **Mobile Adaptability**: Wrap seat grid in a pinch-to-zoom / horizontal scroll container with an intuitive visual indicator ("Kéo để xem toàn bộ phòng chiếu").

---

## 21. Booking & Checkout Domain UI

- **Hold Countdown Timer**: Prominent banner showing remaining time (5 minutes initial duration confirmed by business rules). Changes color to warning/red when $< 60$ seconds remain. Displays explicit modal on expiration.
- **Price Breakdown**: List of selected seats, promotion code input with "Áp dụng" (Apply) button, discount amount deducted in green, and bold Final Amount.
- **Payment Method Selection**: Visual cards for VNPay Sandbox with logo and description.
- **Payment Result Banner**: High-impact icon (Green Checkmark for `SUCCESS`, Red Cross for `FAILED`) with order code, booking details, and "Xem vé đã mua" (View Tickets) CTA.

---

## 22. Administrator Domain UI

- **KPI Metric Cards** (Confirmed by Reporting API): Grid showing Gross Revenue, Net Revenue, Tickets Sold, and Average Occupancy Rate.
- **Showtime Scheduling View** (Proposed): Timeline or matrix view showing auditorium schedules with collision validation. Drag-and-drop scheduling is **Suggested** as a future enhancement if supported by frontend components.
- **Export Toolbar** (Confirmed by Reporting API): Buttons for "Xuất CSV" (UTF-8 BOM) and "Xuất Excel" (XLSX) on administrative reporting views.

---

## 23. Responsive Layout Guidelines

| Breakpoint | Target Screen | Layout Behaviors |
|---|---|---|
| **Mobile (`< 640px`)** | Phones (Portrait) | - Single column layouts<br>- Collapsible hamburger menu<br>- Seat map horizontal scroll<br>- Sticky bottom checkout bar with total and "Tiếp tục" button<br>- Tables stacked as card lists |
| **Tablet (`640px - 1024px`)** | Tablets / Small Laptops | - 2-3 column movie grids<br>- Compact admin sidebar (icons + tooltips)<br>- 2-column checkout layout |
| **Desktop (`> 1024px`)** | Laptops & Desktops | - Full 4-5 column movie grids<br>- Fixed admin sidebar with expanded labels<br>- Split checkout: 2/3 Seat map + 1/3 Sticky summary panel<br>- Full data tables with sorting & batch actions |

---

## 24. Accessibility Standards (a11y)

1. **Color Independence**: Crucial domain states (Seat status, Booking status, Form errors) must never rely on color alone. Always pair colors with icons, text labels, or distinct geometric patterns.
2. **Keyboard Navigation**:
   - Logical `Tab` order through form fields and seat grids.
   - Arrow-key navigation across seat grid items.
   - `Enter` or `Space` to toggle seat selection.
3. **Focus States**: High-contrast focus rings (`focus-visible:ring-2 focus-visible:ring-offset-2`).
4. **Touch Targets**: Minimum interactive size of $44 \times 44\text{ px}$ on mobile touch devices.

---

## 25. Tailwind CSS Implementation Mapping

Map semantic design system tokens directly to Tailwind CSS classes:

```text
Design Token          ──►  Tailwind Utility Mapping
─────────────────────────────────────────────────────────────
Surface (Card)        ──►  bg-slate-800 border border-slate-700
Primary Action        ──►  bg-indigo-600 hover:bg-indigo-700 text-white
Danger Action         ──►  bg-rose-600 hover:bg-rose-700 text-white
Text Primary          ──►  text-white / text-slate-100
Text Secondary        ──►  text-slate-300
Border Default        ──►  border-slate-700
Focus Ring            ──►  focus:ring-2 focus:ring-indigo-500 focus:outline-none
Status Badge (Paid)   ──►  bg-emerald-900/40 text-emerald-300 border border-emerald-700
Card Radius           ──►  rounded-xl
Button Radius         ──►  rounded-lg
```

---

## 26. Design Token Status Matrix

This matrix authoritatively categorizes all design decisions for the CineBook project into four distinct status tiers:

| Category / Decision Area | Current Status | Authoritative Source / Basis | Note |
|---|:---:|---|---|
| **Frontend Tech Stack** (Vue 3, Vite, TS, Tailwind, Pinia, Axios) | **Confirmed** | `AGENTS.md` §3 | Strictly locked |
| **Backend REST API Contracts & DTOs** | **Confirmed** | `docs/api.md` | Single source of truth |
| **Seat States** (`AVAILABLE`, `SELECTED`, `HELD`, `SOLD`, `BROKEN`) | **Confirmed** | `docs/business-rules.md` §8 & DB schema | Backend invariant |
| **Booking Lifecycle States** (`PENDING_PAYMENT`, `PAID`, `EXPIRED`, `REFUNDED`, `CANCELLED`) | **Confirmed** | `docs/business-rules.md` §8.3 | Backend invariant |
| **Hold Expiration Duration** (5 minutes countdown) | **Confirmed** | `docs/business-rules.md` §8.2 | Backend invariant |
| **Reporting Dashboard KPIs** (Gross, Net, Refund, Tickets, Occupancy) | **Confirmed** | `docs/api.md` §10 & Reporting module | Supported by API |
| **Report Export Formats** (CSV with UTF-8 BOM, XLSX) | **Confirmed** | `docs/api.md` §10.5 | Supported by API |
| **Grayscale Base Theme** (Dark surface palette) | **Proposed** | Cinema UI convention + Tailwind Slate | Default recommendation |
| **Component Radius Scale** (`rounded-lg`, `rounded-xl`, `rounded-2xl`) | **Proposed** | Design System standardization | Default recommendation |
| **Spacing Scale** (Tailwind 4px base scale) | **Proposed** | Design System standardization | Default recommendation |
| **Responsive Breakpoints** (`sm:`, `md:`, `lg:`, `xl:`) | **Proposed** | Standard Tailwind CSS utilities | Default recommendation |
| **Age Rating Categorization** (`P`, `K`, `T13`, `T16`, `T18`) | **Proposed** | Vietnamese Cinema Regulations / `docs/use-cases/movie.md` | Standard UI badge convention |
| **Admin Navigation Structure** (Categorized sidebar groups) | **Proposed** | System domain decomposition | Recommended navigation layout |
| **Drag-and-Drop Showtime Scheduling** | **Suggested** | UX enhancement | Optional; fallback to standard form/matrix |
| **Movie Trailer Modal** | **Suggested** | UX enhancement | Conditional on `trailerUrl` availability |
| **Breadcrumb Navigation on Admin Views** | **Suggested** | UX enhancement | Recommended for deep nested views |
| **Brand Accent Color** (Indigo / Amber / Red) | **To be finalized** | Developer / Designer selection | Pending final brand decision |
