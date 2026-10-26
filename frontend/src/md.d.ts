import type React from "react"

/**
 * JSX typings for the @material/web custom elements used in the app.
 * React 19 assigns props as element properties when the property exists,
 * so camelCase property names (supportingText, hasIcon, ...) are used.
 */
type Base = React.DetailedHTMLProps<React.HTMLAttributes<HTMLElement>, HTMLElement> & {
  class?: string
  slot?: string
  disabled?: boolean
  /** `for` attribute used by md-ripple / md-focus-ring to attach to another control by id. */
  for?: string
}

type ButtonProps = Base & {
  hasIcon?: boolean
  trailingIcon?: boolean
  href?: string
  target?: string
  type?: "button" | "submit" | "reset"
  value?: string
  form?: string
}

type IconButtonProps = Base & {
  href?: string
  target?: string
  toggle?: boolean
  selected?: boolean
  type?: "button" | "submit" | "reset"
}

type FabProps = Base & {
  label?: string
  size?: "small" | "medium" | "large"
  variant?: "surface" | "primary" | "secondary" | "tertiary"
  lowered?: boolean
}

type TextFieldProps = Base & {
  label?: string
  value?: string
  type?: string
  placeholder?: string
  supportingText?: string
  errorText?: string
  error?: boolean
  required?: boolean
  min?: string
  max?: string
  step?: string
  autocomplete?: string
  prefixText?: string
  suffixText?: string
  hasLeadingIcon?: boolean
  readOnly?: boolean
  name?: string
}

type SelectProps = Base & {
  label?: string
  value?: string
  required?: boolean
  supportingText?: string
  errorText?: string
  error?: boolean
  menuPositioning?: "absolute" | "fixed" | "popover"
}

type SelectOptionProps = Base & { value?: string; selected?: boolean }
type DialogProps = Base & { open?: boolean; type?: "alert" }
type ProgressProps = Base & { value?: number; max?: number; indeterminate?: boolean; buffer?: number }
type ChipProps = Base & { label?: string; removeOnly?: boolean; elevated?: boolean; href?: string }
type ListItemProps = Base & { type?: "text" | "button" | "link"; href?: string; target?: string }
type FocusRingProps = Base & { inward?: boolean }

declare module "react" {
  namespace JSX {
    interface IntrinsicElements {
      "md-filled-button": ButtonProps
      "md-filled-tonal-button": ButtonProps
      "md-outlined-button": ButtonProps
      "md-text-button": ButtonProps
      "md-icon-button": IconButtonProps
      "md-filled-tonal-icon-button": IconButtonProps
      "md-fab": FabProps
      "md-icon": Base
      "md-outlined-text-field": TextFieldProps
      "md-outlined-select": SelectProps
      "md-select-option": SelectOptionProps
      "md-dialog": DialogProps
      "md-linear-progress": ProgressProps
      "md-circular-progress": ProgressProps
      "md-chip-set": Base
      "md-input-chip": ChipProps
      "md-assist-chip": ChipProps
      "md-list": Base
      "md-list-item": ListItemProps
      "md-divider": Base & { inset?: boolean }
      "md-ripple": Base
      "md-focus-ring": FocusRingProps
      "md-elevation": Base
    }
  }
}
