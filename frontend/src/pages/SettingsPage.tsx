import { useState } from "react"
import { TextField } from "../components/fields"
import { Icon, SectionCard, SegmentedButton, StatusChip } from "../components/ui"
import { useSnackbar } from "../components/Snackbar"
import { api, errorMessage } from "../lib/api"
import { useAppState } from "../state/AppState"
import { SEED_COLORS, type ThemePrefs } from "../theme/theme"

const WORKFLOW = [
  { icon: "person", title: "Student profile", body: "Your university, major, degree level, graduation year, and optional progress PDF." },
  { icon: "travel_explore", title: "Web search", body: "DuckDuckGo finds the university's degree requirements page." },
  { icon: "picture_as_pdf", title: "Page snapshot", body: "Playwright loads the page, dismisses overlays, and saves it as a PDF." },
  { icon: "text_snippet", title: "Text extraction", body: "Text is extracted from the requirements PDF and your progress PDF." },
  { icon: "psychology", title: "LLM analysis", body: "The configured LLM turns the text into an XML course plan." },
  { icon: "account_tree", title: "Course selection", body: "A prerequisite graph is built and up to 6 courses are picked within your credit limit." },
]

/** Picks a legible check-mark color for a swatch (relative luminance > 0.4 counts as light). */
function isLight(hex: string) {
  const [r, g, b] = [1, 3, 5].map((i) => {
    const c = parseInt(hex.slice(i, i + 2), 16) / 255
    return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4
  })
  return 0.2126 * r + 0.7152 * g + 0.0722 * b > 0.4
}

