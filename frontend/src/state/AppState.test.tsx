import { act, renderHook, waitFor } from "@testing-library/react"
import type { ReactNode } from "react"
import { describe, expect, it } from "vitest"
import { SnackbarProvider } from "../components/Snackbar"
import { bodyOf, mockFetch } from "../test/fetchMock"
import { AppStateProvider, useAppState } from "./AppState"

const wrapper = ({ children }: { children: ReactNode }) => (
  <SnackbarProvider>
    <AppStateProvider>{children}</AppStateProvider>
  </SnackbarProvider>
)

const COURSES = [
  { courseName: "CSE 2231", creditHours: 4, category: "Core", description: "", prerequisites: ["CSE 2221"] },
  { courseName: "MATH 2568", creditHours: 3, category: "Math", description: "", prerequisites: [] },
]

const backend = () =>
  mockFetch({
    "POST /api/upload-progress": () => ({ body: { status: "stored", progressFileId: "file-1" } }),
    "POST /api/recommendations": () => ({
      body: { recommendations: [], summarySentence: "s", coursePlanXmlPath: "/plans/p.xml", coursePlanAvailable: true },
    }),
    "POST /api/courses/select": () => ({ body: { courses: COURSES, totalCredits: 7, importErrors: ["SQL unavailable; used XML-only fallback selection.", "Course X skipped"] } }),
    "POST /api/progress/save/local": (init) => ({
      body: { status: "saved", caseNumber: JSON.parse(String(init?.body)).caseNumber ?? "NEW00001", mode: "local" },
    }),
    "GET /api/progress/list": () => ({ body: [] }),
  })

async function fillRequired(result: { current: ReturnType<typeof useAppState> }) {
  act(() => {
    result.current.updateField("university", " OSU ")
    result.current.updateField("major", "CS")
    result.current.updateField("maxCreditHour", 12)
    result.current.updateField("completedCourses", ["CSE 2221"])
  })
}

describe("AppState generate flow", () => {
  it("blocks generation and reports field errors when required fields are empty", async () => {
    const fetch = backend()
    const { result } = renderHook(() => useAppState(), { wrapper })

    let started = true
    await act(async () => {
      started = await result.current.generate()
    })

    expect(started).toBe(false)
    expect(result.current.fieldErrors.university).toBeTruthy()
    expect(fetch).not.toHaveBeenCalled()
  })

  it("uploads the PDF, then sends its id and the constraints to the backend", async () => {
    const fetch = backend()
    const { result } = renderHook(() => useAppState(), { wrapper })
    await fillRequired(result)

    await act(async () => {
      await result.current.uploadPdf(new File(["%PDF"], "progress.pdf", { type: "application/pdf" }))
    })
    expect(result.current.upload).toEqual({ status: "done", fileName: "progress.pdf" })

    await act(async () => {
      await result.current.generate()
    })

    expect(bodyOf(fetch, "/api/recommendations")).toMatchObject({ university: "OSU", major: "CS", progressFileId: "file-1" })
    expect(bodyOf(fetch, "/api/courses/select")).toEqual({
      coursePlanXmlPath: "/plans/p.xml",
      maxCredits: 12,
      completedCourses: ["CSE 2221"],
    })
    expect(result.current.phase).toBe("done")
    expect(result.current.results.courses).toHaveLength(2)
    expect(result.current.results.totalCredits).toBe(7)
    expect(result.current.results.importErrors).toEqual(["Course X skipped"])
    expect(result.current.hasUnseenResults).toBe(true)
  })

  it("rejects non-PDF uploads without calling the backend", async () => {
    const fetch = backend()
    const { result } = renderHook(() => useAppState(), { wrapper })

    await act(async () => {
      await result.current.uploadPdf(new File(["hi"], "notes.txt", { type: "text/plain" }))
    })

    expect(result.current.upload.status).toBe("error")
    expect(fetch).not.toHaveBeenCalled()
  })

  it("stops after recommendations when no course plan is produced", async () => {
    const fetch = mockFetch({
      "POST /api/recommendations": () => ({ body: { coursePlanAvailable: false, coursePlanXmlPath: "" } }),
    })
    const { result } = renderHook(() => useAppState(), { wrapper })
    await fillRequired(result)

    await act(async () => {
      await result.current.generate()
    })

    expect(result.current.phase).toBe("done")
    expect(fetch.mock.calls.map(([u]) => String(u))).toEqual(["/api/recommendations"])
    expect(result.current.logs.some((l) => l.level === "warning")).toBe(true)
  })

  it("records an error phase when the backend fails", async () => {
    mockFetch({ "POST /api/recommendations": () => ({ status: 500, body: { message: "LLM unreachable" } }) })
    const { result } = renderHook(() => useAppState(), { wrapper })
    await fillRequired(result)

    await act(async () => {
      await result.current.generate()
    })

    expect(result.current.phase).toBe("error")
    expect(result.current.runError).toBe("LLM unreachable")
  })
})

