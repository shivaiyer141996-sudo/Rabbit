import { describe, expect, it } from "vitest";
import {
  accessLabel,
  formatInrFromPaise,
  planPrice,
  wholeDaysRemaining,
} from "./commercial-rules";
import type { CommercialOverview } from "./live-types";

const catalog: CommercialOverview["catalog"] = [
  {
    code: "BASIC",
    label: "Basic",
    description: "Assessment only",
    prices: [
      { studentLimit: 50, monthlyPricePaise: 59_900 },
      { studentLimit: 150, monthlyPricePaise: 99_900 },
      { studentLimit: 500, monthlyPricePaise: 149_900 },
    ],
    entitlements: ["ASSESSMENT_DELIVERY"],
  },
];

describe("commercial rules", () => {
  it("formats paise as Indian rupees", () => {
    expect(formatInrFromPaise(59_900)).toContain("599");
  });

  it("selects only the exact approved capacity price", () => {
    expect(planPrice(catalog, "BASIC", 150)).toBe(99_900);
    expect(planPrice(catalog, "BASIC", 51)).toBeUndefined();
  });

  it("uses a clear read-only expiry label", () => {
    expect(accessLabel("TRIAL_EXPIRED")).toContain("read-only");
  });

  it("rounds a partial final day up and never becomes negative", () => {
    expect(
      wholeDaysRemaining("2026-09-21T00:00:00Z", "2026-09-20T12:00:00Z"),
    ).toBe(1);
    expect(
      wholeDaysRemaining("2026-09-19T00:00:00Z", "2026-09-20T12:00:00Z"),
    ).toBe(0);
  });
});
