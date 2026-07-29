import { describe, expect, it } from "vitest";
import { validateGradeBands } from "./settings-validation";

describe("validateGradeBands", () => {
  it("accepts a complete contiguous configuration", () => {
    expect(
      validateGradeBands([
        { code: "A", label: "Excellent", minPercentage: 80, maxPercentage: 100 },
        { code: "B", label: "Good", minPercentage: 40, maxPercentage: 79.99 },
        { code: "F", label: "Support", minPercentage: 0, maxPercentage: 39.99 },
      ]),
    ).toBeNull();
  });

  it("rejects a gap", () => {
    expect(
      validateGradeBands([
        { code: "A", label: "Excellent", minPercentage: 80, maxPercentage: 100 },
        { code: "F", label: "Support", minPercentage: 0, maxPercentage: 39.99 },
      ]),
    ).toContain("gaps");
  });
});
