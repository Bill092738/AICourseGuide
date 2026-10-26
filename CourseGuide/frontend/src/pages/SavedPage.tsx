import { useCallback, useEffect, useState } from "react"
import { Dialog } from "../components/Dialog"
import { TextField } from "../components/fields"
import { Card, EmptyState, Icon, SectionCard, SegmentedButton, StatusChip } from "../components/ui"
import { useSnackbar } from "../components/Snackbar"
import type { UserProgress } from "../lib/api"
import { useAppState } from "../state/AppState"

function formatDate(s?: string) {
  if (!s) return ""
  const d = new Date(s)
  return Number.isNaN(d.getTime()) ? s : d.toLocaleString(undefined, { dateStyle: "medium", timeStyle: "short" })
}

function describe(p: UserProgress) {
  try {
    const f = p.formData ? JSON.parse(p.formData) : {}
    return [f.major, f.university].filter(Boolean).join(" · ")
  } catch {
    return ""
  }
}

export function SavedPage({ twoPane, onLoaded }: { twoPane: boolean; onLoaded: () => void }) {
  const s = useAppState()
  const { showSnackbar } = useSnackbar()
  const [loadId, setLoadId] = useState("")
  const [toDelete, setToDelete] = useState<string | null>(null)

  const { savedList, refreshSavedList } = s
  useEffect(() => {
    if (savedList === null) void refreshSavedList()
  }, [savedList, refreshSavedList])

  const load = useCallback(
    async (id: string) => {
      if (await s.loadProgress(id)) onLoaded()
    },
    [s, onLoaded],
  )

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(s.caseNumber)
      showSnackbar("Case number copied")
    } catch {
      showSnackbar("Could not copy to the clipboard")
    }
  }

  const current = (
    <SectionCard icon="cloud_upload" title="This session" subtitle="Save your form and selected courses under a case number.">
      <div>
        <p className="md-typescale-label-large mb-2 text-on-surface-variant">Storage</p>
        <SegmentedButton
          label="Storage mode"
          value={s.storageMode}
          onChange={s.setStorageMode}
          options={[
            { value: "local", label: "Local (SQL)", icon: "storage" },
            { value: "online", label: "Online (Supabase)", icon: "cloud" },
          ]}
        />
        <p className="md-typescale-body-small mt-2 text-on-surface-variant">
          {s.storageMode === "local"
            ? "Saved to the local MySQL database."
            : "Saved locally and synced to Supabase (requires backend configuration)."}
        </p>
      </div>

      {s.storageMode === "online" && s.syncStatus?.needsSync && (
        <div className="flex flex-wrap items-center gap-3 rounded-md bg-tertiary-container p-3 pl-4 text-on-tertiary-container">
          <Icon name="sync_problem" />
          <p className="md-typescale-body-medium min-w-0 flex-1">
            {s.syncStatus.unsyncedCount} local {s.syncStatus.unsyncedCount === 1 ? "record isn't" : "records aren't"} synced yet.
          </p>
          <md-filled-tonal-button onClick={s.syncNow} disabled={s.busy === "sync"}>
            {s.busy === "sync" ? "Syncing…" : "Sync now"}
          </md-filled-tonal-button>
        </div>
      )}

      <TextField
        label="Case number"
        value={s.caseNumber}
        onValue={(v) => s.setCaseNumber(v.toUpperCase().replace(/\s/g, ""))}
        supportingText="Leave empty to get a new one when you save"
        leadingIcon="tag"
        trailing={
          s.caseNumber ? (
            <md-icon-button slot="trailing-icon" aria-label="Copy case number" onClick={copy}>
              <md-icon>content_copy</md-icon>
            </md-icon-button>
          ) : undefined
        }
      />
      <div className="flex flex-wrap justify-end gap-2">
        <md-text-button hasIcon onClick={s.generateCaseNumber}>
          <md-icon slot="icon">autorenew</md-icon>
          New number
        </md-text-button>
        <md-filled-button hasIcon onClick={s.saveProgress} disabled={s.busy === "save"}>
          <md-icon slot="icon">save</md-icon>
          {s.busy === "save" ? "Saving…" : "Save progress"}
        </md-filled-button>
      </div>
    </SectionCard>
  )

  const loadCard = (
    <SectionCard icon="download" title="Load progress" subtitle={`Restore a saved session from ${s.storageMode === "online" ? "online" : "local"} storage.`}>
      <TextField
        label="Case number to load"
        value={loadId}
        onValue={(v) => setLoadId(v.toUpperCase().replace(/\s/g, ""))}
        onEnter={() => void load(loadId)}
        leadingIcon="search"
      />
      <div className="flex justify-end">
        <md-filled-tonal-button onClick={() => void load(loadId)} disabled={s.busy === "load" || !loadId.trim()}>
          {s.busy === "load" ? "Loading…" : "Load"}
        </md-filled-tonal-button>
      </div>
    </SectionCard>
  )

  const list = (
    <Card className="overflow-hidden">
      <div className="flex items-center gap-2 py-3 pl-4 pr-2 medium:pl-6">
        <h2 className="md-typescale-title-large flex-1 text-on-surface">Saved sessions</h2>
        <md-icon-button aria-label="Refresh" onClick={refreshSavedList} disabled={s.busy === "list"}>
          <md-icon>refresh</md-icon>
        </md-icon-button>
      </div>
      {s.busy === "list" && <md-linear-progress indeterminate className="block w-full" />}
      {s.savedListError ? (
        <EmptyState icon="cloud_off" title="Couldn't load saved sessions" body={s.savedListError} />
      ) : savedList && savedList.length === 0 ? (
        <EmptyState icon="inventory_2" title="Nothing saved yet" body="Saved sessions in local storage appear here." />
      ) : (
        <md-list className="bg-transparent!" style={{ "--md-list-container-color": "transparent" } as React.CSSProperties}>
          {(savedList ?? []).map((p) => {
            const id = p.caseNumber ?? ""
            const detail = describe(p)
            return (
              <md-list-item key={id} type="text">
                <md-icon slot="start">{p.syncedToCloud ? "cloud_done" : "storage"}</md-icon>
                <div slot="headline" className="font-mono">
                  {id}
                  {id === s.caseNumber && (
                    <span className="ml-2 align-middle">
                      <StatusChip tone="primary">Current</StatusChip>
                    </span>
                  )}
                </div>
                {detail && <div slot="supporting-text">{detail}</div>}
                <div slot="supporting-text">{formatDate(p.updatedAt)}</div>
                <div slot="end" className="flex">
                  <md-icon-button aria-label={`Open ${id}`} onClick={() => void load(id)}>
                    <md-icon>open_in_browser</md-icon>
                  </md-icon-button>
                  <md-icon-button aria-label={`Delete ${id}`} onClick={() => setToDelete(id)}>
                    <md-icon>delete</md-icon>
                  </md-icon-button>
                </div>
              </md-list-item>
            )
          })}
        </md-list>
      )}
    </Card>
  )

  return (
    <>
      {twoPane ? (
        <div className="grid grid-cols-2 items-start gap-6">
          <div className="flex flex-col gap-6">
            {current}
            {loadCard}
          </div>
          {list}
        </div>
      ) : (
        <div className="flex flex-col gap-4 medium:gap-6">
          {current}
          {loadCard}
          {list}
        </div>
      )}

      <Dialog
        open={toDelete !== null}
        onClose={() => setToDelete(null)}
        icon="delete"
        headline="Delete saved session?"
        actions={
          <>
            <md-text-button onClick={() => setToDelete(null)}>Cancel</md-text-button>
            <md-text-button
              onClick={() => {
                if (toDelete) void s.deleteProgress(toDelete)
                setToDelete(null)
              }}
            >
              Delete
            </md-text-button>
          </>
        }
      >
        <span className="font-mono">{toDelete}</span> will be removed from local storage. This can't be undone.
      </Dialog>
    </>
  )
}
