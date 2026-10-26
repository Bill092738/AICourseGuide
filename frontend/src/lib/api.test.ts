import { describe, expect, it, vi } from "vitest"
import { api, ApiError, errorMessage } from "./api"
import { bodyOf, mockFetch } from "../test/fetchMock"

describe("api client", () => {
  it("posts JSON to /api/recommendations", async () => {
    const fetch = mockFetch({ "POST /api/recommendations": () => ({ body: { coursePlanAvailable: false } }) })

    await api.recommendations({
      university: "OSU",
      major: "CS",
      degreeLevel: "",
      graduationYear: 2027,
      targetMajor: "",
      targetMinor: "",
      planName: "",
      preferredElectives: "",
      semester: "",
      gpa: "",
      progressFileId: "abc",
      llmConfig: {},
    })

    const [, init] = fetch.mock.calls[0]
    expect((init?.headers as Record<string, string>)["Content-Type"]).toBe("application/json")
    expect(bodyOf(fetch, "/api/recommendations")).toMatchObject({ university: "OSU", progressFileId: "abc" })
  })

  it("uploads the progress PDF as multipart form data", async () => {
    const fetch = mockFetch({ "POST /api/upload-progress": () => ({ body: { status: "stored", progressFileId: "id1" } }) })

    const res = await api.uploadProgress(new File(["%PDF"], "p.pdf", { type: "application/pdf" }))

    expect(res.progressFileId).toBe("id1")
    const body = fetch.mock.calls[0][1]?.body as FormData
    expect(body).toBeInstanceOf(FormData)
    expect((body.get("progressPdf") as File).name).toBe("p.pdf")
  })

  it("sends course selection constraints", async () => {
    const fetch = mockFetch({ "POST /api/courses/select": () => ({ body: { courses: [] } }) })

    await api.selectCourses("/plans/p.xml", 15, ["CSE 2221"])

    expect(bodyOf(fetch, "/api/courses/select")).toEqual({
      coursePlanXmlPath: "/plans/p.xml",
      maxCredits: 15,
      completedCourses: ["CSE 2221"],
    })
  })

  it("picks the save endpoint by storage mode", async () => {
    const fetch = mockFetch({
      "POST /api/progress/save/local": () => ({ body: { caseNumber: "L", mode: "local" } }),
      "POST /api/progress/save/online": () => ({ body: { caseNumber: "O", mode: "online" } }),
    })

    expect((await api.saveProgress("local", {})).caseNumber).toBe("L")
    expect((await api.saveProgress("online", {})).caseNumber).toBe("O")
    expect(fetch).toHaveBeenCalledTimes(2)
  })

  it("encodes path and query parameters", async () => {
    const fetch = mockFetch({
      "GET /api/progress/load/A%2FB": () => ({ body: {} }),
      "DELETE /api/progress/delete/A%20B": () => ({ body: {} }),
    })

    await api.loadProgress("A/B", "online")
    await api.deleteProgress("A B")

    expect(String(fetch.mock.calls[0][0])).toBe("/api/progress/load/A%2FB?mode=online")
    expect(fetch.mock.calls[1][1]?.method).toBe("DELETE")
    expect(api.snapshotSearchUrl("OSU CS & more")).toBe("/api/snapshot/pdf/search?query=OSU%20CS%20%26%20more")
  })

  it("throws ApiError with the server message on failure", async () => {
    mockFetch({ "GET /api/progress/load/X": () => ({ status: 404, body: { message: "No progress found" } }) })

    const err = await api.loadProgress("X", "local").catch((e: unknown) => e)

    expect(err).toBeInstanceOf(ApiError)
    expect((err as ApiError).status).toBe(404)
    expect((err as ApiError).message).toBe("No progress found")
  })

  it("falls back to the HTTP status when the error body is not JSON", async () => {
    vi.stubGlobal("fetch", async () => new Response("<html>oops</html>", { status: 500 }))

    await expect(api.health()).rejects.toMatchObject({ status: 500, message: "HTTP 500" })
  })

  it("errorMessage handles non-Error values", () => {
    expect(errorMessage(new Error("boom"))).toBe("boom")
    expect(errorMessage("string")).toBe("Unknown error")
  })
})
