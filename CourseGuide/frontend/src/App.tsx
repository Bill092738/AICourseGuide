import { useState } from "react"
import './App.css'

type DegreeLevel = "" | "Undergraduate" | "Graduate"

type FormState = {
  university: string
  major: string
  degreeLevel: DegreeLevel
  graduationYear: number | ""
  progressPdf: File | null
  targetMajor: string
  targetMinor: string
  planName: string
  preferredElectives: string
  maxCreditHour: number | ""
  semester: string
  gpa: number | ""
}

type RecommendationsResponse = {
  recommendations?: string[]
  coursePlanCsvPath?: string
  coursePlanAvailable?: boolean
  [k: string]: unknown
}

type SelectedCourse = {
  courseName: string
  creditHours: number
  category: string
  description: string
  prerequisites: string[]
}

type SelectResponse = {
  courses: SelectedCourse[]
  totalCredits: number
  importSuccess: number
  importErrors: string[]
}

export default function App() {
  const [form, setForm] = useState<FormState>({
    university: "",
    major: "",
    degreeLevel: "",
    graduationYear: "",
    progressPdf: null,
    targetMajor: "",
    targetMinor: "",
    planName: "",
    preferredElectives: "",
    maxCreditHour: "",
    semester: "",
    gpa: "",
  })
  const [results, setResults] = useState<string[]>([])
  const [status, setStatus] = useState<string>("")
  const [uploadProgress, setUploadProgress] = useState<string>("")
  const [isUploading, setIsUploading] = useState(false)
  const [selected, setSelected] = useState<SelectedCourse[]>([])
  const [totalCredits, setTotalCredits] = useState<number>(0)
  const [importErrors, setImportErrors] = useState<string[]>([])

  const onChange =
    <K extends keyof FormState>(key: K) =>
    (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => {
      const val = e.target.value
      if (key === "graduationYear" || key === "maxCreditHour" || key === "gpa") {
        setForm((s) => ({ ...s, [key]: val === "" ? "" : Number(val) }))
      } else {
        setForm((s) => ({ ...s, [key]: val }))
      }
    }

  const onFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0] ?? null
    setForm((s) => ({ ...s, progressPdf: file }))
    setUploadProgress("") // Clear previous upload status
  }

  // New: Manual upload function with confirmation
  const uploadPdfNow = async () => {
    if (!form.progressPdf) {
      setUploadProgress("No file selected")
      return
    }

    setIsUploading(true)
    setUploadProgress("Uploading...")

    try {
      const formData = new FormData()
      formData.append("progressPdf", form.progressPdf)

      const uploadRes = await fetch("/api/upload-progress", {
        method: "POST",
        body: formData,
      })

      if (!uploadRes.ok) {
        throw new Error(`Upload failed: HTTP ${uploadRes.status}`)
      }

      const uploadData = await uploadRes.json()
      const fileId = uploadData.progressFileId || ""
      
      console.log("PDF uploaded with ID:", fileId)
      setUploadProgress(`✓ Uploaded successfully (ID: ${fileId.substring(0, 8)}...)`)
    } catch (err) {
      const message = err instanceof Error ? err.message : "Unknown error"
      setUploadProgress(`✗ Upload failed: ${message}`)
      console.error("Upload error:", err)
    } finally {
      setIsUploading(false)
    }
  }

  const submit = async () => {
    setStatus("Loading...")
    setResults([])
    setSelected([])
    setImportErrors([])
    setTotalCredits(0)
    try {
      // Assume your existing call gets CSV path back:
      const res = await fetch("/api/recommendations", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          university: form.university,
          major: form.major,
          degreeLevel: form.degreeLevel,
          graduationYear: form.graduationYear,
          targetMajor: form.targetMajor,
          targetMinor: form.targetMinor,
          planName: form.planName,
          preferredElectives: form.preferredElectives,
          semester: form.semester,
          gpa: form.gpa,
        }),
      })
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      const data: RecommendationsResponse = await res.json()
      setResults(data.recommendations ?? [])

      if (data.coursePlanAvailable && data.coursePlanCsvPath) {
        const max = form.maxCreditHour === "" || Number(form.maxCreditHour) <= 0 ? 18 : Number(form.maxCreditHour)
        const selectRes = await fetch("/api/courses/select", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            coursePlanCsvPath: data.coursePlanCsvPath,
            maxCredits: max,
            completedCourses: [] as string[],
          }),
        })
        if (!selectRes.ok) throw new Error(`Select HTTP ${selectRes.status}`)
        const sel: SelectResponse = await selectRes.json()
        setSelected(sel.courses ?? [])
        setTotalCredits(sel.totalCredits ?? 0)
        setImportErrors(sel.importErrors ?? [])
      }

      setStatus("Complete!")
    } catch (err) {
      const message = err instanceof Error ? err.message : "Unknown error"
      setStatus(`Error: ${message}`)
      console.error(err)
    }
  }

  return (
    <div className="page">
      <div className="container">
        <header className="mb8">
          <h1 className="h1">CourseGuide</h1>
          <p className="muted">Get simple recommendations based on your profile and goals.</p>
        </header>

        <section className="card">
          <h2 className="h2">User basic info</h2>
          <div className="field">
            <label htmlFor="university">University</label>
            <input
              id="university"
              type="text"
              placeholder='e.g., "The Ohio State University"'
              value={form.university}
              onChange={onChange("university")}
            />
          </div>
          <div className="field">
            <label htmlFor="major">Major</label>
            <input
              id="major"
              type="text"
              placeholder="e.g., Computer Science"
              value={form.major}
              onChange={onChange("major")}
            />
          </div>
          <div className="grid2">
            <div className="field">
              <label htmlFor="degreeLevel">Degree level</label>
              <select
                id="degreeLevel"
                value={form.degreeLevel}
                onChange={onChange("degreeLevel")}
              >
                <option value="" disabled>
                  Select
                </option>
                <option value="Undergraduate">Undergraduate</option>
                <option value="Graduate">Graduate</option>
              </select>
            </div>
            <div className="field">
              <label htmlFor="graduationYear">Graduation year</label>
              <input
                id="graduationYear"
                type="number"
                min={1900}
                max={2100}
                placeholder="e.g., 2027"
                value={form.graduationYear === "" ? "" : form.graduationYear}
                onChange={onChange("graduationYear")}
              />
            </div>
          </div>
        </section>

        <section className="card mt6">
          <h2 className="h2">Current progress</h2>
          <p className="muted">
            Upload a single PDF that includes: current courses, completed courses, and any transfer credits.
          </p>
          <div className="field">
            <label htmlFor="progressPdf">Progress PDF</label>
            <div className="flex flex-col gap-3">
              <div className="flex items-center gap-3">
                <input
                  id="progressPdf"
                  type="file"
                  accept="application/pdf"
                  onChange={onFileChange}
                  className="border-0 bg-transparent p-0 text-sm text-gray-900
                             file:inline-flex file:items-center file:justify-center
                             file:rounded-md file:border-0 file:bg-blue-600
                             file:px-4 file:py-2 file:text-white
                             hover:file:bg-blue-700 file:cursor-pointer
                             disabled:file:opacity-50 disabled:file:cursor-not-allowed"
                  disabled={isUploading}
                />
                {form.progressPdf && (
                  <span className="text-sm text-gray-600">{form.progressPdf.name}</span>
                )}
              </div>
              
              {form.progressPdf && (
                <div className="flex items-center gap-3">
                  <button
                    type="button"
                    onClick={uploadPdfNow}
                    disabled={isUploading}
                    className="inline-flex items-center justify-center rounded-md border border-gray-300 bg-white px-4 py-2 text-sm font-medium text-gray-700 shadow-sm hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed"
                  >
                    {isUploading ? (
                      <>
                        <svg className="animate-spin -ml-1 mr-2 h-4 w-4 text-gray-700" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                          <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
                          <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                        </svg>
                        Uploading...
                      </>
                    ) : (
                      "Upload Now"
                    )}
                  </button>
                  
                  {uploadProgress && (
                    <span className={`text-sm ${uploadProgress.startsWith('✓') ? 'text-green-600' : uploadProgress.startsWith('✗') ? 'text-red-600' : 'text-blue-600'}`}>
                      {uploadProgress}
                    </span>
                  )}
                </div>
              )}
            </div>
          </div>
        </section>

        <section className="card mt6">
          <h2 className="h2">Target or goal</h2>
          <div className="field">
            <label htmlFor="targetMajor">Target major</label>
            <input
              id="targetMajor"
              type="text"
              placeholder="e.g., Computer & Information Science"
              value={form.targetMajor}
              onChange={onChange("targetMajor")}
            />
          </div>
          <div className="field">
            <label htmlFor="targetMinor">Target minor (optional)</label>
            <input
              id="targetMinor"
              type="text"
              placeholder="e.g., Mathematics"
              value={form.targetMinor}
              onChange={onChange("targetMinor")}
            />
          </div>
          <div className="field">
            <label htmlFor="planName">Plan name</label>
            <input
              id="planName"
              type="text"
              placeholder='e.g., "BS CIS 2023-2024"'
              value={form.planName}
              onChange={onChange("planName")}
            />
          </div>
          <div className="field">
            <label htmlFor="preferredElectives">Preferred electives (comma-separated)</label>
            <input
              id="preferredElectives"
              type="text"
              placeholder='e.g., AI, Data Science, Systems'
              value={form.preferredElectives}
              onChange={onChange("preferredElectives")}
            />
          </div>

          <fieldset className="fieldset">
            <legend>Constraints</legend>
            <div className="grid2">
              <div className="field">
                <label htmlFor="maxCreditHour">Max credit hours</label>
                <input
                  id="maxCreditHour"
                  type="number"
                  min={1}
                  max={30}
                  placeholder="e.g., 18"
                  value={form.maxCreditHour === "" ? "" : form.maxCreditHour}
                  onChange={onChange("maxCreditHour")}
                />
              </div>
              <div className="field">
                <label htmlFor="semester">Semester</label>
                <input
                  id="semester"
                  type="text"
                  placeholder='e.g., "Spring 2026"'
                  value={form.semester}
                  onChange={onChange("semester")}
                />
              </div>
            </div>
          </fieldset>

          <div className="grid2">
            <div className="field">
              <label htmlFor="gpa">GPA</label>
              <input
                id="gpa"
                type="number"
                min={0}
                max={4}
                step={0.01}
                placeholder="e.g., 3.7"
                value={form.gpa === "" ? "" : form.gpa}
                onChange={onChange("gpa")}
              />
            </div>
            <div />
          </div>

          <div className="actions">
            <button
              type="button"
              onClick={submit}
              className="inline-flex items-center rounded-md bg-blue-600 px-4 py-2 text-white hover:bg-blue-700"
            >
              Get Recommendations
            </button>
            <a
              href="/api/health"
              target="_blank"
              rel="noreferrer"
              className="text-blue-600 underline"
            >
              Health
            </a>
          </div>
          {status && <p className="status">{status}</p>}
        </section>

        <section className="mt6">
          <h2 className="h2 mb2">Recommendations</h2>
          <ul className="list">
            {results.map((item, idx) => (
              <li key={idx}>{item}</li>
            ))}
          </ul>
        </section>

        {selected.length > 0 && (
          <section className="mt-6 p-4 bg-white shadow rounded">
            <h2 className="text-xl font-semibold mb-2">Selected Courses (max 6)</h2>
            <p className="text-sm text-gray-600 mb-2">Total Credits: {totalCredits}</p>
            {importErrors.length > 0 && (
              <div className="mb-3">
                <p className="text-red-600 text-sm">Import/selection notes:</p>
                <ul className="list-disc ml-5 text-sm text-red-700">
                  {importErrors.map((e, i) => <li key={i}>{e}</li>)}
                </ul>
              </div>
            )}
            <div className="overflow-x-auto">
              <table className="min-w-full text-sm">
                <thead>
                  <tr className="text-left border-b">
                    <th className="py-2 pr-4">Course</th>
                    <th className="py-2 pr-4">Credits</th>
                    <th className="py-2 pr-4">Category</th>
                    <th className="py-2 pr-4">Prerequisites</th>
                    <th className="py-2 pr-4">Description</th>
                  </tr>
                </thead>
                <tbody>
                  {selected.map((c, i) => (
                    <tr key={i} className="border-b">
                      <td className="py-2 pr-4 font-medium">{c.courseName}</td>
                      <td className="py-2 pr-4">{c.creditHours}</td>
                      <td className="py-2 pr-4">{c.category}</td>
                      <td className="py-2 pr-4">{c.prerequisites?.join(", ") || "—"}</td>
                      <td className="py-2 pr-4">{c.description || "—"}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </section>
        )}
      </div>
    </div>
  )
}
