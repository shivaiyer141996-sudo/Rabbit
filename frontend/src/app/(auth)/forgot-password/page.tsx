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
          <button className="button button-primary button-full">
            Send OTP
          </button>
          <Link className="button button-ghost button-full" href="/login">
            <ArrowLeft size={16} /> Back to sign in
          </Link>
          <div className="demo-note">
            OTP delivery is documented for Milestone 2 because it depends on the
            notification worker. No recovery secret is exposed in Milestone 1.
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
