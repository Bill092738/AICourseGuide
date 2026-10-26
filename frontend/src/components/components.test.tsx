import { act, fireEvent, render, screen } from "@testing-library/react"
import { describe, expect, it, vi } from "vitest"
import { SnackbarProvider, useSnackbar } from "./Snackbar"
import { SegmentedButton, StatusChip } from "./ui"

describe("SegmentedButton", () => {
  const options = [
    { value: "system", label: "System" },
    { value: "light", label: "Light" },
    { value: "dark", label: "Dark" },
  ] as const

  it("exposes a radio group with the selected option checked", () => {
    render(<SegmentedButton label="Theme" value="light" onChange={() => {}} options={[...options]} />)

    expect(screen.getByRole("radiogroup", { name: "Theme" })).toBeTruthy()
    expect(screen.getByRole("radio", { name: "Light" }).getAttribute("aria-checked")).toBe("true")
    expect(screen.getByRole("radio", { name: "Dark" }).getAttribute("aria-checked")).toBe("false")
  })

  it("reports the clicked option", () => {
    const onChange = vi.fn()
    render(<SegmentedButton label="Theme" value="light" onChange={onChange} options={[...options]} />)

    fireEvent.click(screen.getByRole("radio", { name: "Dark" }))

    expect(onChange).toHaveBeenCalledWith("dark")
  })
})

describe("StatusChip", () => {
  it("renders its label", () => {
    render(<StatusChip tone="success">Complete</StatusChip>)
    expect(screen.getByText("Complete")).toBeTruthy()
  })
})

describe("Snackbar", () => {
  function Trigger({ action }: { action?: () => void }) {
    const { showSnackbar } = useSnackbar()
    return (
      <button onClick={() => showSnackbar("Saved", action ? { label: "Undo", onClick: action } : undefined)}>show</button>
    )
  }

  it("shows a message and hides it after 4 seconds", () => {
    vi.useFakeTimers()
    render(
      <SnackbarProvider>
        <Trigger />
      </SnackbarProvider>,
    )

    fireEvent.click(screen.getByText("show"))
    expect(screen.getByRole("status").textContent).toContain("Saved")

    act(() => {
      vi.advanceTimersByTime(4000)
    })
    expect(screen.queryByRole("status")).toBeNull()
    vi.useRealTimers()
  })

  it("runs the action and dismisses", () => {
    const action = vi.fn()
    render(
      <SnackbarProvider>
        <Trigger action={action} />
      </SnackbarProvider>,
    )

    fireEvent.click(screen.getByText("show"))
    fireEvent.click(screen.getByText("Undo"))

    expect(action).toHaveBeenCalledOnce()
    expect(screen.queryByRole("status")).toBeNull()
  })

  it("throws when used outside the provider", () => {
    vi.spyOn(console, "error").mockImplementation(() => {})
    expect(() => render(<Trigger />)).toThrow(/SnackbarProvider/)
  })
})
