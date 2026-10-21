# Symmetry UI

The web console. React + the custom **Harbor** design system
(macOS-Settings-inspired, solid surfaces, light/dark) with Radix UI primitives —
see [`docs/UI-PLAN.md`](../docs/UI-PLAN.md). AWS Cloudscape has been removed; do
not add Cloudscape screens.

## Run

```sh
npm install
npm run dev        # http://localhost:5173
```

The dev server proxies `/api` to the Java API (default `http://localhost:8080`).
Override with `SYMMETRY_API=http://host:port npm run dev`.

## Build

```sh
npm run build      # type-checks then bundles to dist/
```

## Notes

- `src/App.tsx` is the route tree; `src/components/Shell.tsx` is the Harbor
  window, title bar and role-specific sidebar.
- Routes: `/user/login`, `/user/admin/login`, `/user/*`, `/user/admin/*` — see
  `docs/UI-PLAN.md`.
- Dev sign-in stores a user email in `localStorage` (`symmetry.devUser`) and
  sends it as `X-Symmetry-User`. The seeded accounts are
  `worker@symmetry.local`, `publisher@symmetry.local`, `admin@symmetry.local`.
