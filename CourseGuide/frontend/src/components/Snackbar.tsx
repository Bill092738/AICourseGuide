/* eslint-disable react-refresh/only-export-components */
import { createContext, useCallback, useContext, useEffect, useRef, useState, type ReactNode } from "react"

type SnackbarAction = { label: string; onClick: () => void }
type SnackbarItem = { id: number; message: string; action?: SnackbarAction }

const Ctx = createContext<{ showSnackbar: (message: string, action?: SnackbarAction) => void } | null>(null)

/**
 * M3 snackbar: inverse-surface container, extra-small (4dp) corners, inverse-primary action,
 * shown one at a time for 4 seconds (6 seconds with an action).
 * Position is controlled by the --snackbar-bottom variable set by the app shell
 * so it sits above the navigation bar and FAB on compact windows.
 */
export function SnackbarProvider({ children }: { children: ReactNode }) {
  const [item, setItem] = useState<SnackbarItem | null>(null)
  const seq = useRef(0)

  const showSnackbar = useCallback((message: string, action?: SnackbarAction) => {
    setItem({ id: ++seq.current, message, action })
  }, [])

  useEffect(() => {
    if (!item) return
    const t = setTimeout(() => setItem(null), item.action ? 6000 : 4000)
    return () => clearTimeout(t)
  }, [item])

  return (
    <Ctx.Provider value={{ showSnackbar }}>
      {children}
      <div
        aria-live="polite"
        className="pointer-events-none fixed inset-x-0 z-50 flex justify-center px-4 medium:justify-start medium:px-6"
        style={{ bottom: "var(--snackbar-bottom, 16px)" }}
      >
        {item && (
          <div
            key={item.id}
            role="status"
            className="snackbar-enter pointer-events-auto flex min-h-12 w-full max-w-[672px] items-center gap-2 rounded-xs bg-inverse-surface py-1 pl-4 pr-2 text-inverse-on-surface shadow-3 medium:w-auto medium:min-w-[344px]"
          >
            <span className="md-typescale-body-medium flex-1 py-2.5">{item.message}</span>
            {item.action && (
              <md-text-button
                style={{ "--md-text-button-label-text-color": "var(--md-sys-color-inverse-primary)" } as React.CSSProperties}
                onClick={() => {
                  item.action?.onClick()
                  setItem(null)
                }}
              >
                {item.action.label}
              </md-text-button>
            )}
            <md-icon-button
              aria-label="Dismiss"
              style={{ "--md-icon-button-icon-color": "var(--md-sys-color-inverse-on-surface)" } as React.CSSProperties}
              onClick={() => setItem(null)}
            >
              <md-icon>close</md-icon>
            </md-icon-button>
          </div>
        )}
      </div>
    </Ctx.Provider>
  )
}

export function useSnackbar() {
  const v = useContext(Ctx)
  if (!v) throw new Error("useSnackbar must be used inside SnackbarProvider")
  return v
}
