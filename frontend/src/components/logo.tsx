import Image from "next/image";

export function Logo({ compact = false }: { compact?: boolean }) {
  return (
    <div className={compact ? "player-brand" : "auth-brand"}>
      <Image
        className={compact ? "rabbit-mark-image" : "rabbit-logo-image"}
        src={compact ? "/rabbit-mark.png" : "/rabbit-logo.png"}
        width={compact ? 38 : 230}
        height={compact ? 45 : 80}
        alt="Rabbit — Assess. Progress. Excel."
        priority={!compact}
      />
      {compact && <div className="brand-copy"><strong>Rabbit</strong><span>Assessment platform</span></div>}
    </div>
  );
}
