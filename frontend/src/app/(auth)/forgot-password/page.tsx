import Link from "next/link";
import { ArrowLeft } from "lucide-react";
import { Logo } from "@/components/logo";

export default function ForgotPasswordPage() {
  return (
    <div className="auth-shell">
      <section className="auth-panel">
        <div className="auth-card">
          <Logo />
          <h1>Reset your password</h1>
          <p>
            Enter your registered email. Rabbit will send a six-digit OTP that is
            valid for ten minutes.
          </p>
          <div className="field">
            <label htmlFor="recovery-email">Email address</label>
            <input id="recovery-email" type="email" placeholder="you@institution.edu" />
          </div>
          <button
            aria-describedby="recovery-provider-note"
            className="button button-primary button-full"
            disabled
          >
            Recovery unavailable in pilot
          </button>
          <Link className="button button-ghost button-full" href="/login">
            <ArrowLeft size={16} /> Back to sign in
          </Link>
          <div className="demo-note" id="recovery-provider-note">
            Recovery delivery remains disabled in the controlled pilot until the
            institution approves its email provider, consent wording, and support
            escalation process. No recovery secret is exposed by this page.
          </div>
        </div>
      </section>
      <aside className="auth-visual" aria-hidden="true">
        <div className="visual-content">
          <span className="visual-eyebrow">Secure by design</span>
          <h2>Access that respects every role.</h2>
          <p>
            Tenant-scoped identity, short-lived access tokens, and a complete
            foundation for governed academic workflows.
          </p>
        </div>
      </aside>
    </div>
  );
}
