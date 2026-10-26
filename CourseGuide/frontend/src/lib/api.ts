/** Typed client for the CourseGuide Spring Boot backend (proxied at /api by Vite). */

export type DegreeLevel = "" | "Undergraduate" | "Graduate"

export type LlmConfig = {
  apiBaseUrl: string
  apiKey: string
  modelName: string
}

export type StorageMode = "local" | "online"

export type RecommendationsRequest = {
  university: string
  major: string
  degreeLevel: DegreeLevel
  graduationYear: number | ""
  targetMajor: string
  targetMinor: string
  planName: string
  preferredElectives: string
  semester: string
  gpa: number | ""
  progressFileId?: string
  llmConfig: Partial<LlmConfig>
}

export type RecommendationsResponse = {
  recommendations?: string[]
  summarySentence?: string
  coursePlanXmlPath?: string
  coursePlanAvailable?: boolean
}

export type SelectedCourse = {
  courseName: string
  creditHours: number
  category: string
  description: string
  prerequisites: string[]
}

export type SelectResponse = {
  courses?: SelectedCourse[]
  totalCredits?: number
  importSuccess?: number
  importErrors?: string[]
}

export type UserProgress = {
  caseNumber?: string
  formData?: string
  xmlContent?: string
  selectedCourses?: string
  totalCredits?: string
  createdAt?: string
  updatedAt?: string
  syncedToCloud?: boolean
}

export type SaveResponse = { status: string; caseNumber: string; mode: StorageMode; synced?: boolean }
export type SyncStatus = { unsyncedCount: number; needsSync: boolean }
export type SyncResult = { status: string; totalUnsynced: number; syncedCount: number; remaining: number }

export class ApiError extends Error {
  status: number
  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(path, init)
  if (!res.ok) {
    let message = `HTTP ${res.status}`
    try {
      const body = await res.json()
      if (body && typeof body.message === "string" && body.message) message = body.message
    } catch {
      /* body was not JSON */
    }
    throw new ApiError(res.status, message)
  }
  return (await res.json()) as T
}

const json = (body: unknown): RequestInit => ({
  method: "POST",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify(body),
})

export const api = {
  health: () => request<{ status: string }>("/api/health"),

  uploadProgress: (file: File) => {
    const fd = new FormData()
    fd.append("progressPdf", file)
    return request<{ status: string; progressFileId: string }>("/api/upload-progress", { method: "POST", body: fd })
  },

  recommendations: (body: RecommendationsRequest) => request<RecommendationsResponse>("/api/recommendations", json(body)),

  selectCourses: (coursePlanXmlPath: string, maxCredits: number, completedCourses: string[]) =>
    request<SelectResponse>("/api/courses/select", json({ coursePlanXmlPath, maxCredits, completedCourses })),

  /** URL of a PDF snapshot of the top search result for a query (opened in a new tab). */
  snapshotSearchUrl: (query: string) => `/api/snapshot/pdf/search?query=${encodeURIComponent(query)}`,

  newCaseNumber: () => request<{ caseNumber: string }>("/api/progress/case-number", { method: "POST" }),

  saveProgress: (mode: StorageMode, progress: UserProgress) =>
    request<SaveResponse>(mode === "online" ? "/api/progress/save/online" : "/api/progress/save/local", json(progress)),

  loadProgress: (caseNumber: string, mode: StorageMode) =>
    request<UserProgress>(`/api/progress/load/${encodeURIComponent(caseNumber)}?mode=${mode}`),

  listProgress: () => request<UserProgress[]>("/api/progress/list"),

  deleteProgress: (caseNumber: string) =>
    request<{ status: string; caseNumber: string }>(`/api/progress/delete/${encodeURIComponent(caseNumber)}`, {
      method: "DELETE",
    }),

  syncStatus: () => request<SyncStatus>("/api/progress/sync/status"),

  sync: () => request<SyncResult>("/api/progress/sync", { method: "POST" }),
}

export const errorMessage = (err: unknown) => (err instanceof Error ? err.message : "Unknown error")
