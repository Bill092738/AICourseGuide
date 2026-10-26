import { beforeEach, describe, expect, it } from "vitest"
import { applyTheme, DEFAULT_THEME, loadThemePrefs, saveThemePrefs, SEED_COLORS } from "./theme"

const css = (name: string) => document.documentElement.style.getPropertyValue(name)

describe("theme preferences", () => {
  it("returns defaults when nothing is stored", () => {
    expect(loadThemePrefs()).toEqual(DEFAULT_THEME)
  })

  it("round-trips saved preferences", () => {
    saveThemePrefs({ mode: "dark", seed: SEED_COLORS[2].hex })

    expect(loadThemePrefs()).toEqual({ mode: "dark", seed: SEED_COLORS[2].hex })
  })

  it("ignores invalid or corrupt stored values", () => {
    localStorage.setItem("courseguide-theme", JSON.stringify({ mode: "neon", seed: "red" }))
    expect(loadThemePrefs()).toEqual(DEFAULT_THEME)

    localStorage.setItem("courseguide-theme", "{not json")
    expect(loadThemePrefs()).toEqual(DEFAULT_THEME)
  })
})

describe("applyTheme", () => {
  beforeEach(() => {
    document.head.innerHTML = '<meta name="theme-color" content="#000000">'
    document.documentElement.removeAttribute("style")
  })

  it("writes every M3 color role as a CSS variable", () => {
    applyTheme({ mode: "light", seed: "#6750A4" })

    for (const role of ["primary", "on-primary", "surface", "surface-container-highest", "outline-variant", "inverse-primary"]) {
      expect(css(`--md-sys-color-${role}`), role).toMatch(/^#[0-9a-f]{6}$/)
    }
    expect(document.documentElement.dataset.theme).toBe("light")
    expect(document.documentElement.style.colorScheme).toBe("light")
  })

  it("dark mode produces a darker surface and updates the browser theme color", () => {
    applyTheme({ mode: "light", seed: "#4285F4" })
    const lightSurface = css("--md-sys-color-surface")
    applyTheme({ mode: "dark", seed: "#4285F4" })
    const darkSurface = css("--md-sys-color-surface")

    const luminance = (hex: string) => parseInt(hex.slice(1, 3), 16) + parseInt(hex.slice(3, 5), 16) + parseInt(hex.slice(5, 7), 16)
    expect(luminance(darkSurface)).toBeLessThan(luminance(lightSurface))
    expect(document.documentElement.dataset.theme).toBe("dark")
    expect(document.querySelector('meta[name="theme-color"]')?.getAttribute("content")).toBe(darkSurface)
  })

  it("different seeds produce different primary colors", () => {
    applyTheme({ mode: "light", seed: SEED_COLORS[0].hex })
    const a = css("--md-sys-color-primary")
    applyTheme({ mode: "light", seed: SEED_COLORS[5].hex })

    expect(css("--md-sys-color-primary")).not.toBe(a)
  })
})
