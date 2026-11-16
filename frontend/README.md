# HRM frontend

React + Vite single-page app for the HRM Spring Boot backend (`../backend`).

## Run

```bash
cp .env.example .env.local   # optional; defaults to http://localhost:8080
npm ci
npm run dev                  # http://localhost:5173 (the backend CORS allows only this origin)
npm run lint
npm run build
```

## How it talks to the backend

- Auth is the backend's HttpOnly `HRM_AUTH` cookie, so Axios uses `withCredentials`. The JWT is never read or stored by JavaScript; only name/email/role are cached in `localStorage` for display.
- Session restore uses `GET /api/auth/me`; any `401` from a protected endpoint signs the user out.
- Roles are `ADMIN` and `EMPLOYEE`. Hiding UI is convenience only; the backend enforces access.
- All HTTP calls live in `src/api/`. Search, sorting, filtering and pagination are **client-side** over the lists the API returns (the backend has no paging/filter parameters for these lists).

## Layout

`src/api` API modules · `src/context` auth + toasts · `src/components` layout/common UI · `src/pages` screens · `src/routes` route guard.
