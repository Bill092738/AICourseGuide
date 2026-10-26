import { act, renderHook } from "@testing-library/react"
import { describe, expect, it } from "vitest"
import { useWindowClass, windowClassFor } from "./useWindowClass"

describe("windowClassFor", () => {
  it.each([
    [360, "compact"],
    [599, "compact"],
    [600, "medium"],
    [839, "medium"],
    [840, "expanded"],
    [1199, "expanded"],
    [1200, "large"],
    [1599, "large"],
    [1600, "extra-large"],
  ] as const)("%i dp is %s", (width, expected) => {
    expect(windowClassFor(width)).toBe(expected)
  })
})

describe("useWindowClass", () => {
  it("updates when the window is resized", () => {
    window.innerWidth = 412
    const { result } = renderHook(() => useWindowClass())
    expect(result.current).toBe("compact")

    act(() => {
      window.innerWidth = 1440
      window.dispatchEvent(new Event("resize"))
    })

    expect(result.current).toBe("large")
  })
})
