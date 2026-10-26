import { useEffect, useRef, type ReactNode } from "react"

/** Basic M3 dialog (md-dialog) controlled by `open`; onClose fires for scrim, Escape, and actions. */
export function Dialog({
  open,
  onClose,
  icon,
  headline,
  children,
  actions,
}: {
  open: boolean
  onClose: () => void
  icon?: string
  headline: string
  children: ReactNode
  actions: ReactNode
}) {
  const ref = useRef<HTMLElement & { open: boolean }>(null)

  useEffect(() => {
    const el = ref.current
    if (!el) return
    const handler = () => onClose()
    el.addEventListener("close", handler)
    return () => el.removeEventListener("close", handler)
  }, [onClose])

  return (
    <md-dialog ref={ref} open={open}>
      {icon && <md-icon slot="icon">{icon}</md-icon>}
      <div slot="headline">{headline}</div>
      <div slot="content" className="md-typescale-body-medium text-on-surface-variant">
        {children}
      </div>
      <div slot="actions">{actions}</div>
    </md-dialog>
  )
}
