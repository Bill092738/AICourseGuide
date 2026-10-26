import { useEffect, useRef, useState, type ReactNode } from "react"

type TextFieldProps = {
  label: string
  value: string
  onValue: (v: string) => void
  type?: "text" | "number" | "password" | "url" | "search"
  supportingText?: string
  errorText?: string
  required?: boolean
  min?: number
  max?: number
  step?: number
  placeholder?: string
  inputMode?: "text" | "numeric" | "decimal" | "url"
  autocomplete?: string
  leadingIcon?: string
  trailing?: ReactNode
  onEnter?: () => void
  className?: string
  disabled?: boolean
}

/** Wrapper around <md-outlined-text-field> with a controlled value. */
export function TextField(props: TextFieldProps) {
  const ref = useRef<HTMLElement & { value: string }>(null)
  const { onValue, onEnter } = props

  // React skips setting a prop that did not change between renders, but the element's own
  // value changes as the user types, so re-sync whenever they diverge (e.g. after clearing).
  useEffect(() => {
    const el = ref.current
    if (el && el.value !== props.value) el.value = props.value
  })

  useEffect(() => {
    const el = ref.current
    if (!el) return
    const onInput = () => onValue(el.value)
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Enter" && onEnter) {
        e.preventDefault()
        onEnter()
      }
    }
    el.addEventListener("input", onInput)
    el.addEventListener("keydown", onKey)
    return () => {
      el.removeEventListener("input", onInput)
      el.removeEventListener("keydown", onKey)
    }
  }, [onValue, onEnter])

  return (
    <md-outlined-text-field
      ref={ref}
      className={props.className}
      label={props.label}
      value={props.value}
      type={props.type ?? "text"}
      supportingText={props.errorText ? "" : (props.supportingText ?? "")}
      errorText={props.errorText ?? ""}
      error={Boolean(props.errorText)}
      required={props.required}
      min={props.min !== undefined ? String(props.min) : undefined}
      max={props.max !== undefined ? String(props.max) : undefined}
      step={props.step !== undefined ? String(props.step) : undefined}
      placeholder={props.placeholder ?? ""}
      inputMode={props.inputMode}
      autocomplete={props.autocomplete ?? ""}
      hasLeadingIcon={Boolean(props.leadingIcon)}
      disabled={props.disabled}
    >
      {props.leadingIcon && <md-icon slot="leading-icon">{props.leadingIcon}</md-icon>}
      {props.trailing}
    </md-outlined-text-field>
  )
}

/** Number field that stores "" when empty, matching the backend payload. */
export function NumberField({
  value,
  onValue,
  ...rest
}: Omit<TextFieldProps, "value" | "onValue" | "type"> & { value: number | ""; onValue: (v: number | "") => void }) {
  return (
    <TextField
      {...rest}
      type="number"
      value={value === "" ? "" : String(value)}
      onValue={(v) => onValue(v === "" ? "" : Number.isFinite(Number(v)) ? Number(v) : "")}
    />
  )
}

/** Wrapper around <md-outlined-select>. */
export function Select({
  label,
  value,
  onValue,
  options,
  supportingText,
}: {
  label: string
  value: string
  onValue: (v: string) => void
  options: { value: string; label: string }[]
  supportingText?: string
}) {
  const ref = useRef<HTMLElement & { value: string }>(null)
  useEffect(() => {
    const el = ref.current
    if (!el) return
    const onChange = () => onValue(el.value)
    el.addEventListener("change", onChange)
    return () => el.removeEventListener("change", onChange)
  }, [onValue])

  return (
    <md-outlined-select ref={ref} label={label} value={value} supportingText={supportingText ?? ""} menuPositioning="fixed">
      {options.map((o) => (
        <md-select-option key={o.value} value={o.value} selected={o.value === value}>
          <div slot="headline">{o.label}</div>
        </md-select-option>
      ))}
    </md-outlined-select>
  )
}

/**
 * Text field that turns entries into removable M3 input chips
 * (Enter or comma adds; the chip's remove button deletes).
 */
export function ChipInput({
  label,
  values,
  onValues,
  supportingText,
}: {
  label: string
  values: string[]
  onValues: (v: string[]) => void
  supportingText?: string
}) {
  const [draft, setDraft] = useState("")

  const add = (raw: string) => {
    const parts = raw
      .split(",")
      .map((s) => s.trim())
      .filter(Boolean)
    if (!parts.length) return
    const next = [...values]
    for (const p of parts) if (!next.some((v) => v.toLowerCase() === p.toLowerCase())) next.push(p)
    onValues(next)
    setDraft("")
  }

  return (
    <div className="flex flex-col gap-2">
      <TextField
        label={label}
        value={draft}
        onValue={(v) => (v.includes(",") ? add(v) : setDraft(v))}
        onEnter={() => add(draft)}
        supportingText={supportingText}
        trailing={
          draft.trim() ? (
            <md-icon-button slot="trailing-icon" aria-label="Add" onClick={() => add(draft)}>
              <md-icon>add</md-icon>
            </md-icon-button>
          ) : undefined
        }
      />
      {values.length > 0 && (
        <md-chip-set>
          {values.map((v) => (
            <RemovableChip key={v} label={v} onRemove={() => onValues(values.filter((x) => x !== v))} />
          ))}
        </md-chip-set>
      )}
    </div>
  )
}

function RemovableChip({ label, onRemove }: { label: string; onRemove: () => void }) {
  const ref = useRef<HTMLElement>(null)
  useEffect(() => {
    const el = ref.current
    if (!el) return
    const handler = (e: Event) => {
      // The chip removes itself from the DOM by default; React owns the DOM, so cancel and update state.
      e.preventDefault()
      onRemove()
    }
    el.addEventListener("remove", handler)
    return () => el.removeEventListener("remove", handler)
  }, [onRemove])
  return <md-input-chip ref={ref} label={label} />
}