describe("AppState save and load", () => {
  it("saves the form and results, then restores them by case number", async () => {
    const fetch = backend()
    const { result } = renderHook(() => useAppState(), { wrapper })
    await fillRequired(result)
    await act(async () => {
      await result.current.generate()
    })

    await act(async () => {
      await result.current.saveProgress()
    })
    expect(result.current.caseNumber).toBe("NEW00001")
    const saved = bodyOf(fetch, "/api/progress/save/local")
    expect(JSON.parse(saved.formData)).toMatchObject({ university: " OSU ", completedCourses: ["CSE 2221"] })
    expect(saved.totalCredits).toBe("7")

    // Load it back into a fresh session.
    mockFetch({
      "GET /api/progress/load/NEW00001": () => ({ body: { ...saved, caseNumber: "NEW00001" } }),
    })
    const fresh = renderHook(() => useAppState(), { wrapper }).result
    let ok = false
    await act(async () => {
      ok = await fresh.current.loadProgress(" new00001 ")
    })

    expect(ok).toBe(true)
    expect(fresh.current.caseNumber).toBe("NEW00001")
    expect(fresh.current.form.major).toBe("CS")
    expect(fresh.current.form.completedCourses).toEqual(["CSE 2221"])
    expect(fresh.current.results.courses).toHaveLength(2)
    expect(fresh.current.phase).toBe("done")
  })

  it("reports a missing case number", async () => {
    mockFetch({ "GET /api/progress/load/NOPE0000": () => ({ status: 404, body: {} }) })
    const { result } = renderHook(() => useAppState(), { wrapper })

    let ok = true
    await act(async () => {
      ok = await result.current.loadProgress("NOPE0000")
    })

    expect(ok).toBe(false)
    expect(result.current.caseNumber).toBe("")
  })

  it("switching to online storage checks sync status and offers to sync", async () => {
    mockFetch({
      "GET /api/progress/sync/status": () => ({ body: { unsyncedCount: 2, needsSync: true } }),
      "POST /api/progress/sync": () => ({ body: { status: "synced", totalUnsynced: 2, syncedCount: 2, remaining: 0 } }),
      "GET /api/progress/list": () => ({ body: [] }),
    })
    const { result } = renderHook(() => useAppState(), { wrapper })

    act(() => result.current.setStorageMode("online"))
    await waitFor(() => expect(result.current.syncDialogOpen).toBe(true))
    expect(localStorage.getItem("courseguide-storage-mode")).toBe("online")

    await act(async () => {
      await result.current.syncNow()
    })
    expect(result.current.syncDialogOpen).toBe(false)
    expect(result.current.syncStatus).toEqual({ unsyncedCount: 0, needsSync: false })
  })

  it("persists LLM settings in localStorage", () => {
    const { result } = renderHook(() => useAppState(), { wrapper })

    act(() => result.current.setLlmConfig({ apiBaseUrl: "https://api.example", apiKey: "k", modelName: "m" }))

    expect(JSON.parse(localStorage.getItem("courseguide-api-config") ?? "{}")).toEqual({
      apiBaseUrl: "https://api.example",
      apiKey: "k",
      modelName: "m",
    })
  })
})
