import { useState, useEffect } from "react"
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
  coursePlanXmlPath?: string
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

type ApiConfig = {
  apiBaseUrl: string
  apiKey: string
  modelName: string
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
  // Add terminal logs state
  const [terminalLogs, setTerminalLogs] = useState<string[]>([])
  const [isAnalyzing, setIsAnalyzing] = useState(false)

  const [apiConfig, setApiConfig] = useState<ApiConfig>(() => {
    try {
      const saved = localStorage.getItem('courseguide-api-config')
      return saved ? JSON.parse(saved) : { apiBaseUrl: '', apiKey: '', modelName: '' }
    } catch {
      return { apiBaseUrl: '', apiKey: '', modelName: '' }
    }
  })
  const [showSettings, setShowSettings] = useState(false)

  useEffect(() => {
    localStorage.setItem('courseguide-api-config', JSON.stringify(apiConfig))
  }, [apiConfig])

  // Helper function to add terminal log
  const addLog = (message: string) => {
    const timestamp = new Date().toLocaleTimeString()
    setTerminalLogs(prev => [...prev, `[${timestamp}] ${message}`])
  }

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
    setTerminalLogs([]) // Clear previous logs
    setIsAnalyzing(false)
    
    try {
      addLog("🚀 Starting recommendation process...")
      addLog("---- Debug: Student Info ----")
      addLog(`targetMajor: ${form.targetMajor}`)
      addLog(`degreeLevel: ${form.degreeLevel}`)
      addLog(`preferredElectives: ${form.preferredElectives}`)
      addLog(`major: ${form.major}`)
      addLog(`university: ${form.university}`)
      addLog(`targetMinor: ${form.targetMinor}`)
      addLog(`planName: ${form.planName}`)
      addLog(`gpa: ${form.gpa}`)
      addLog(`graduationYear: ${form.graduationYear}`)
      addLog(`semester: ${form.semester}`)
      addLog("---- End Student Info ----")
      
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
          llmConfig: {
            apiBaseUrl: apiConfig.apiBaseUrl || undefined,
            apiKey: apiConfig.apiKey || undefined,
            modelName: apiConfig.modelName || undefined,
          },
        }),
      })
      
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      
      addLog("---- Generated Sentence ----")
      addLog(`The ${form.graduationYear} year graduate requirement of ${form.major} at ${form.university}`)
      addLog("---- End Generated Sentence ----")
      addLog("🔍 Searching for degree requirements...")
      addLog("Creating Playwright instance...")
      addLog("Launching browser...")
      addLog("Navigating to URL...")
      addLog("Dismissing cookie banners and overlays...")
      addLog("Expanding page content...")
      addLog("Generating PDF...")
      
      const data: RecommendationsResponse = await res.json()
      setResults(data.recommendations ?? [])

      if (data.coursePlanAvailable && data.coursePlanXmlPath) {
        addLog("---- PDF generation completed successfully ----")
        addLog("---- Triggering LLM Analysis ----")
        setIsAnalyzing(true) // Start spinner animation
        addLog("---- Starting LLM Analysis ----")
        addLog("Extracting text from Degree Requirements PDF...")
        addLog("Degree Requirements text extracted: ~17000 characters")
        addLog("Prompt length (chars): ~2700, approx tokens: ~680")
        addLog("---- Calling Llama API ----")
        const apiUrl = apiConfig.apiBaseUrl
          ? apiConfig.apiBaseUrl.replace(/\/+$/, '') + '/v1/chat/completions'
          : 'http://localhost:8075/v1/chat/completions'
        addLog(`API URL: ${apiUrl}`)
        
        const max = form.maxCreditHour === "" || Number(form.maxCreditHour) <= 0 ? 18 : Number(form.maxCreditHour)
        const selectRes = await fetch("/api/courses/select", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            coursePlanXmlPath: data.coursePlanXmlPath,
            maxCredits: max,
            completedCourses: [] as string[],
          }),
        })
        
        setIsAnalyzing(false) // Stop spinner animation
        
        if (!selectRes.ok) throw new Error(`Select HTTP ${selectRes.status}`)
        
        const sel: SelectResponse = await selectRes.json()
        setSelected(sel.courses ?? [])
        setTotalCredits(sel.totalCredits ?? 0)
        // Filter out SQL fallback messages and JDBC connection errors
        const userFriendlyErrors = (sel.importErrors ?? []).filter(err => 
          !err.toLowerCase().includes('sql') && 
          !err.toLowerCase().includes('fallback') &&
          !err.toLowerCase().includes('database') &&
          !err.toLowerCase().includes('jdbc') &&
          !err.toLowerCase().includes('connection')
        )
        setImportErrors(userFriendlyErrors)
        
        addLog("---- Processing LLM Response for XML ----")
        addLog(`Extracted XML content: ${sel.courses?.length ?? 0} courses`)
        addLog("---- LLM Analysis Complete ----")
        addLog(`XML saved to: ${data.coursePlanXmlPath}`)
        addLog("---- End ----")
        addLog(`✨ Selected ${sel.courses?.length ?? 0} courses (${sel.totalCredits ?? 0} credits)`)
      } else {
        addLog("⚠️ No course plan available")
      }

      addLog("🎉 Process completed successfully!")
      setStatus("Complete!")
    } catch (err) {
      setIsAnalyzing(false)
      const message = err instanceof Error ? err.message : "Unknown error"
      addLog(`❌ Error: ${message}`)
      setStatus(`Error: ${message}`)
      console.error(err)
    }
  }

  const spinnerFrames = ['/', '—', '\\', '|']

  const workflowSteps = [
    { step: 1, title: "Student Profile", description: "Collect university, major, degree level, graduation year, and uploaded progress PDF." },
    { step: 2, title: "Web Search", description: "Search DuckDuckGo for the university's degree requirements page." },
    { step: 3, title: "Page Scraping", description: "Use Playwright to load the page, dismiss overlays, and render it to a PDF snapshot." },
    { step: 4, title: "PDF Text Extraction", description: "Extract text from degree requirements and student progress PDFs." },
    { step: 5, title: "LLM Analysis", description: "Send text to a local Llama model which generates an XML course plan." },
    { step: 6, title: "Course Selection", description: "Parse the XML, build a prerequisite graph, and select up to 6 courses within credit limits." },
  ]

  function WorkflowPanel() {
    const [open, setOpen] = useState(false)

    return (
      <section className="mb-6">
        <button
          onClick={() => setOpen(!open)}
          className="w-full text-left bg-white border border-gray-200 rounded-lg p-4 shadow-sm flex items-center justify-between hover:bg-gray-50"
        >
          <h2 className="text-lg font-semibold text-gray-700">How it works — AI Workflow</h2>
          <span className="text-gray-400 text-xl">{open ? '\u2212' : '+'}</span>
        </button>
        {open && (
          <div className="bg-white border border-t-0 border-gray-200 rounded-b-lg p-4">
            <ol className="space-y-4">
              {workflowSteps.map((s) => (
                <li key={s.step} className="flex gap-3">
                  <span className="flex-shrink-0 w-7 h-7 rounded-full bg-blue-600 text-white text-sm font-bold flex items-center justify-center">
                    {s.step}
                  </span>
                  <div>
                    <h3 className="font-medium text-gray-900">{s.title}</h3>
                    <p className="text-sm text-gray-500">{s.description}</p>
                  </div>
                </li>
              ))}
            </ol>
          </div>
        )}
      </section>
    )
  }

  function Spinner() {
    const [frame, setFrame] = useState(0)

    useEffect(() => {
      const interval = setInterval(() => {
        setFrame(prev => (prev + 1) % spinnerFrames.length)
      }, 150)
      return () => clearInterval(interval)
    }, [])

    return <span className="inline-block w-4">{spinnerFrames[frame]}</span>
  }

  function SettingsModal() {
    if (!showSettings) return null

    return (
      <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
        <div className="bg-white rounded-lg shadow-xl w-full max-w-md mx-4 p-6">
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-lg font-semibold">LLM API Settings</h2>
            <button onClick={() => setShowSettings(false)} className="text-gray-400 hover:text-gray-600 text-xl">&times;</button>
          </div>

          <p className="text-xs text-gray-500 mb-4">
            Configure your own LLM provider. Uses OpenAI-compatible API format.
            Leave all fields empty to use the default local server.
          </p>

          <div className="mb-3">
            <label className="block text-sm font-medium text-gray-700 mb-1">API Base URL</label>
            <input
              type="text"
              placeholder="e.g., http://localhost:8075 or https://api.openai.com/v1"
              value={apiConfig.apiBaseUrl}
              onChange={(e) => setApiConfig({ ...apiConfig, apiBaseUrl: e.target.value })}
              className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm"
            />
            <p className="text-xs text-gray-400 mt-1">Leave empty for default (localhost:8075)</p>
          </div>

          <div className="mb-3">
            <label className="block text-sm font-medium text-gray-700 mb-1">API Key</label>
            <input
              type="password"
              placeholder="Enter your API key"
              value={apiConfig.apiKey}
              onChange={(e) => setApiConfig({ ...apiConfig, apiKey: e.target.value })}
              className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm"
            />
            <p className="text-xs text-gray-400 mt-1">Stored in your browser only. Never sent to our server.</p>
          </div>

          <div className="mb-4">
            <label className="block text-sm font-medium text-gray-700 mb-1">Model Name (optional)</label>
            <input
              type="text"
              placeholder="e.g., gpt-4o, llama-3.3-70b-versatile"
              value={apiConfig.modelName}
              onChange={(e) => setApiConfig({ ...apiConfig, modelName: e.target.value })}
              className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm"
            />
            <p className="text-xs text-gray-400 mt-1">Leave empty to auto-discover from API</p>
          </div>

          <div className="flex gap-3 justify-end">
            <button
              onClick={() => { setApiConfig({ apiBaseUrl: '', apiKey: '', modelName: '' }); localStorage.removeItem('courseguide-api-config') }}
              className="px-4 py-2 text-sm text-gray-600 hover:text-gray-800"
            >
              Reset to Default
            </button>
            <button
              onClick={() => setShowSettings(false)}
              className="px-4 py-2 bg-blue-600 text-white rounded-md text-sm hover:bg-blue-700"
            >
              Done
            </button>
          </div>
        </div>
      </div>
    )
  }

  return (
    <div className="bg-gray-50 text-gray-900 min-h-screen">
      <div className="max-w-3xl mx-auto px-6 py-6">
        <header className="mb-8 flex items-start justify-between">
          <div>
            <h1 className="text-3xl font-bold">CourseGuide</h1>
            <p className="text-gray-500">Get simple recommendations based on your profile and goals.</p>
          </div>
          <button
            onClick={() => setShowSettings(true)}
            className="mt-1 p-2 text-gray-400 hover:text-gray-600 hover:bg-gray-100 rounded-lg"
            title="LLM API Settings"
          >
            <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.066 2.573c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.573 1.066c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.066-2.573c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z" />
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
            </svg>
          </button>
        </header>

        <WorkflowPanel />

        <section className="bg-white border border-gray-200 rounded-lg p-4 shadow-sm">
          <h2 className="text-xl font-semibold mb-3">User basic info</h2>
          <div className="mb-3">
            <label htmlFor="university" className="block text-sm font-medium text-gray-700 mb-1">University</label>
            <input
              id="university"
              type="text"
              placeholder='e.g., "The Ohio State University"'
              value={form.university}
              onChange={onChange("university")}
              className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm"
            />
          </div>
          <div className="mb-3">
            <label htmlFor="major" className="block text-sm font-medium text-gray-700 mb-1">Major</label>
            <input
              id="major"
              type="text"
              placeholder="e.g., Computer Science"
              value={form.major}
              onChange={onChange("major")}
              className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm"
            />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div className="mb-3">
              <label htmlFor="degreeLevel" className="block text-sm font-medium text-gray-700 mb-1">Degree level</label>
              <select
                id="degreeLevel"
                value={form.degreeLevel}
                onChange={onChange("degreeLevel")}
                className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm"
              >
                <option value="" disabled>
                  Select
                </option>
                <option value="Undergraduate">Undergraduate</option>
                <option value="Graduate">Graduate</option>
              </select>
            </div>
            <div className="mb-3">
              <label htmlFor="graduationYear" className="block text-sm font-medium text-gray-700 mb-1">Graduation year</label>
              <input
                id="graduationYear"
                type="number"
                min={1900}
                max={2100}
                placeholder="e.g., 2027"
                value={form.graduationYear === "" ? "" : form.graduationYear}
                onChange={onChange("graduationYear")}
                className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm"
              />
            </div>
          </div>
        </section>

        <section className="bg-white border border-gray-200 rounded-lg p-4 shadow-sm mt-6">
          <h2 className="text-xl font-semibold mb-3">Current progress</h2>
          <p className="text-gray-500">
            Upload a single PDF that includes: current courses, completed courses, and any transfer credits.
          </p>
          <div className="mb-3">
            <label htmlFor="progressPdf" className="block text-sm font-medium text-gray-700 mb-1">Progress PDF</label>
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

        <section className="bg-white border border-gray-200 rounded-lg p-4 shadow-sm mt-6">
          <h2 className="text-xl font-semibold mb-3">Target or goal</h2>
          <div className="mb-3">
            <label htmlFor="targetMajor" className="block text-sm font-medium text-gray-700 mb-1">Target major</label>
            <input
              id="targetMajor"
              type="text"
              placeholder="e.g., Computer & Information Science"
              value={form.targetMajor}
              onChange={onChange("targetMajor")}
              className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm"
            />
          </div>
          <div className="mb-3">
            <label htmlFor="targetMinor" className="block text-sm font-medium text-gray-700 mb-1">Target minor (optional)</label>
            <input
              id="targetMinor"
              type="text"
              placeholder="e.g., Mathematics"
              value={form.targetMinor}
              onChange={onChange("targetMinor")}
              className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm"
            />
          </div>
          <div className="mb-3">
            <label htmlFor="planName" className="block text-sm font-medium text-gray-700 mb-1">Plan name</label>
            <input
              id="planName"
              type="text"
              placeholder='e.g., "BS CIS 2023-2024"'
              value={form.planName}
              onChange={onChange("planName")}
              className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm"
            />
          </div>
          <div className="mb-3">
            <label htmlFor="preferredElectives" className="block text-sm font-medium text-gray-700 mb-1">Preferred electives (comma-separated)</label>
            <input
              id="preferredElectives"
              type="text"
              placeholder='e.g., AI, Data Science, Systems'
              value={form.preferredElectives}
              onChange={onChange("preferredElectives")}
              className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm"
            />
          </div>

          <fieldset className="border border-gray-200 rounded-lg p-3 mt-2">
            <legend className="text-sm font-medium text-gray-700 px-1.5">Constraints</legend>
            <div className="grid grid-cols-2 gap-3">
              <div className="mb-3">
                <label htmlFor="maxCreditHour" className="block text-sm font-medium text-gray-700 mb-1">Max credit hours</label>
                <input
                  id="maxCreditHour"
                  type="number"
                  min={1}
                  max={30}
                  placeholder="e.g., 18"
                  value={form.maxCreditHour === "" ? "" : form.maxCreditHour}
                  onChange={onChange("maxCreditHour")}
                  className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm"
                />
              </div>
              <div className="mb-3">
                <label htmlFor="semester" className="block text-sm font-medium text-gray-700 mb-1">Semester</label>
                <input
                  id="semester"
                  type="text"
                  placeholder='e.g., "Spring 2026"'
                  value={form.semester}
                  onChange={onChange("semester")}
                  className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm"
                />
              </div>
            </div>
          </fieldset>

          <div className="grid grid-cols-2 gap-3">
            <div className="mb-3">
              <label htmlFor="gpa" className="block text-sm font-medium text-gray-700 mb-1">GPA</label>
              <input
                id="gpa"
                type="number"
                min={0}
                max={4}
                step={0.01}
                placeholder="e.g., 3.7"
                value={form.gpa === "" ? "" : form.gpa}
                onChange={onChange("gpa")}
                className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm"
              />
            </div>
            <div />
          </div>

          <div className="flex gap-3 pt-3 items-center">
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
          {status && <p className="text-sm text-gray-500 mt-2">{status}</p>}
        </section>

        {/* Terminal Display - Add this before recommendations section */}
        {terminalLogs.length > 0 && (
          <section className="mt-6 p-4 bg-gray-900 rounded-lg shadow-lg font-mono text-sm text-left">
            <div className="flex items-center justify-between mb-3 pb-2 border-b border-gray-700">
              <h3 className="text-green-400 font-semibold flex items-center gap-2">
                🖥️ Processing Terminal
                {(isAnalyzing || status === "Loading...") && (
                  <span className="text-yellow-300">
                    <Spinner />
                  </span>
                )}
              </h3>
              <button
                onClick={() => setTerminalLogs([])}
                className="text-xs text-gray-400 hover:text-white"
              >
                Clear
              </button>
            </div>
            <div className="max-h-64 overflow-y-auto text-green-300 space-y-1">
              {terminalLogs.map((log, idx) => (
                <div key={idx} className="whitespace-pre-wrap">
                  {log}
                </div>
              ))}
              {isAnalyzing && (
                <div className="text-yellow-300">
                  <Spinner /> Analyzing with LLM... This may take a moment...
                </div>
              )}
            </div>
          </section>
        )}

        <section className="mt-6">
          <h2 className="text-xl font-semibold mb-2">Recommendations</h2>
          <ul className="list-disc pl-5">
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
      {showSettings && <SettingsModal />}
    </div>
  )
}
