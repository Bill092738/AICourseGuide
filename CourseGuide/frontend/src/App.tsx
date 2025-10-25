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
  [k: string]: unknown
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
  }

  const submit = async () => {
    setStatus("Loading...")
    setResults([])
    try {
      // Send the full form; backend can ignore fields it doesn't need.
      const payload = {
        university: form.university,
        major: form.major,
        degreeLevel: form.degreeLevel,
        graduationYear: form.graduationYear === "" ? null : form.graduationYear,
        targetMajor: form.targetMajor,
        targetMinor: form.targetMinor,
        planName: form.planName,
        preferredElectives: form.preferredElectives,
        maxCreditHour: form.maxCreditHour === "" ? null : form.maxCreditHour,
        semester: form.semester,
        // Old script expected { major, gpa } — keep these too.
        gpa: form.gpa === "" ? 0 : form.gpa,
      }

      const res = await fetch("/api/recommendations", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload),
      })

      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      const data: RecommendationsResponse = await res.json()
      setResults(data.recommendations ?? [])
      setStatus("")
    } catch (err) {
      setStatus("Error contacting backend")
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
                           hover:file:bg-blue-700 file:cursor-pointer"
              />
              {form.progressPdf && (
                <span className="text-sm text-gray-600">{form.progressPdf.name}</span>
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
      </div>
    </div>
  )
}
