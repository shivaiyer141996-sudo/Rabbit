import type { ReactNode } from "react";

const statusTone: Record<string, string> = {
  APPROVED: "success",
  PUBLISHED: "success",
  ACTIVE: "success",
  SCHEDULED: "info",
  UNDER_REVIEW: "warning",
  READY_FOR_REVIEW: "warning",
  DRAFT: "neutral",
  RETIRED: "danger",
  SUSPENDED: "danger",
  COMPLETED: "success",
};

export function StatusBadge({
  status,
  children,
}: {
  status: string;
  children?: ReactNode;
}) {
  const tone = statusTone[status] ?? "neutral";
  return (
    <span className={`badge badge-${tone}`}>
      {children ?? status.replaceAll("_", " ")}
    </span>
  );
}
