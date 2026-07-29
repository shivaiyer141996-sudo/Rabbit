import { describe, expect, it } from "vitest";
import { validateQuestionDraft, type QuestionDraft } from "./question-validation";

const validDraft: QuestionDraft = {
  stem: "Which planet is known as the Red Planet?",
  type: "SINGLE_CORRECT",
  subject: "Science",
  topic: "Solar System",
  difficulty: "EASY",
  marks: 2,
  negativeMarks: 0.5,
  options: [
    { text: "Earth", correct: false },
    { text: "Mars", correct: true },
    { text: "Jupiter", correct: false },
    { text: "Venus", correct: false },
  ],
};

describe("validateQuestionDraft", () => {
  it("accepts a valid Single Correct MCQ", () => {
    expect(validateQuestionDraft(validDraft)).toEqual([]);
  });

  it("requires exactly one answer for Single Correct MCQ", () => {
    const invalid = {
      ...validDraft,
      options: validDraft.options.map((option) => ({ ...option, correct: false })),
    };
    expect(validateQuestionDraft(invalid)).toContain(
      "Single Correct MCQ requires exactly one correct option.",
    );
  });

  it("requires two answers for Multiple Correct MCQ", () => {
    const invalid = { ...validDraft, type: "MULTIPLE_CORRECT" as const };
    expect(validateQuestionDraft(invalid)).toContain(
      "Multiple Correct MCQ requires at least two correct options.",
    );
  });

  it("rejects negative marks greater than marks", () => {
    expect(
      validateQuestionDraft({ ...validDraft, marks: 2, negativeMarks: 3 }),
    ).toContain("Negative marks must be between zero and the question marks.");
  });
});
