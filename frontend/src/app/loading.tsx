import { Logo } from "@/components/logo";

export default function Loading() {
  return (
    <div className="platform-loading" role="status" aria-live="polite">
      <Logo />
      <span className="loading-orbit" aria-hidden="true" />
      <p>Loading your Rabbit workspace…</p>
    </div>
  );
}
