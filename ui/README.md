# Symmetry UI

The web console, built with **React + AWS Cloudscape** so it looks and behaves
like the AWS console.

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

- Cloudscape is React-only and peer-depends on React ≥ 16.8; this app pins React 18.
- `src/App.tsx` is the console shell (`TopNavigation` + `SideNavigation` + a
  Contracts table). Add pages for submissions, leaderboard, and new builds next.
