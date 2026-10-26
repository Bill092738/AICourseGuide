import { api, type SelectedCourse } from "../lib/api"
import { Card, EmptyState, Icon, StatusChip } from "../components/ui"
import { useAppState, type RunPhase } from "../state/AppState"

export function ResultsPage({ twoPane, onGoToPlan, onGoToSaved }: { twoPane: boolean; onGoToPlan: () => void; onGoToSaved: () => void }) {
  const s = useAppState()
  const { results, phase } = s
  const hasCourses = results.courses.length > 0

  if (phase === "idle" && !hasCourses && s.logs.length === 0) {
    return (
      <EmptyState
        icon="auto_awesome"
        title="No plan yet"
        body="Fill in your profile and goals, then generate a plan. Selected courses will show up here."
        action={
          <md-filled-tonal-button hasIcon onClick={onGoToPlan}>
            <md-icon slot="icon">edit_note</md-icon>
            Go to plan
          </md-filled-tonal-button>
        }
      />
    )
  }

  const f = s.form
  const snapshotQuery = [f.university, f.major, "degree requirements", f.graduationYear].filter((x) => String(x).trim()).join(" ")

  const main = (
    <div className="flex min-w-0 flex-col gap-4 medium:gap-6">
      {(phase !== "idle" || hasCourses) && <SummaryCard onSave={onGoToSaved} snapshotQuery={f.university && f.major ? snapshotQuery : ""} />}

      {results.importErrors.length > 0 && (
        <Card className="flex gap-3 bg-surface-container-highest! p-4 text-on-surface-variant">
          <Icon name="info" className="text-tertiary" />
          <ul className="md-typescale-body-medium list-disc pl-4">
            {results.importErrors.map((e, i) => (
              <li key={i}>{e}</li>
            ))}
          </ul>
        </Card>
      )}

      {hasCourses && (
        <section aria-labelledby="courses-heading">
          <h2 id="courses-heading" className="md-typescale-title-medium mb-3 px-1 text-on-surface-variant">
            Selected courses
          </h2>
          <ul className="grid grid-cols-1 gap-3 large:grid-cols-2">
            {results.courses.map((c, i) => (
              <li key={`${c.courseName}-${i}`}>
                <CourseCard course={c} />
              </li>
            ))}
          </ul>
        </section>
      )}

      {phase === "done" && !hasCourses && (
        <Card variant="outlined">
          <EmptyState
            icon="search_off"
            title="No courses selected"
            body="The backend could not produce a course plan. Check the activity log, your LLM settings, and that the university and major are spelled correctly."
          />
        </Card>
      )}

      {results.recommendations.length > 0 && (
        <Card className="p-4 medium:p-6">
          <h2 className="md-typescale-title-medium mb-2 text-on-surface">Recommendations</h2>
          <ul className="md-typescale-body-medium list-disc pl-5 text-on-surface-variant">
            {results.recommendations.map((r, i) => (
              <li key={i}>{r}</li>
            ))}
          </ul>
        </Card>
      )}
    </div>
  )

  const side = (
    <div className="flex min-w-0 flex-col gap-4 medium:gap-6">
      {phase !== "idle" && <PhaseCard phase={phase} error={s.runError} />}
      <ActivityLog />
    </div>
  )

  if (twoPane) {
    return (
      <div className="grid grid-cols-[minmax(0,1fr)_360px] items-start gap-6">
        {main}
        <div className="sticky top-20">{side}</div>
      </div>
    )
  }
  return (
    <div className="flex flex-col gap-4 medium:gap-6">
      {phase === "requirements" || phase === "selecting" || phase === "error" ? (
        <>
          {side}
          {main}
        </>
      ) : (
        <>
          {main}
          {side}
        </>
      )}
    </div>
  )
}

function SummaryCard({ onSave, snapshotQuery }: { onSave: () => void; snapshotQuery: string }) {
  const { results, phase, running, caseNumber } = useAppState()
  const pct = results.maxCredits > 0 ? Math.min(1, results.totalCredits / results.maxCredits) : 0
  return (
    <Card className="bg-primary-container! p-4 text-on-primary-container medium:p-6">
      <div className="flex flex-wrap items-end gap-x-8 gap-y-3">
        <div>
          <p className="md-typescale-label-large opacity-80">Total credits</p>
          <p className="md-typescale-display-small">
            {running ? "—" : results.totalCredits}
            <span className="md-typescale-title-medium opacity-80"> / {results.maxCredits}</span>
          </p>
        </div>
        <div>
          <p className="md-typescale-label-large opacity-80">Courses</p>
          <p className="md-typescale-display-small">{running ? "—" : results.courses.length}</p>
        </div>
      </div>
      <md-linear-progress
        className="mt-4 block w-full"
        value={running ? 0 : pct}
        indeterminate={running}
        style={{ "--md-linear-progress-track-color": "var(--md-sys-color-surface-container-highest)" } as React.CSSProperties}
      />
      {results.summarySentence && <p className="md-typescale-body-medium mt-3 opacity-80">{results.summarySentence}</p>}
      <div className="mt-4 flex flex-wrap gap-2">
        {phase === "done" && results.courses.length > 0 && (
          <md-filled-button hasIcon onClick={onSave}>
            <md-icon slot="icon">save</md-icon>
            {caseNumber ? "Save progress" : "Save this plan"}
          </md-filled-button>
        )}
        {snapshotQuery && (
          <md-outlined-button hasIcon href={api.snapshotSearchUrl(snapshotQuery)} target="_blank">
            <md-icon slot="icon">open_in_new</md-icon>
            Requirements PDF
          </md-outlined-button>
        )}
      </div>
    </Card>
  )
}

