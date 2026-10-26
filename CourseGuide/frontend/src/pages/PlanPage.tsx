import { useRef } from "react"
import { ChipInput, NumberField, Select, TextField } from "../components/fields"
import { Icon, SectionCard } from "../components/ui"
import { useAppState } from "../state/AppState"

export function PlanPage({ twoColumn, showInlineSubmit, onGenerate }: { twoColumn: boolean; showInlineSubmit: boolean; onGenerate: () => void }) {
  const s = useAppState()
  const { form, updateField: set, fieldErrors: err } = s

  const profile = (
    <SectionCard icon="school" title="About you" subtitle="Used to find your program's degree requirements.">
      <TextField
        label="University"
        required
        value={form.university}
        onValue={(v) => set("university", v)}
        placeholder="The Ohio State University"
        autocomplete="organization"
        errorText={err.university}
      />
      <TextField
        label="Current major"
        required
        value={form.major}
        onValue={(v) => set("major", v)}
        placeholder="Computer Science"
        errorText={err.major}
      />
      <div className="grid grid-cols-1 gap-4 medium:grid-cols-2">
        <Select
          label="Degree level"
          value={form.degreeLevel}
          onValue={(v) => set("degreeLevel", v === "Undergraduate" || v === "Graduate" ? v : "")}
          options={[
            { value: "Undergraduate", label: "Undergraduate" },
            { value: "Graduate", label: "Graduate" },
          ]}
        />
        <NumberField
          label="Graduation year"
          value={form.graduationYear}
          onValue={(v) => set("graduationYear", v)}
          min={1900}
          max={2100}
          placeholder="2027"
          inputMode="numeric"
          errorText={err.graduationYear}
        />
      </div>
    </SectionCard>
  )

  const progress = (
    <SectionCard icon="description" title="Academic progress" subtitle="Optional. Helps skip courses you've already taken.">
      <ProgressPdfPicker />
      <ChipInput
        label="Completed courses"
        values={form.completedCourses}
        onValues={(v) => set("completedCourses", v)}
        supportingText="Press Enter or type a comma to add, e.g. CSE 2221"
      />
      <NumberField
        label="GPA"
        value={form.gpa}
        onValue={(v) => set("gpa", v)}
        min={0}
        max={4}
        step={0.01}
        placeholder="3.7"
        inputMode="decimal"
        errorText={err.gpa}
      />
    </SectionCard>
  )

  const goals = (
    <SectionCard icon="flag" title="Goals" subtitle="What you're working toward.">
      <TextField
        label="Target major"
        value={form.targetMajor}
        onValue={(v) => set("targetMajor", v)}
        placeholder="Computer & Information Science"
      />
      <TextField label="Target minor" value={form.targetMinor} onValue={(v) => set("targetMinor", v)} placeholder="Mathematics" supportingText="Optional" />
      <TextField label="Plan name" value={form.planName} onValue={(v) => set("planName", v)} placeholder="BS CIS 2023-2024" />
      <TextField
        label="Preferred electives"
        value={form.preferredElectives}
        onValue={(v) => set("preferredElectives", v)}
        placeholder="AI, Data Science, Systems"
        supportingText="Separate topics with commas"
      />
    </SectionCard>
  )

  const constraints = (
    <SectionCard icon="tune" title="Constraints" subtitle="Limits for the semester you're planning.">
      <div className="grid grid-cols-1 gap-4 medium:grid-cols-2">
        <NumberField
          label="Max credit hours"
          value={form.maxCreditHour}
          onValue={(v) => set("maxCreditHour", v)}
          min={1}
          max={30}
          placeholder="18"
          inputMode="numeric"
          supportingText="Defaults to 18"
          errorText={err.maxCreditHour}
        />
        <TextField label="Semester" value={form.semester} onValue={(v) => set("semester", v)} placeholder="Spring 2026" />
      </div>
    </SectionCard>
  )

  const submit = showInlineSubmit && (
    <div className="flex flex-wrap items-center justify-end gap-2 pt-2">
      <md-text-button onClick={s.resetForm} disabled={s.running}>
        Clear form
      </md-text-button>
      <md-filled-button hasIcon onClick={onGenerate} disabled={s.running}>
        <md-icon slot="icon">auto_awesome</md-icon>
        {s.running ? "Generating…" : "Generate plan"}
      </md-filled-button>
    </div>
  )

  if (twoColumn) {
    // Expanded+ windows: two panes of equal width with a 24dp spacer.
    return (
      <div className="flex flex-col gap-6">
        <div className="grid grid-cols-2 items-start gap-6">
          <div className="flex flex-col gap-6">
            {profile}
            {progress}
          </div>
          <div className="flex flex-col gap-6">
            {goals}
            {constraints}
          </div>
        </div>
        {submit}
      </div>
    )
  }

  return (
    <div className="flex flex-col gap-4 medium:gap-6">
      {profile}
      {progress}
      {goals}
      {constraints}
      {submit}
      {!showInlineSubmit && (
        <div className="flex justify-center">
          <md-text-button onClick={s.resetForm} disabled={s.running}>
            Clear form
          </md-text-button>
        </div>
      )}
    </div>
  )
}

