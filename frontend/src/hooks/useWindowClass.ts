import { useSyncExternalStore } from "react"

/** M3 breakpoints (formerly window size classes). */
export type WindowClass = "compact" | "medium" | "expanded" | "large" | "extra-large"

/** Maps a window width in CSS pixels (dp) to its M3 breakpoint. */
export function windowClassFor(w: number): WindowClass {
  if (w < 600) return "compact"
  if (w < 840) return "medium"
  if (w < 1200) return "expanded"
  if (w < 1600) return "large"
  return "extra-large"
}

const current = () => windowClassFor(window.innerWidth)

function subscribe(cb: () => void) {
  window.addEventListener("resize", cb)
  return () => window.removeEventListener("resize", cb)
}

export function useWindowClass(): WindowClass {
  return useSyncExternalStore(subscribe, current, () => "compact")
}
