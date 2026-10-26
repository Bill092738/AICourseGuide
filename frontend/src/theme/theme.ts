import {
  Hct,
  MaterialDynamicColors,
  SchemeTonalSpot,
  hexFromArgb,
  argbFromHex,
} from "@material/material-color-utilities"

export type ThemeMode = "system" | "light" | "dark"

export type ThemePrefs = {
  mode: ThemeMode
  seed: string
}

/** Seed colors offered in Settings, similar to Android's wallpaper/basic color options. */
export const SEED_COLORS: { name: string; hex: string }[] = [
  { name: "Blue", hex: "#4285F4" },
  { name: "Baseline", hex: "#6750A4" },
  { name: "Teal", hex: "#00897B" },
  { name: "Green", hex: "#34A853" },
  { name: "Amber", hex: "#F9AB00" },
  { name: "Rose", hex: "#D81B60" },
]

const STORAGE_KEY = "courseguide-theme"
export const DEFAULT_THEME: ThemePrefs = { mode: "system", seed: SEED_COLORS[0].hex }

// Every md.sys.color role written as --md-sys-color-<kebab-name>.
const ROLES = [
  "primary", "onPrimary", "primaryContainer", "onPrimaryContainer", "inversePrimary",
  "secondary", "onSecondary", "secondaryContainer", "onSecondaryContainer",
  "tertiary", "onTertiary", "tertiaryContainer", "onTertiaryContainer",
  "error", "onError", "errorContainer", "onErrorContainer",
  "background", "onBackground",
  "surface", "surfaceDim", "surfaceBright", "surfaceTint",
  "surfaceContainerLowest", "surfaceContainerLow", "surfaceContainer",
  "surfaceContainerHigh", "surfaceContainerHighest",
  "onSurface", "surfaceVariant", "onSurfaceVariant",
  "inverseSurface", "inverseOnSurface",
  "outline", "outlineVariant", "shadow", "scrim",
  "primaryFixed", "primaryFixedDim", "onPrimaryFixed", "onPrimaryFixedVariant",
  "secondaryFixed", "secondaryFixedDim", "onSecondaryFixed", "onSecondaryFixedVariant",
  "tertiaryFixed", "tertiaryFixedDim", "onTertiaryFixed", "onTertiaryFixedVariant",
] as const

const kebab = (s: string) => s.replace(/[A-Z]/g, (m) => "-" + m.toLowerCase())

export function loadThemePrefs(): ThemePrefs {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return DEFAULT_THEME
    const parsed = JSON.parse(raw) as Partial<ThemePrefs>
    const mode: ThemeMode = parsed.mode === "light" || parsed.mode === "dark" ? parsed.mode : "system"
    const seed = typeof parsed.seed === "string" && /^#[0-9a-f]{6}$/i.test(parsed.seed) ? parsed.seed : DEFAULT_THEME.seed
    return { mode, seed }
  } catch {
    return DEFAULT_THEME
  }
}

export function saveThemePrefs(prefs: ThemePrefs) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(prefs))
}

export function systemPrefersDark() {
  return window.matchMedia("(prefers-color-scheme: dark)").matches
}

export function resolveDark(mode: ThemeMode) {
  return mode === "dark" || (mode === "system" && systemPrefersDark())
}

/** Generates a tonal-spot dynamic scheme (the Android default) and writes all color roles to :root. */
export function applyTheme(prefs: ThemePrefs) {
  const isDark = resolveDark(prefs.mode)
  const scheme = new SchemeTonalSpot(Hct.fromInt(argbFromHex(prefs.seed)), isDark, 0, "2025")
  const root = document.documentElement
  const colors = MaterialDynamicColors as unknown as Record<string, { getArgb: (s: SchemeTonalSpot) => number }>

  for (const role of ROLES) {
    root.style.setProperty(`--md-sys-color-${kebab(role)}`, hexFromArgb(colors[role].getArgb(scheme)))
  }
  root.style.colorScheme = isDark ? "dark" : "light"
  root.dataset.theme = isDark ? "dark" : "light"

  // Match Chrome's status bar / address bar to the top app bar surface.
  const surface = hexFromArgb(colors.surface.getArgb(scheme))
  document.querySelectorAll('meta[name="theme-color"]').forEach((m) => m.setAttribute("content", surface))
}
