import { useCallback, useEffect, useState } from "react"
import { Dialog } from "./components/Dialog"
import { NavigationBar, NavigationRail, TopAppBar, type Destination } from "./components/Navigation"
import { useWindowClass } from "./hooks/useWindowClass"
import { PlanPage } from "./pages/PlanPage"
import { ResultsPage } from "./pages/ResultsPage"
import { SavedPage } from "./pages/SavedPage"
import { SettingsPage } from "./pages/SettingsPage"
import { useAppState, validateForm } from "./state/AppState"
import { applyTheme, loadThemePrefs, saveThemePrefs, type ThemePrefs } from "./theme/theme"

type DestId = "plan" | "results" | "saved" | "settings"
const DEST_IDS: DestId[] = ["plan", "results", "saved", "settings"]

const fromHash = (): DestId => {
  const h = window.location.hash.replace("#", "") as DestId
  return DEST_IDS.includes(h) ? h : "plan"
}

export default function App() {
  const s = useAppState()
  const windowClass = useWindowClass()
  const compact = windowClass === "compact"
  const wide = windowClass === "expanded" || windowClass === "large" || windowClass === "extra-large"
  const railExpanded = windowClass === "large" || windowClass === "extra-large"

  // ---- Theme (dynamic color) ----
  const [theme, setTheme] = useState<ThemePrefs>(loadThemePrefs)
  useEffect(() => {
    applyTheme(theme)
    saveThemePrefs(theme)
    if (theme.mode !== "system") return
    const mq = window.matchMedia("(prefers-color-scheme: dark)")
    const onChange = () => applyTheme(theme)
    mq.addEventListener("change", onChange)
    return () => mq.removeEventListener("change", onChange)
  }, [theme])

  // ---- Destinations (hash-based, so the browser/Android back button works) ----
  const [dest, setDest] = useState<DestId>(fromHash)
  useEffect(() => {
    const onHash = () => setDest(fromHash())
    window.addEventListener("hashchange", onHash)
    return () => window.removeEventListener("hashchange", onHash)
  }, [])
  const navigate = useCallback((id: string) => {
    if (window.location.hash !== `#${id}`) window.location.hash = id
    window.scrollTo({ top: 0 })
  }, [])

  const { hasUnseenResults, markResultsSeen } = s
  useEffect(() => {
    if (dest === "results" && hasUnseenResults) markResultsSeen()
  }, [dest, hasUnseenResults, markResultsSeen])

  // Jump to Results as soon as a run starts.
  useEffect(() => {
    if (s.phase === "requirements") navigate("results")
  }, [s.phase, navigate])

  // ---- Scroll state: top app bar tint and extended-FAB collapse ----
  const [scrolled, setScrolled] = useState(false)
  const [fabExtended, setFabExtended] = useState(true)
  useEffect(() => {
    let last = window.scrollY
    const onScroll = () => {
      const y = window.scrollY
      setScrolled(y > 0)
      if (Math.abs(y - last) > 8) {
        setFabExtended(y < last || y < 16)
        last = y
      }
    }
    window.addEventListener("scroll", onScroll, { passive: true })
    return () => window.removeEventListener("scroll", onScroll)
  }, [])

  const onGenerate = useCallback(() => {
    if (s.running) {
      navigate("results")
      return
    }
    if (Object.values(validateForm(s.form)).some(Boolean)) navigate("plan")
    void s.generate()
  }, [s, navigate])

  const destinations: Destination[] = [
    { id: "plan", label: "Plan", icon: "edit_note" },
    {
      id: "results",
      label: "Results",
      icon: "fact_check",
      badge: dest !== "results" && hasUnseenResults ? s.results.courses.length || true : s.running && dest !== "results" ? true : undefined,
    },
    { id: "saved", label: "Saved", icon: "bookmarks" },
    { id: "settings", label: "Settings", icon: "settings" },
  ]

  const showCompactFab = compact && dest === "plan"
  const fabLabel = s.running ? "Generating…" : "Generate plan"

  // Snackbars sit above the navigation bar and FAB on compact windows.
  useEffect(() => {
    const bottom = compact
      ? `calc(var(--app-nav-bar-height) + env(safe-area-inset-bottom) + ${showCompactFab ? 88 : 16}px)`
      : "24px"
    document.documentElement.style.setProperty("--snackbar-bottom", bottom)
  }, [compact, showCompactFab])

  const title = dest === "plan" ? "CourseGuide" : destinations.find((d) => d.id === dest)!.label

  const caseChip = s.caseNumber && dest !== "saved" && (
    <md-assist-chip label={s.caseNumber} onClick={() => navigate("saved")} className="font-mono">
      <md-icon slot="icon">bookmark</md-icon>
    </md-assist-chip>
  )

  const page = (() => {
    switch (dest) {
      case "plan":
        return <PlanPage twoColumn={wide} showInlineSubmit={!compact} onGenerate={onGenerate} />
      case "results":
        return <ResultsPage twoPane={wide} onGoToPlan={() => navigate("plan")} onGoToSaved={() => navigate("saved")} />
      case "saved":
        return <SavedPage twoPane={wide} onLoaded={() => navigate("plan")} />
      case "settings":
        return <SettingsPage twoPane={wide} theme={theme} onTheme={setTheme} />
    }
  })()

  return (
    <div className="min-h-dvh bg-surface">
      {!compact && (
        <NavigationRail
          destinations={destinations}
          active={dest}
          onSelect={navigate}
          expanded={railExpanded}
          header={
            railExpanded ? (
              <div className="flex h-12 items-center gap-3 px-2">
                <img src="/icon.svg" alt="" className="size-8 rounded-sm" />
                <span className="md-typescale-title-large text-on-surface">CourseGuide</span>
              </div>
            ) : (
              <img src="/icon.svg" alt="CourseGuide" className="size-10 rounded-md" />
            )
          }
          fab={
            <md-fab
              variant="primary"
              label={railExpanded ? fabLabel : ""}
              aria-label={fabLabel}
              title={fabLabel}
              onClick={onGenerate}
              lowered
            >
              <md-icon slot="icon">{s.running ? "hourglass_top" : "auto_awesome"}</md-icon>
            </md-fab>
          }
        />
      )}

      <div className={compact ? "" : railExpanded ? "pl-[260px]" : "pl-24"}>
        <TopAppBar title={title} scrolled={scrolled} trailing={caseChip} />
        <main
          className="mx-auto w-full max-w-[1280px] px-[var(--app-margin)] pt-2"
          style={{
            paddingBottom: compact
              ? `calc(var(--app-nav-bar-height) + env(safe-area-inset-bottom) + ${showCompactFab ? 96 : 24}px)`
              : "32px",
          }}
        >
          <div key={dest} className="page-enter">
            {page}
          </div>
        </main>
      </div>

      {showCompactFab && (
        <div
          className="fixed right-4 z-30"
          style={{ bottom: "calc(var(--app-nav-bar-height) + env(safe-area-inset-bottom) + 16px)" }}
        >
          <md-fab variant="primary" label={fabExtended ? fabLabel : ""} aria-label={fabLabel} onClick={onGenerate}>
            <md-icon slot="icon">{s.running ? "hourglass_top" : "auto_awesome"}</md-icon>
          </md-fab>
        </div>
      )}

      {compact && <NavigationBar destinations={destinations} active={dest} onSelect={navigate} />}

      <Dialog
        open={s.syncDialogOpen}
        onClose={() => s.setSyncDialogOpen(false)}
        icon="cloud_sync"
        headline="Sync to online storage?"
        actions={
          <>
            <md-text-button onClick={() => s.setSyncDialogOpen(false)}>Not now</md-text-button>
            <md-text-button onClick={s.syncNow} disabled={s.busy === "sync"}>
              {s.busy === "sync" ? "Syncing…" : "Sync"}
            </md-text-button>
          </>
        }
      >
        You have {s.syncStatus?.unsyncedCount ?? 0} local {s.syncStatus?.unsyncedCount === 1 ? "record" : "records"} that
        aren't in Supabase yet. Syncing uploads them; local copies are kept.
      </Dialog>
    </div>
  )
}
