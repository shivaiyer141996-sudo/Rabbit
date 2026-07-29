import type { GradeBand } from "./types";

export function validateGradeBands(bands: GradeBand[]): string | null {
  if (!bands.length) return "Add at least one grade band.";
  const ordered = [...bands].sort(
    (left, right) => left.minPercentage - right.minPercentage,
  );
  if (
    ordered[0].minPercentage !== 0 ||
    ordered.at(-1)?.maxPercentage !== 100
  ) {
    return "Grade bands must cover 0–100.";
  }
  for (let index = 0; index < ordered.length; index += 1) {
    const band = ordered[index];
    if (
      band.minPercentage < 0 ||
      band.maxPercentage > 100 ||
      band.minPercentage > band.maxPercentage
    ) {
      return "Each grade range must be valid.";
    }
    if (index > 0) {
      const expected = Number((ordered[index - 1].maxPercentage + 0.01).toFixed(2));
      if (Number(band.minPercentage.toFixed(2)) !== expected) {
        return "Grade bands cannot contain gaps or overlaps.";
      }
    }
  }
  if (new Set(bands.map((band) => band.code.trim().toUpperCase())).size !== bands.length) {
    return "Grade codes must be unique.";
  }
  return null;
}