export function SettingsPage({
  twoPane,
  theme,
  onTheme,
}: {
  twoPane: boolean
  theme: ThemePrefs
  onTheme: (t: ThemePrefs) => void
}) {
  const { llmConfig, setLlmConfig, resetLlmConfig } = useAppState()
  const { showSnackbar } = useSnackbar()
  const [showKey, setShowKey] = useState(false)
  const [health, setHealth] = useState<{ state: "unknown" | "checking" | "ok" | "down"; detail?: string }>({ state: "unknown" })

  const checkHealth = async () => {
    setHealth({ state: "checking" })
    try {
      const r = await api.health()
      setHealth({ state: r.status === "ok" ? "ok" : "down", detail: r.status })
    } catch (err) {
      setHealth({ state: "down", detail: errorMessage(err) })
    }
  }

  const appearance = (
    <SectionCard icon="palette" title="Appearance" subtitle="Material You dynamic color, generated from a seed color.">
      <div>
        <p className="md-typescale-label-large mb-2 text-on-surface-variant">Theme</p>
        <SegmentedButton
          label="Theme"
          value={theme.mode}
          onChange={(mode) => onTheme({ ...theme, mode })}
          options={[
            { value: "system", label: "System", icon: "brightness_auto" },
            { value: "light", label: "Light", icon: "light_mode" },
            { value: "dark", label: "Dark", icon: "dark_mode" },
          ]}
        />
      </div>
      <div>
        <p className="md-typescale-label-large mb-2 text-on-surface-variant">Color</p>
        <div role="radiogroup" aria-label="Seed color" className="flex flex-wrap gap-3">
          {SEED_COLORS.map((c) => {
            const selected = c.hex.toLowerCase() === theme.seed.toLowerCase()
            return (
              <button
                key={c.hex}
                type="button"
                role="radio"
                aria-checked={selected}
                aria-label={c.name}
                title={c.name}
                onClick={() => onTheme({ ...theme, seed: c.hex })}
                className={`relative flex size-12 items-center justify-center rounded-full outline-none ring-offset-2 ring-offset-surface-container-low transition-shadow ${
                  selected ? "ring-2 ring-primary" : ""
                }`}
                style={{ background: c.hex }}
              >
                <md-ripple />
                <md-focus-ring />
                {selected && <Icon name="check" className={isLight(c.hex) ? "text-[#000]" : "text-[#fff]"} />}
              </button>
            )
          })}
        </div>
      </div>
    </SectionCard>
  )

  const llm = (
    <SectionCard
      icon="psychology"
      title="LLM API"
      subtitle="Any OpenAI-compatible API. Leave everything empty to use the default local server (localhost:8075)."
    >
      <TextField
        label="API base URL"
        type="url"
        inputMode="url"
        value={llmConfig.apiBaseUrl}
        onValue={(v) => setLlmConfig({ ...llmConfig, apiBaseUrl: v })}
        placeholder="https://api.openai.com/v1"
        supportingText="Empty uses http://localhost:8075"
      />
      <TextField
        label="API key"
        type={showKey ? "text" : "password"}
        value={llmConfig.apiKey}
        onValue={(v) => setLlmConfig({ ...llmConfig, apiKey: v })}
        autocomplete="off"
        supportingText="Stored in this browser and sent to the CourseGuide backend with each request"
        trailing={
          <md-icon-button slot="trailing-icon" aria-label={showKey ? "Hide API key" : "Show API key"} onClick={() => setShowKey(!showKey)}>
            <md-icon>{showKey ? "visibility_off" : "visibility"}</md-icon>
          </md-icon-button>
        }
      />
      <TextField
        label="Model name"
        value={llmConfig.modelName}
        onValue={(v) => setLlmConfig({ ...llmConfig, modelName: v })}
        placeholder="gpt-4o, llama-3.3-70b-versatile"
        supportingText="Optional. Empty auto-discovers the model from the API"
      />
      <div className="flex justify-end">
        <md-text-button
          hasIcon
          onClick={() => {
            resetLlmConfig()
            showSnackbar("LLM settings reset to default")
          }}
        >
          <md-icon slot="icon">restart_alt</md-icon>
          Reset to default
        </md-text-button>
      </div>
    </SectionCard>
  )

  const server = (
    <SectionCard
      icon="dns"
      title="Server"
      subtitle="CourseGuide backend health."
      action={
        health.state === "ok" ? (
          <StatusChip tone="success" icon="check_circle">Online</StatusChip>
        ) : health.state === "down" ? (
          <StatusChip tone="error" icon="error">Unreachable</StatusChip>
        ) : undefined
      }
    >
      {health.state === "down" && health.detail && <p className="md-typescale-body-medium text-error">{health.detail}</p>}
      <div className="flex justify-end">
        <md-outlined-button hasIcon onClick={checkHealth} disabled={health.state === "checking"}>
          <md-icon slot="icon">monitor_heart</md-icon>
          {health.state === "checking" ? "Checking…" : "Check health"}
        </md-outlined-button>
      </div>
    </SectionCard>
  )

  const about = (
    <SectionCard icon="info" title="How it works" subtitle="What happens when you generate a plan.">
      <ol className="flex flex-col gap-4">
        {WORKFLOW.map((w, i) => (
          <li key={w.title} className="flex gap-4">
            <span className="flex size-10 shrink-0 items-center justify-center rounded-full bg-surface-container-highest text-on-surface-variant">
              <Icon name={w.icon} />
            </span>
            <div>
              <p className="md-typescale-title-small text-on-surface">
                {i + 1}. {w.title}
              </p>
              <p className="md-typescale-body-medium text-on-surface-variant">{w.body}</p>
            </div>
          </li>
        ))}
      </ol>
    </SectionCard>
  )

  if (twoPane) {
    return (
      <div className="grid grid-cols-2 items-start gap-6">
        <div className="flex flex-col gap-6">
          {appearance}
          {llm}
        </div>
        <div className="flex flex-col gap-6">
          {server}
          {about}
        </div>
      </div>
    )
  }
  return (
    <div className="flex flex-col gap-4 medium:gap-6">
      {appearance}
      {llm}
      {server}
      {about}
    </div>
  )
}