function CourseCard({ course }: { course: SelectedCourse }) {
  const prereqs = course.prerequisites?.filter(Boolean) ?? []
  return (
    <Card variant="outlined" className="h-full p-4">
      <div className="flex items-start gap-3">
        <span className="flex size-10 shrink-0 items-center justify-center rounded-full bg-tertiary-container text-on-tertiary-container">
          <Icon name="menu_book" />
        </span>
        <div className="min-w-0 flex-1">
          <h3 className="md-typescale-title-medium break-words text-on-surface">{course.courseName}</h3>
          <div className="mt-1.5 flex flex-wrap gap-1.5">
            <StatusChip tone="primary" icon="schedule">
              {course.creditHours} {course.creditHours === 1 ? "credit" : "credits"}
            </StatusChip>
            {course.category && <StatusChip tone="neutral">{course.category}</StatusChip>}
          </div>
        </div>
      </div>
      {course.description && <p className="md-typescale-body-medium mt-3 text-on-surface-variant">{course.description}</p>}
      <p className="md-typescale-body-small mt-3 flex items-center gap-1.5 text-on-surface-variant">
        <Icon name="account_tree" className="!text-[16px]" />
        {prereqs.length ? `Prerequisites: ${prereqs.join(", ")}` : "No prerequisites"}
      </p>
    </Card>
  )
}

const PHASES: { id: Exclude<RunPhase, "idle" | "done" | "error">; title: string; body: string }[] = [
  {
    id: "requirements",
    title: "Find requirements and analyze",
    body: "Search for the degree requirements page, save it as a PDF, and have the LLM build a course plan.",
  },
  { id: "selecting", title: "Select courses", body: "Build the prerequisite graph and pick courses within your credit limit." },
]

function PhaseCard({ phase, error }: { phase: RunPhase; error: string }) {
  const activeIndex = phase === "requirements" ? 0 : phase === "selecting" ? 1 : phase === "done" ? 2 : -1
  return (
    <Card className="p-4 medium:p-6">
      <div className="mb-3 flex items-center gap-2">
        <h2 className="md-typescale-title-medium flex-1 text-on-surface">Status</h2>
        {phase === "done" && (
          <StatusChip tone="success" icon="check">
            Complete
          </StatusChip>
        )}
        {phase === "error" && (
          <StatusChip tone="error" icon="error">
            Failed
          </StatusChip>
        )}
      </div>
      <ol className="flex flex-col gap-3">
        {PHASES.map((p, i) => {
          const state = phase === "error" ? (i === 0 ? "error" : "pending") : i < activeIndex ? "done" : i === activeIndex ? "active" : "pending"
          return (
            <li key={p.id} className="flex gap-3">
              <span className="flex size-6 shrink-0 items-center justify-center">
                {state === "active" ? (
                  <md-circular-progress indeterminate style={{ "--md-circular-progress-size": "24px" } as React.CSSProperties} />
                ) : (
                  <Icon
                    name={state === "done" ? "check_circle" : state === "error" ? "cancel" : "radio_button_unchecked"}
                    filled={state !== "pending"}
                    className={state === "done" ? "text-primary" : state === "error" ? "text-error" : "text-outline"}
                  />
                )}
              </span>
              <div>
                <p className={`md-typescale-title-small ${state === "pending" ? "text-on-surface-variant" : "text-on-surface"}`}>{p.title}</p>
                <p className="md-typescale-body-small text-on-surface-variant">{p.body}</p>
              </div>
            </li>
          )
        })}
      </ol>
      {phase === "error" && error && <p className="md-typescale-body-medium mt-3 text-error">{error}</p>}
    </Card>
  )
}

function ActivityLog() {
  const { logs, clearLogs } = useAppState()
  if (!logs.length) return null
  const color = { info: "text-on-surface-variant", success: "text-primary", warning: "text-tertiary", error: "text-error" }
  return (
    <Card variant="outlined" className="overflow-hidden">
      <div className="flex items-center gap-2 py-2 pl-4 pr-2">
        <Icon name="terminal" className="text-on-surface-variant" />
        <h2 className="md-typescale-title-small flex-1 text-on-surface">Activity</h2>
        <md-text-button onClick={clearLogs}>Clear</md-text-button>
      </div>
      <md-divider />
      <ol className="max-h-72 overflow-y-auto px-4 py-3 font-mono text-[12px] leading-5">
        {logs.map((l, i) => (
          <li key={i} className={`whitespace-pre-wrap break-words ${color[l.level]}`}>
            <span className="text-outline">{l.time}</span> {l.message}
          </li>
        ))}
      </ol>
    </Card>
  )
}
