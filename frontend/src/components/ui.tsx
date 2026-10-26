import type { ReactNode } from "react"

export function Icon({ name, filled, className = "" }: { name: string; filled?: boolean; className?: string }) {
  return (
    <span aria-hidden="true" className={`material-symbol ${filled ? "filled" : ""} ${className}`}>
      {name}
    </span>
  )
}

/**
 * M3 card used for page sections. Variants follow the card spec:
 * filled = surface-container-highest, outlined = surface + outline-variant, elevated = surface-container-low + level 1.
 * Corner radius is the medium (12dp) shape token.
 */
export function Card({
  variant = "filled",
  className = "",
  children,
}: {
  variant?: "filled" | "outlined" | "elevated"
  className?: string
  children: ReactNode
}) {
  const v =
    variant === "outlined"
      ? "bg-surface border border-outline-variant"
      : variant === "elevated"
        ? "bg-surface-container-low shadow-1"
        : "bg-surface-container-low"
  return <section className={`rounded-md ${v} ${className}`}>{children}</section>
}

/** Card with an icon + title header, used to group form fields. */
export function SectionCard({
  icon,
  title,
  subtitle,
  action,
  children,
  className = "",
}: {
  icon: string
  title: string
  subtitle?: string
  action?: ReactNode
  children: ReactNode
  className?: string
}) {
  return (
    <Card className={`p-4 medium:p-6 ${className}`}>
      <header className="mb-4 flex items-start gap-4">
        <span className="flex size-10 shrink-0 items-center justify-center rounded-full bg-secondary-container text-on-secondary-container">
          <Icon name={icon} />
        </span>
        <div className="min-w-0 flex-1">
          <h2 className="md-typescale-title-large text-on-surface">{title}</h2>
          {subtitle && <p className="md-typescale-body-medium mt-0.5 text-on-surface-variant">{subtitle}</p>}
        </div>
        {action}
      </header>
      <div className="flex flex-col gap-4">{children}</div>
    </Card>
  )
}

/** Empty state: icon, headline, body, optional action. */
export function EmptyState({ icon, title, body, action }: { icon: string; title: string; body: string; action?: ReactNode }) {
  return (
    <div className="flex flex-col items-center px-6 py-12 text-center">
      <span className="mb-4 flex size-20 items-center justify-center rounded-full bg-surface-container-highest text-on-surface-variant">
        <Icon name={icon} className="!text-[40px]" />
      </span>
      <h2 className="md-typescale-title-large text-on-surface">{title}</h2>
      <p className="md-typescale-body-medium mt-2 max-w-sm text-on-surface-variant">{body}</p>
      {action && <div className="mt-6">{action}</div>}
    </div>
  )
}

/**
 * M3 segmented button (single-select). @material/web has this only in labs,
 * so it is built from tokens: 40dp height, full corner, outline border,
 * selected segment = secondary-container with a check icon.
 */
export function SegmentedButton<T extends string>({
  value,
  onChange,
  options,
  label,
}: {
  value: T
  onChange: (v: T) => void
  options: { value: T; label: string; icon?: string }[]
  label: string
}) {
  return (
    <div role="radiogroup" aria-label={label} className="flex h-10 w-full overflow-hidden rounded-full border border-outline">
      {options.map((o, i) => {
        const selected = o.value === value
        return (
          <button
            key={o.value}
            type="button"
            role="radio"
            aria-checked={selected}
            onClick={() => onChange(o.value)}
            className={`md-typescale-label-large relative flex min-w-0 flex-1 items-center justify-center gap-2 px-3 outline-none transition-colors duration-200 ${
              i > 0 ? "border-l border-outline" : ""
            } ${selected ? "bg-secondary-container text-on-secondary-container" : "text-on-surface"}`}
          >
            <md-ripple />
            <md-focus-ring inward />
            {selected ? <Icon name="check" className="!text-[18px]" /> : o.icon && <Icon name={o.icon} className="!text-[18px]" />}
            <span className="truncate">{o.label}</span>
          </button>
        )
      })}
    </div>
  )
}

/** Inline status pill built on M3 container roles. */
export function StatusChip({ tone, icon, children }: { tone: "neutral" | "success" | "error" | "primary"; icon?: string; children: ReactNode }) {
  const tones = {
    neutral: "bg-surface-container-highest text-on-surface-variant",
    success: "bg-tertiary-container text-on-tertiary-container",
    error: "bg-error-container text-on-error-container",
    primary: "bg-primary-container text-on-primary-container",
  }
  return (
    <span className={`md-typescale-label-medium inline-flex h-6 items-center gap-1 rounded-sm px-2 ${tones[tone]}`}>
      {icon && <Icon name={icon} className="!text-[16px]" />}
      {children}
    </span>
  )
}
