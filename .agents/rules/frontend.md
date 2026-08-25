---
name: cinebook-frontend
description: Mandatory rules and conventions for Vue 3 frontend development in CineBook.
---

# CineBook Frontend Rules

Canonical Stack: Vue 3 (Composition API, `<script setup>`), Vite, TypeScript, Vue Router, Pinia, Axios, Tailwind CSS.

## 1. Frontend Autonomy

- **Autonomous scope**: Component structure, UI layout, responsive styling, loading/empty/error states, form UX, minor accessibility and visual refinements.
- **Strictly locked**: Backend API contracts, business rules, authentication semantics, and user booking flows must not be altered silently.

## 2. UI / Design Integration (Penpot)

- When a Penpot visual design exists, treat it as the primary visual reference.
- Reproduce hierarchy, spacing, typography, and intended component interactions faithfully.
- Reusable UI components should follow consistent design tokens via Tailwind CSS.

## 3. State Management & API Communication

- **Local State**: Use `ref` / `reactive` within components for local UI concerns (modals, form inputs, local tabs).
- **Pinia Stores**: Use strictly for genuinely shared application state (auth session, cross-route booking progress). Do not place all local state into Pinia.
- **API Client**: All server requests must go through the centralized Axios client. Never connect directly to MySQL or use server-side secrets in browser code.

## 4. Browser Verification

- Frontend work is not complete by source inspection alone.
- When browser tooling is available, verify:
  - Route navigation and route guards.
  - Correct rendering and responsive layout.
  - Form validation and error messaging.
  - API integration, loading spinners, empty states, and runtime console errors.