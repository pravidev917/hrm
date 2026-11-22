# HRM frontend

React + Vite single-page app for the HRM Spring Boot backend (`../backend`).

## Run

```bash
cp .env.example .env         # local browser-safe Vite settings
npm ci
npm run dev                  # http://localhost:5173 (the backend CORS allows only this origin)
npm run lint
npm run build
```

Vite reads `frontend/.env` automatically. Set `VITE_PROXY_TARGET` there if the
backend runs on a different port. Do not put backend credentials in this file.

## How it talks to the backend

- Two entry points, each with its own URL and endpoint: employees sign in at `/login` (`POST /api/auth/employee/login`), administrators at `/admin/login` (`POST /api/auth/admin/login`). There is no account-type switch in the UI.
- Nobody can self-register. Administrators create employee accounts (`/employees`) and other administrator accounts (`/administrators`).
- Auth is the backend's HttpOnly `HRM_AUTH` cookie, so Axios uses `withCredentials`. The JWT is never read or stored by JavaScript; only name/email/role are cached in `localStorage` for display.
- Session restore uses `GET /api/auth/me`; any `401` from a protected endpoint signs the user out.
- Roles are `ADMIN` and `EMPLOYEE`. Hiding UI is convenience only; the backend enforces access.
- All HTTP calls live in `src/api/`. Search, sorting, filtering and pagination are **client-side** over the lists the API returns (the backend has no paging/filter parameters for these lists).

## Layout

`src/api` API modules · `src/config/brand.js` product name + logo path · `src/context` auth + toasts · `src/components` layout/common UI · `src/pages` screens · `src/routes` route guard.

Drop the company logo at `public/company-logo.png` to show it in the sidebar and on the sign-in cards; a monogram is shown until the file exists.

## Branding

The palette lives in `src/styles/tokens.css` (`--purple-*`: Dark Purple `#40156B`, Deep Purple `#6B368E`, Primary Purple `#8955A5`, Light Purple `#A286B6`, Very Light Purple `#C7B0D7`, White `#FFFFFF`). Name and logo paths are in `src/config/brand.js`.

The delivered logo (`public/company-logo.png`, white background) is converted into display assets by:

```bash
python scripts/derive_logo_assets.py
```

- `public/company-logo-trim.png` — trimmed lockup, shown on the sign-in cards
- `public/company-mark.png` — emblem only, transparent, used in the sidebar chip and as the favicon
