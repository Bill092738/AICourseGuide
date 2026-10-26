/* eslint-disable react-refresh/only-export-components */
import { createContext, useCallback, useContext, useEffect, useRef, useState, type ReactNode } from "react"
import {
  api,
  errorMessage,
  type DegreeLevel,
  type LlmConfig,
  type SelectedCourse,
  type StorageMode,
  type SyncStatus,
  type UserProgress,
} from "../lib/api"
import { useSnackbar } from "../components/Snackbar"

export type FormState = {
  university: string
  major: string
  degreeLevel: DegreeLevel
  graduationYear: number | ""
  targetMajor: string
  targetMinor: string
  planName: string
  preferredElectives: string
  maxCreditHour: number | ""
  semester: string
  gpa: number | ""
  completedCourses: string[]
  /** Server-side ID of the uploaded progress PDF (from /api/upload-progress). */
  progressFileId: string
  progressFileName: string
}

export const EMPTY_FORM: FormState = {
  university: "",
  major: "",
  degreeLevel: "",
  graduationYear: "",
  targetMajor: "",
  targetMinor: "",
  planName: "",
  preferredElectives: "",
  maxCreditHour: "",
  semester: "",
  gpa: "",
  completedCourses: [],
  progressFileId: "",
  progressFileName: "",
}

export type UploadState =
  | { status: "idle" }
  | { status: "uploading"; fileName: string }
  | { status: "done"; fileName: string }
  | { status: "error"; fileName: string; message: string }

/** Real phases of the generate flow, mapped to the two backend calls. */
export type RunPhase = "idle" | "requirements" | "selecting" | "done" | "error"

export type LogEntry = { time: string; message: string; level: "info" | "success" | "warning" | "error" }

export type Results = {
  summarySentence: string
  recommendations: string[]
  courses: SelectedCourse[]
  totalCredits: number
  maxCredits: number
  importErrors: string[]
  coursePlanXmlPath: string
  coursePlanAvailable: boolean
}

const EMPTY_RESULTS: Results = {
  summarySentence: "",
  recommendations: [],
  courses: [],
  totalCredits: 0,
  maxCredits: 18,
  importErrors: [],
  coursePlanXmlPath: "",
  coursePlanAvailable: false,
}

export type FieldErrors = Partial<Record<keyof FormState, string>>

const LLM_KEY = "courseguide-api-config"
const MODE_KEY = "courseguide-storage-mode"
const EMPTY_LLM: LlmConfig = { apiBaseUrl: "", apiKey: "", modelName: "" }

function loadLlmConfig(): LlmConfig {
  try {
    const raw = localStorage.getItem(LLM_KEY)
    return raw ? { ...EMPTY_LLM, ...JSON.parse(raw) } : EMPTY_LLM
  } catch {
    return EMPTY_LLM
  }
}

function toNum(v: unknown): number | "" {
  if (v === "" || v === null || v === undefined) return ""
  const n = Number(v)
  return Number.isFinite(n) ? n : ""
}

/** Accepts form JSON saved by this app or by the previous frontend. */
function parseSavedForm(raw: string): FormState {
  const p = JSON.parse(raw) as Record<string, unknown>
  const str = (k: string) => (typeof p[k] === "string" ? (p[k] as string) : "")
  const level = str("degreeLevel")
  return {
    ...EMPTY_FORM,
    university: str("university"),
    major: str("major"),
    degreeLevel: level === "Undergraduate" || level === "Graduate" ? level : "",
    graduationYear: toNum(p.graduationYear),
    targetMajor: str("targetMajor"),
    targetMinor: str("targetMinor"),
    planName: str("planName"),
    preferredElectives: str("preferredElectives"),
    maxCreditHour: toNum(p.maxCreditHour),
    semester: str("semester"),
    gpa: toNum(p.gpa),
    completedCourses: Array.isArray(p.completedCourses) ? p.completedCourses.filter((c) => typeof c === "string") : [],
    progressFileId: str("progressFileId"),
    progressFileName: str("progressFileName"),
  }
}

/** Import errors about the SQL fallback are internal details, not user-facing problems. */
const isUserFacingError = (e: string) => !/sql|fallback|database|jdbc|connection/i.test(e)

