import type { LucideIcon } from "lucide-react";

export function MetricCard({
  label,
  value,
  change,
  icon: Icon,
}: {
  label: string;
  value: string;
  change?: string;
  icon: LucideIcon;
}) {
  return (
    <article className="metric-card">
      <div className="metric-top">
        <span className="metric-icon">
          <Icon size={18} />
        </span>
        {change && <span className="metric-change">{change}</span>}
      </div>
      <strong>{value}</strong>
      <span>{label}</span>
    </article>
  );
}
