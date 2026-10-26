import { vi } from "vitest"

export type Route = (init: RequestInit | undefined, url: string) => { status?: number; body?: unknown } | undefined

/**
 * Stubs global fetch. `routes` maps "METHOD /path" (path without query string) to a handler.
 * Returns the mock so tests can inspect calls.
 */
export function mockFetch(routes: Record<string, Route>) {
  const fn = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input)
    const method = (init?.method ?? "GET").toUpperCase()
    const key = `${method} ${url.split("?")[0]}`
    const handler = routes[key]
    const res = handler?.(init, url) ?? { status: 404, body: { message: `No mock for ${key}` } }
    const status = res.status ?? 200
    return new Response(JSON.stringify(res.body ?? {}), { status, headers: { "Content-Type": "application/json" } })
  })
  vi.stubGlobal("fetch", fn)
  return fn
}

/** Parses the JSON body of the n-th call to `path`. */
export function bodyOf(fn: ReturnType<typeof mockFetch>, path: string, n = 0) {
  const calls = fn.mock.calls.filter(([u]) => String(u).split("?")[0] === path)
  return JSON.parse(String(calls[n]?.[1]?.body ?? "null"))
}
