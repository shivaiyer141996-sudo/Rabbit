import type { Metadata } from "next";
import { ActivationForm } from "@/components/activation-form";
import { Logo } from "@/components/logo";

export const metadata: Metadata = { title: "Activate account" };

export default function ActivatePage() {
  return (
    <div className="auth-shell">
      <section className="auth-panel">
        <div className="auth-card">
          <Logo />
          <h1>Activate your account</h1>
          <p>
            Confirm your invitation and create the password you will use to sign
            in securely.
          </p>
          <ActivationForm />
        </div>
      </section>
      <aside className="auth-visual" aria-label="Rabbit secure account activation">
        <div className="visual-content">
          <span className="visual-eyebrow">One secure first step</span>
          <h2>Your learning workspace is ready.</h2>
          <p>
            Invitation links expire and work only once. Your new password is
            protected with the same controls used across Rabbit.
          </p>
        </div>
      </aside>
    </div>
  );
}