export function validateForm(f: FormState): FieldErrors {
  const errors: FieldErrors = {}
  if (!f.university.trim()) errors.university = "Enter your university"
  if (!f.major.trim()) errors.major = "Enter your current major"
  if (f.graduationYear !== "" && (f.graduationYear < 1900 || f.graduationYear > 2100)) errors.graduationYear = "Use a year between 1900 and 2100"
  if (f.gpa !== "" && (f.gpa < 0 || f.gpa > 4)) errors.gpa = "GPA must be between 0 and 4"
  if (f.maxCreditHour !== "" && (f.maxCreditHour < 1 || f.maxCreditHour > 30)) errors.maxCreditHour = "Use 1 to 30 credit hours"
  return errors
}

function useAppStateValue() {
  const { showSnackbar } = useSnackbar()

  const [form, setForm] = useState<FormState>(EMPTY_FORM)
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({})
  const [upload, setUpload] = useState<UploadState>({ status: "idle" })
  const [phase, setPhase] = useState<RunPhase>("idle")
  const [runError, setRunError] = useState("")
  const [results, setResults] = useState<Results>(EMPTY_RESULTS)
  const [hasUnseenResults, setHasUnseenResults] = useState(false)
  const [logs, setLogs] = useState<LogEntry[]>([])

  const [llmConfig, setLlmConfig] = useState<LlmConfig>(loadLlmConfig)
  const [storageMode, setStorageModeState] = useState<StorageMode>(() =>
    localStorage.getItem(MODE_KEY) === "online" ? "online" : "local",
  )
  const [caseNumber, setCaseNumber] = useState("")
  const [syncStatus, setSyncStatus] = useState<SyncStatus | null>(null)
  const [syncDialogOpen, setSyncDialogOpen] = useState(false)
  const [savedList, setSavedList] = useState<UserProgress[] | null>(null)
  const [savedListError, setSavedListError] = useState("")
  const [busy, setBusy] = useState<"" | "save" | "load" | "sync" | "list" | "delete">("")

  useEffect(() => {
    localStorage.setItem(LLM_KEY, JSON.stringify(llmConfig))
  }, [llmConfig])

  const log = useCallback((message: string, level: LogEntry["level"] = "info") => {
    setLogs((prev) => [...prev, { time: new Date().toLocaleTimeString(), message, level }])
  }, [])

  const updateField = useCallback(<K extends keyof FormState>(key: K, value: FormState[K]) => {
    setForm((s) => ({ ...s, [key]: value }))
    setFieldErrors((e) => (e[key] ? { ...e, [key]: undefined } : e))
  }, [])

  // ---- Progress PDF upload ----
  const uploadToken = useRef(0)
  const uploadPdf = useCallback(
    async (file: File) => {
      const token = ++uploadToken.current
      if (!file.name.toLowerCase().endsWith(".pdf") && file.type !== "application/pdf") {
        setUpload({ status: "error", fileName: file.name, message: "Only PDF files are supported" })
        return
      }
      setUpload({ status: "uploading", fileName: file.name })
      try {
        const res = await api.uploadProgress(file)
        if (token !== uploadToken.current) return
        setForm((s) => ({ ...s, progressFileId: res.progressFileId, progressFileName: file.name }))
        setUpload({ status: "done", fileName: file.name })
        log(`Uploaded progress PDF "${file.name}"`, "success")
      } catch (err) {
        if (token !== uploadToken.current) return
        setUpload({ status: "error", fileName: file.name, message: errorMessage(err) })
        log(`Progress PDF upload failed: ${errorMessage(err)}`, "error")
      }
    },
    [log],
  )

  const clearPdf = useCallback(() => {
    uploadToken.current++
    setUpload({ status: "idle" })
    setForm((s) => ({ ...s, progressFileId: "", progressFileName: "" }))
  }, [])

  // ---- Generate plan ----
  const running = phase === "requirements" || phase === "selecting"

  const generate = useCallback(async (): Promise<boolean> => {
    if (running) return false
    const errors = validateForm(form)
    setFieldErrors(errors)
    if (Object.values(errors).some(Boolean)) {
      showSnackbar("Fix the highlighted fields to continue")
      return false
    }
    if (upload.status === "uploading") {
      showSnackbar("Wait for the progress PDF to finish uploading")
      return false
    }

    setLogs([])
    setResults(EMPTY_RESULTS)
    setRunError("")
    setPhase("requirements")
    const maxCredits = form.maxCreditHour === "" || form.maxCreditHour <= 0 ? 18 : form.maxCreditHour

    try {
      log(`Building plan for ${form.major} at ${form.university}`)
      log(form.progressFileId ? `Including progress PDF "${form.progressFileName}"` : "No progress PDF attached")
      log("Finding degree requirements and analyzing them with the LLM. This can take a few minutes.")

      const rec = await api.recommendations({
        university: form.university.trim(),
        major: form.major.trim(),
        degreeLevel: form.degreeLevel,
        graduationYear: form.graduationYear,
        targetMajor: form.targetMajor.trim(),
        targetMinor: form.targetMinor.trim(),
        planName: form.planName.trim(),
        preferredElectives: form.preferredElectives.trim(),
        semester: form.semester.trim(),
        gpa: form.gpa,
        progressFileId: form.progressFileId || undefined,
        llmConfig: {
          apiBaseUrl: llmConfig.apiBaseUrl || undefined,
          apiKey: llmConfig.apiKey || undefined,
          modelName: llmConfig.modelName || undefined,
        },
      })

      const base: Results = {
        ...EMPTY_RESULTS,
        summarySentence: rec.summarySentence ?? "",
        recommendations: rec.recommendations ?? [],
        coursePlanXmlPath: rec.coursePlanXmlPath ?? "",
        coursePlanAvailable: Boolean(rec.coursePlanAvailable && rec.coursePlanXmlPath),
        maxCredits,
      }
      setResults(base)
      if (rec.summarySentence) log(`Requirements query: ${rec.summarySentence}`)

      if (!base.coursePlanAvailable) {
        log("No course plan was produced. The requirements page or LLM may be unavailable.", "warning")
        setPhase("done")
        setHasUnseenResults(true)
        return true
      }

      log(`Course plan generated: ${base.coursePlanXmlPath}`, "success")
      setPhase("selecting")
      log(`Selecting courses within ${maxCredits} credit hours`)

      const sel = await api.selectCourses(base.coursePlanXmlPath, maxCredits, form.completedCourses)
      const courses = sel.courses ?? []
      setResults({
        ...base,
        courses,
        totalCredits: sel.totalCredits ?? 0,
        importErrors: (sel.importErrors ?? []).filter(isUserFacingError),
      })
      log(`Selected ${courses.length} courses (${sel.totalCredits ?? 0} credits)`, "success")
      setPhase("done")
      setHasUnseenResults(true)
      return true
    } catch (err) {
      const msg = errorMessage(err)
      setRunError(msg)
      setPhase("error")
      log(`Error: ${msg}`, "error")
      showSnackbar(`Plan generation failed: ${msg}`)
      return true
    }
  }, [running, form, upload.status, llmConfig, log, showSnackbar])

  // ---- Save / load ----
  const refreshSavedList = useCallback(async () => {
    setBusy("list")
    setSavedListError("")
    try {
      setSavedList(await api.listProgress())
    } catch (err) {
      setSavedListError(errorMessage(err))
      setSavedList([])
    } finally {
      setBusy("")
    }
  }, [])

  const checkSyncStatus = useCallback(async (promptIfNeeded: boolean) => {
    try {
      const status = await api.syncStatus()
      setSyncStatus(status)
      if (promptIfNeeded && status.needsSync) setSyncDialogOpen(true)
    } catch (err) {
      log(`Could not check sync status: ${errorMessage(err)}`, "warning")
    }
  }, [log])

  const setStorageMode = useCallback(
    (mode: StorageMode) => {
      setStorageModeState(mode)
      localStorage.setItem(MODE_KEY, mode)
      if (mode === "online") void checkSyncStatus(true)
    },
    [checkSyncStatus],
  )

  // Check sync status once on startup when online mode was remembered.
  const startupChecked = useRef(false)
  useEffect(() => {
    if (startupChecked.current) return
    startupChecked.current = true
    if (storageMode === "online") void checkSyncStatus(true)
  }, [storageMode, checkSyncStatus])

  const generateCaseNumber = useCallback(async () => {
    try {
      const { caseNumber: cn } = await api.newCaseNumber()
      setCaseNumber(cn)
      showSnackbar(`New case number ${cn}`)
    } catch (err) {
      showSnackbar(`Could not generate a case number: ${errorMessage(err)}`)
    }
  }, [showSnackbar])

  const saveProgress = useCallback(async () => {
    setBusy("save")
    try {
      const res = await api.saveProgress(storageMode, {
        caseNumber: caseNumber || undefined,
        formData: JSON.stringify(form),
        xmlContent: "",
        selectedCourses: JSON.stringify(results.courses),
        totalCredits: String(results.totalCredits),
      })
      setCaseNumber(res.caseNumber)
      const where = res.mode === "online" ? (res.synced === false ? "locally (cloud sync pending)" : "online") : "locally"
      showSnackbar(`Saved ${where} as ${res.caseNumber}`)
      log(`Progress saved as ${res.caseNumber} (${res.mode})`, "success")
      if (storageMode === "online") void checkSyncStatus(false)
      void refreshSavedList()
    } catch (err) {
      showSnackbar(`Save failed: ${errorMessage(err)}`)
    } finally {
      setBusy("")
    }
  }, [storageMode, caseNumber, form, results.courses, results.totalCredits, showSnackbar, log, checkSyncStatus, refreshSavedList])

  const loadProgress = useCallback(
    async (cn: string): Promise<boolean> => {
      const id = cn.trim().toUpperCase()
      if (!id) {
        showSnackbar("Enter a case number")
        return false
      }
      setBusy("load")
      try {
        const p = await api.loadProgress(id, storageMode)
        if (p.formData) {
          const loaded = parseSavedForm(p.formData)
          setForm(loaded)
          setUpload(loaded.progressFileId ? { status: "done", fileName: loaded.progressFileName || "Uploaded PDF" } : { status: "idle" })
        }
        const courses: SelectedCourse[] = p.selectedCourses ? JSON.parse(p.selectedCourses) : []
        setResults({
          ...EMPTY_RESULTS,
          courses: Array.isArray(courses) ? courses : [],
          totalCredits: Number(p.totalCredits) || 0,
          coursePlanAvailable: Array.isArray(courses) && courses.length > 0,
        })
        setPhase(Array.isArray(courses) && courses.length > 0 ? "done" : "idle")
        setFieldErrors({})
        setCaseNumber(p.caseNumber || id)
        showSnackbar(`Loaded ${p.caseNumber || id}`)
        log(`Progress loaded for ${p.caseNumber || id}`, "success")
        return true
      } catch (err) {
        const notFound = err instanceof Error && "status" in err && (err as { status: number }).status === 404
        showSnackbar(notFound ? `No saved progress for ${id}` : `Load failed: ${errorMessage(err)}`)
        return false
      } finally {
        setBusy("")
      }
    },
    [storageMode, showSnackbar, log],
  )

  const deleteProgress = useCallback(
    async (cn: string) => {
      setBusy("delete")
      try {
        await api.deleteProgress(cn)
        if (cn === caseNumber) setCaseNumber("")
        showSnackbar(`Deleted ${cn}`)
        void refreshSavedList()
      } catch (err) {
        showSnackbar(`Delete failed: ${errorMessage(err)}`)
      } finally {
        setBusy("")
      }
    },
    [caseNumber, showSnackbar, refreshSavedList],
  )

  const syncNow = useCallback(async () => {
    setBusy("sync")
    try {
      const res = await api.sync()
      setSyncStatus({ unsyncedCount: res.remaining, needsSync: res.remaining > 0 })
      setSyncDialogOpen(false)
      showSnackbar(
        res.remaining > 0 ? `Synced ${res.syncedCount}, ${res.remaining} still pending` : `Synced ${res.syncedCount} records to the cloud`,
      )
      log(`Synced ${res.syncedCount} records to online storage`, "success")
      void refreshSavedList()
    } catch (err) {
      showSnackbar(`Sync failed: ${errorMessage(err)}`)
    } finally {
      setBusy("")
    }
  }, [showSnackbar, log, refreshSavedList])

  const resetForm = useCallback(() => {
    clearPdf()
    setForm(EMPTY_FORM)
    setFieldErrors({})
  }, [clearPdf])

  return {
    form, updateField, fieldErrors, resetForm,
    upload, uploadPdf, clearPdf,
    phase, running, runError, results, generate, logs, clearLogs: () => setLogs([]),
    hasUnseenResults, markResultsSeen: () => setHasUnseenResults(false),
    llmConfig, setLlmConfig, resetLlmConfig: () => setLlmConfig(EMPTY_LLM),
    storageMode, setStorageMode, caseNumber, setCaseNumber, generateCaseNumber,
    saveProgress, loadProgress, deleteProgress,
    savedList, savedListError, refreshSavedList,
    syncStatus, checkSyncStatus, syncNow, syncDialogOpen, setSyncDialogOpen,
    busy,
  }
}

export type AppState = ReturnType<typeof useAppStateValue>

const Ctx = createContext<AppState | null>(null)

export function AppStateProvider({ children }: { children: ReactNode }) {
  const value = useAppStateValue()
  return <Ctx.Provider value={value}>{children}</Ctx.Provider>
}

export function useAppState() {
  const v = useContext(Ctx)
  if (!v) throw new Error("useAppState must be used inside AppStateProvider")
  return v
}
