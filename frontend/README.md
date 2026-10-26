# CourseGuide frontend (Material 3)

React 19 + TypeScript + Vite + Tailwind CSS v4, built to the [Material Design 3](https://m3.material.io/) guidelines.

## Run

```bash
npm install
npm run dev     # http://localhost:5173, proxies /api to the backend on :8080
npm run build   # type-check and production build
npm run lint
npm test        # Vitest unit tests (jsdom)
```

## How Material 3 is applied

| Area | Implementation |
| --- | --- |
| Components | [`@material/web`](https://material-web.dev/), Google's official M3 web components (buttons, FAB, text fields, select, dialog, chips, lists, progress, ripple, focus ring). Registered in `src/material.ts`, JSX typings in `src/md.d.ts`. |
| Color | Dynamic color: `@material/material-color-utilities` builds a tonal-spot scheme from a seed color (light, dark, or system) and writes every `--md-sys-color-*` role to `:root` (`src/theme/theme.ts`). Chrome's `theme-color` follows the surface color. |
| Tokens in Tailwind | `src/index.css` replaces Tailwind's palette, radius, shadow, and breakpoint scales with M3 tokens, so utilities only express M3 roles (`bg-surface-container`, `text-on-primary-container`, `rounded-md` = 12dp, …). |
| Typography | M3 type scale classes (`md-typescale-*`) with Roboto, plus Material Symbols Rounded icons. |
| Layout | M3 breakpoints: **compact** (<600) uses a flexible navigation bar, an extended FAB, and 16dp margins. **Medium/expanded** (600–1199) uses a collapsed navigation rail with the FAB and 24dp margins. **Large+** (≥1200) uses an expanded rail with an extended FAB. Expanded and larger windows switch to two panes. |
| Custom components | Navigation bar/rail, small top app bar, segmented button, and snackbar are not in `@material/web` stable, so they are built from M3 tokens and specs using `md-ripple` / `md-focus-ring` for state layers. |
| Mobile | `viewport-fit=cover` with safe-area insets for Android gesture navigation, `100dvh`, a web app manifest for installing to the home screen, and hash-based navigation so the Android back button moves between destinations. |

## Structure

```
src/
  App.tsx              adaptive shell (nav bar / rail, top app bar, FAB, sync dialog)
  state/AppState.tsx   app state and all backend calls
  lib/api.ts           typed client for every /api endpoint
  pages/               Plan, Results, Saved, Settings
  components/          M3 wrappers and custom components
  theme/theme.ts       dynamic color
```
