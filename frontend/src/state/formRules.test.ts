import { describe, expect, it } from "vitest"
import { EMPTY_FORM, parseSavedForm, validateForm, type FormState } from "./AppState"

const valid: FormState = { ...EMPTY_FORM, university: "OSU", major: "CS" }

describe("validateForm", () => {
  it("requires university and major", () => {
    expect(validateForm(EMPTY_FORM)).toEqual({
      university: "Enter your university",
      major: "Enter your current major",
    })
    expect(validateForm({ ...EMPTY_FORM, university: "  ", major: "CS" })).toHaveProperty("university")
  })

  it("accepts a minimal valid form", () => {
    expect(validateForm(valid)).toEqual({})
  })

  it.each([
    ["graduationYear", 1899],
    ["graduationYear", 2101],
    ["gpa", -0.1],
    ["gpa", 4.01],
    ["maxCreditHour", 0],
    ["maxCreditHour", 31],
  ] as const)("rejects %s = %s", (field, value) => {
    expect(validateForm({ ...valid, [field]: value })).toHaveProperty(field)
  })

  it.each([
    ["graduationYear", 2027],
    ["gpa", 4],
    ["gpa", 0],
    ["maxCreditHour", 30],
  ] as const)("accepts boundary %s = %s", (field, value) => {
    expect(validateForm({ ...valid, [field]: value })).toEqual({})
  })
})

describe("parseSavedForm", () => {
  it("restores a form saved by the current frontend", () => {
    const saved: FormState = {
      ...valid,
      degreeLevel: "Graduate",
      graduationYear: 2027,
      gpa: 3.5,
      completedCourses: ["CSE 2221"],
      progressFileId: "id1",
      progressFileName: "p.pdf",
    }

    expect(parseSavedForm(JSON.stringify(saved))).toEqual(saved)
  })

  it("accepts the previous frontend's format", () => {
    // The old UI stored a File object (serialized as {}) and had no completedCourses.
    const legacy = {
      university: "OSU",
      major: "CS",
      degreeLevel: "Undergraduate",
      graduationYear: "2026",
      progressPdf: {},
      maxCreditHour: 15,
      gpa: "",
    }

    const form = parseSavedForm(JSON.stringify(legacy))

    expect(form.graduationYear).toBe(2026)
    expect(form.maxCreditHour).toBe(15)
    expect(form.gpa).toBe("")
    expect(form.completedCourses).toEqual([])
    expect(form).not.toHaveProperty("progressPdf")
  })

  it("drops invalid values", () => {
    const form = parseSavedForm(
      JSON.stringify({ degreeLevel: "PhD", gpa: "abc", completedCourses: ["A", 3, null], university: 42 }),
    )

    expect(form.degreeLevel).toBe("")
    expect(form.gpa).toBe("")
    expect(form.completedCourses).toEqual(["A"])
    expect(form.university).toBe("")
  })
})
