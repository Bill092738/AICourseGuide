import { useId, type ReactNode } from "react"
import { Icon } from "./ui"

export type Destination = {
  id: string
  label: string
  icon: string
  badge?: number | boolean
}

function Badge({ value }: { value: number | boolean | undefined }) {
  if (!value) return null
  // Small badge (6dp dot) or large badge (16dp, label-small), using the error role per M3 badge spec.
  if (value === true) return <span className="absolute right-3.5 top-0.5 size-1.5 rounded-full bg-error" aria-hidden="true" />
  return (
    <span className="md-typescale-label-small absolute left-[calc(50%+4px)] -top-0.5 flex h-4 min-w-4 items-center justify-center rounded-full bg-error px-1 text-on-error">
      {value > 999 ? "999+" : value}
    </span>
  )
}

const badgeLabel = (b: Destination["badge"]) => (typeof b === "number" ? `, ${b} new` : b ? ", new" : "")

/**
 * Vertical navigation item: the whole item is the touch target, while the state layer
 * (ripple) and focus ring are drawn on the 56x32dp active indicator, as specified for M3 nav items.
 */
function VerticalItem({
  d,
  selected,
  onSelect,
  className,
}: {
  d: Destination
  selected: boolean
  onSelect: (id: string) => void
  className: string
}) {
  const id = "nav-" + useId().replace(/[^a-zA-Z0-9_-]/g, "")
  return (
    <button
      id={id}
      type="button"
      aria-current={selected ? "page" : undefined}
      aria-label={d.label + badgeLabel(d.badge)}
      onClick={() => onSelect(d.id)}
      className={`relative flex w-full flex-col items-center justify-center gap-1 outline-none ${className}`}
    >
      <span
        className={`relative flex h-8 w-14 items-center justify-center rounded-full transition-colors duration-200 ease-standard ${
          selected ? "bg-secondary-container text-on-secondary-container" : "text-on-surface-variant"
        }`}
      >
        <md-ripple for={id} />
        <md-focus-ring for={id} />
        <Icon name={d.icon} filled={selected} />
        <Badge value={d.badge} />
      </span>
      <span className={`md-typescale-label-medium ${selected ? "text-secondary" : "text-on-surface-variant"}`}>{d.label}</span>
    </button>
  )
}

/**
 * Flexible navigation bar (compact windows): 64dp tall, surface-container,
 * vertical items with a 56x32dp secondary-container active indicator.
 */
export function NavigationBar({
  destinations,
  active,
  onSelect,
}: {
  destinations: Destination[]
  active: string
  onSelect: (id: string) => void
}) {
  return (
    <nav
      aria-label="Main"
      className="fixed inset-x-0 bottom-0 z-30 bg-surface-container"
      style={{ paddingBottom: "env(safe-area-inset-bottom)" }}
    >
      <ul className="flex h-[var(--app-nav-bar-height)]">
        {destinations.map((d) => {
          const selected = d.id === active
          return (
            <li key={d.id} className="flex-1">
              <VerticalItem d={d} selected={selected} onSelect={onSelect} className="h-full" />
            </li>
          )
        })}
      </ul>
    </nav>
  )
}

/**
 * Navigation rail for medium and larger windows.
 * - collapsed (medium/expanded): 96dp wide, vertical items, FAB at the top.
 * - expanded (large+): horizontal items with a full-width 56dp indicator and an extended FAB.
 */
export function NavigationRail({
  destinations,
  active,
  onSelect,
  expanded,
  fab,
  header,
}: {
  destinations: Destination[]
  active: string
  onSelect: (id: string) => void
  expanded: boolean
  fab?: ReactNode
  header?: ReactNode
}) {
  return (
    <nav
      aria-label="Main"
      className={`fixed inset-y-0 left-0 z-30 flex flex-col bg-surface ${expanded ? "w-[260px] px-3" : "w-24 items-center"}`}
      style={{ paddingTop: "max(env(safe-area-inset-top), 0px)" }}
    >
      <div className={`flex flex-col gap-4 pb-6 pt-11 ${expanded ? "items-start px-2" : "items-center"}`}>
        {header}
        {fab}
      </div>
      <ul className={`flex flex-col ${expanded ? "gap-0" : "gap-1"} w-full`}>
        {destinations.map((d) => {
          const selected = d.id === active
          if (expanded) {
            return (
              <li key={d.id}>
                <button
                  type="button"
                  aria-current={selected ? "page" : undefined}
                  aria-label={d.label + badgeLabel(d.badge)}
                  onClick={() => onSelect(d.id)}
                  className={`md-typescale-label-large relative flex h-14 w-full items-center gap-3 rounded-full px-4 outline-none transition-colors duration-200 ease-standard ${
                    selected ? "bg-secondary-container text-on-secondary-container" : "text-on-surface-variant"
                  }`}
                >
                  <md-ripple />
                  <md-focus-ring />
                  <Icon name={d.icon} filled={selected} />
                  <span className="flex-1 text-left">{d.label}</span>
                  {typeof d.badge === "number" && d.badge > 0 && <span className="md-typescale-label-large">{d.badge}</span>}
                  {d.badge === true && <span className="size-1.5 rounded-full bg-error" aria-hidden="true" />}
                </button>
              </li>
            )
          }
          return (
            <li key={d.id} className="w-full">
              <VerticalItem d={d} selected={selected} onSelect={onSelect} className="h-16" />
            </li>
          )
        })}
      </ul>
    </nav>
  )
}

/** Small top app bar: 64dp, surface; switches to surface-container when content scrolls under it. */
export function TopAppBar({
  title,
  scrolled,
  leading,
  trailing,
}: {
  title: string
  scrolled: boolean
  leading?: ReactNode
  trailing?: ReactNode
}) {
  return (
    <header
      className={`sticky top-0 z-20 transition-colors duration-200 ease-standard ${scrolled ? "bg-surface-container" : "bg-surface"}`}
      style={{ paddingTop: "env(safe-area-inset-top)" }}
    >
      <div className="flex h-16 items-center gap-1 px-1 medium:px-3">
        {leading ?? <span className="w-3" />}
        <h1 className="md-typescale-title-large min-w-0 flex-1 truncate px-1 text-on-surface">{title}</h1>
        <div className="flex items-center gap-1 pr-1">{trailing}</div>
      </div>
    </header>
  )
}
