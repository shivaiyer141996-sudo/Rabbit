import { describe, expect, it } from "vitest";
import {
  initials,
  mapAssessment,
  mapQuestion,
  type AcademicCatalog,
  type ApiAssessment,
  type ApiQuestion,
} from "./live-types";

const catalog: AcademicCatalog = {
  academicYears: [],
  departments: [],
  sections: [],
  subjects: [{ id: "subject-1", code: "PHY", name: "Physics", active: true }],
  topics: [
    {
      id: "topic-1",
      subjectId: "subject-1",
      name: "Kinematics",
      active: true,
    },
  ],
};

describe("live API view mapping", () => {
  it("maps question IDs to live academic names and option order", () => {
    const question: ApiQuestion = {
      id: "question-1",
      code: "PHY-001",
      stem: "What is velocity?",
      type: "SINGLE_CORRECT",
      subjectId: "subject-1",
      topicId: "topic-1",
      difficulty: "EASY",
      bloomLevel: "REMEMBER",
      marks: 2,
      negativeMarks: 0,
      status: "APPROVED",
      version: 1,
      language: "en",
      authorUserId: "33333333-3333-3333-3333-333333333333",
      options: [
        {
          id: "option-b",
          label: "B",
          text: "Distance",
          correct: false,
          sortOrder: 1,
        },
        {
          id: "option-a",
          label: "A",
          text: "Rate of displacement",
          correct: true,
          sortOrder: 0,
        },
      ],
      createdAt: "2026-07-30T00:00:00Z",
      updatedAt: "2026-07-30T00:00:00Z",
    };

    const view = mapQuestion(question, catalog);

    expect(view.subject).toBe("Physics");
    expect(view.topic).toBe("Kinematics");
    expect(view.options.map((option) => option.label)).toEqual(["A", "B"]);
  });

  it("maps assessment subjects and formats the lifecycle type", () => {
    const assessment: ApiAssessment = {
      id: "assessment-1",
      title: "Motion check",
      code: "ASM-001",
      type: "CHAPTER_TEST",
      subjectId: "subject-1",
      durationMinutes: 30,
      status: "DRAFT",
      totalMarks: 10,
      questionCount: 3,
      shuffleQuestions: true,
      shuffleOptions: false,
      partialMarking: false,
      attemptsAllowed: 1,
      questionIds: [],
      eligibleSectionIds: [],
      createdAt: "2026-07-30T00:00:00Z",
      updatedAt: "2026-07-30T00:00:00Z",
    };

    expect(mapAssessment(assessment, catalog)).toMatchObject({
      subject: "Physics",
      type: "CHAPTER TEST",
      totalMarks: 10,
    });
    expect(initials("Rohan", "Iyer")).toBe("RI");
  });
});
