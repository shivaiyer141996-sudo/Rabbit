import { describe, expect, it } from "vitest";
import { activeSectionOptions, addUnique, matchesAssessmentQuestionFilters, questionIdsForRemovedSubject, reviewSelectionState, studentPortalRouteAllowed } from "./enhancement-rules";

describe("assessment enhancement rules", () => {
  it("prevents duplicate selections and preserves existing selections", () => {
    expect(addUnique(["q1", "q2"], "q2")).toEqual(["q1", "q2"]);
    expect(addUnique(["q1"], "q2")).toEqual(["q1", "q2"]);
  });

  it("identifies only selected questions affected by subject removal", () => {
    expect(questionIdsForRemovedSubject([
      { id: "q1", subjectId: "physics" },
      { id: "q2", subjectId: "chemistry" },
      { id: "q3", subjectId: "physics" },
    ], ["q1", "q2"], "physics")).toEqual(["q1"]);
  });

  it("combines subject, topic, difficulty, type, and text filters", () => {
    const question = { code: "PHY-1", stem: "Velocity", subjectId: "physics", topicId: "motion", difficulty: "EASY", type: "SINGLE_CORRECT" };
    expect(matchesAssessmentQuestionFilters(question, ["physics"], { query: "velo", subjectId: "physics", topicId: "motion", difficulty: "EASY", type: "SINGLE_CORRECT" })).toBe(true);
    expect(matchesAssessmentQuestionFilters(question, ["chemistry"], { query: "", subjectId: "", topicId: "", difficulty: "", type: "" })).toBe(false);
  });
});

describe("review and authorization rules", () => {
  it("represents clear, partial, and select-all review states", () => {
    const required = ["stem", "answer", "metadata"];
    expect(reviewSelectionState([], required)).toMatchObject({ none: true, partial: false, approveEnabled: false });
    expect(reviewSelectionState(["stem"], required)).toMatchObject({ partial: true, approveEnabled: false });
    expect(reviewSelectionState(required, required)).toMatchObject({ all: true, approveEnabled: true });
  });

  it("blocks staff portal routes for a student", () => {
    expect(studentPortalRouteAllowed("/dashboard")).toBe(true);
    expect(studentPortalRouteAllowed("/profile")).toBe(true);
    expect(studentPortalRouteAllowed("/approvals")).toBe(false);
    expect(studentPortalRouteAllowed("/users")).toBe(false);
  });

  it("refreshes dropdown options from the current active section list", () => {
    expect(activeSectionOptions([{ id: "new", status: "ACTIVE" }, { id: "old", status: "ARCHIVED" }])).toEqual([{ id: "new", status: "ACTIVE" }]);
  });
});
