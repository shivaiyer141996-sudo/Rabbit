import Image from "next/image";

export function Logo({ compact = false }: { compact?: boolean }) {
  return (
    <div className={compact ? "player-brand" : "auth-brand"}>
      <Image src="/rabbit-mark.svg" width={compact ? 38 : 46} height={compact ? 38 : 46} alt="" />
      <div className="brand-copy">
        <strong>Rabbit AiP</strong>
        {!compact && <span>Assessment Intelligence Platform</span>}
      </div>
    </div>
  );
}