function ProgressPdfPicker() {
  const { upload, uploadPdf, clearPdf, running } = useAppState()
  const input = useRef<HTMLInputElement>(null)

  const choose = () => input.current?.click()
  const onFile = (e: React.ChangeEvent<HTMLInputElement>) => {
    const f = e.target.files?.[0]
    e.target.value = ""
    if (f) void uploadPdf(f)
  }

  return (
    <div>
      <input ref={input} type="file" accept="application/pdf,.pdf" hidden onChange={onFile} />
      {upload.status === "idle" ? (
        <button
          type="button"
          onClick={choose}
          className="relative flex w-full items-center gap-4 rounded-md border border-dashed border-outline px-4 py-4 text-left outline-none"
        >
          <md-ripple />
          <md-focus-ring />
          <span className="flex size-10 shrink-0 items-center justify-center rounded-full bg-primary-container text-on-primary-container">
            <Icon name="upload_file" />
          </span>
          <span className="min-w-0">
            <span className="md-typescale-title-small block text-on-surface">Upload progress PDF</span>
            <span className="md-typescale-body-small block text-on-surface-variant">
              One PDF with current, completed, and transfer courses
            </span>
          </span>
        </button>
      ) : (
        <div className="overflow-hidden rounded-md border border-outline-variant">
          <div className="flex items-center gap-3 py-2 pl-4 pr-1">
            <span
              className={`flex size-10 shrink-0 items-center justify-center rounded-full ${
                upload.status === "error" ? "bg-error-container text-on-error-container" : "bg-primary-container text-on-primary-container"
              }`}
            >
              <Icon name={upload.status === "error" ? "error" : upload.status === "done" ? "picture_as_pdf" : "upload"} />
            </span>
            <div className="min-w-0 flex-1">
              <p className="md-typescale-title-small truncate text-on-surface">{upload.fileName}</p>
              <p className={`md-typescale-body-small ${upload.status === "error" ? "text-error" : "text-on-surface-variant"}`}>
                {upload.status === "uploading" && "Uploading…"}
                {upload.status === "done" && "Uploaded and ready"}
                {upload.status === "error" && upload.message}
              </p>
            </div>
            {upload.status !== "uploading" && (
              <md-icon-button aria-label="Replace PDF" onClick={choose} disabled={running}>
                <md-icon>swap_horiz</md-icon>
              </md-icon-button>
            )}
            <md-icon-button aria-label="Remove PDF" onClick={clearPdf} disabled={running}>
              <md-icon>close</md-icon>
            </md-icon-button>
          </div>
          {upload.status === "uploading" && <md-linear-progress indeterminate className="block w-full" />}
        </div>
      )}
    </div>
  )
}
