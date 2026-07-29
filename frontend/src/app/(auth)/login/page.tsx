import type { Metadata } from "next";
import { LoginForm } from "@/components/login-form";
import { Logo } from "@/components/logo";

export const metadata: Metadata = { title: "Sign in" };

export default function LoginPage() {
  return (
    <div className="auth-shell">
      <section className="auth-panel">
        <div className="auth-card">
          <Logo />
          <h1>Welcome back</h1>
          <p>
            Sign in to understand every assessment, every learning gap, and every
            next step.
          </p>
          <LoginForm />
        </div>
      </section>
      <aside className="auth-visual" aria-label="Rabbit product promise">
        <div className="visual-content">
          <span className="visual-eyebrow">Student progress, understood</span>
          <h2>Turn every assessment into meaningful action.</h2>
          <p>
            Govern questions, conduct reliable assessments, and give educators
            the clarity to intervene while it still matters.
          </p>
          <div className="visual-metrics">
            <div className="visual-metric">
              <strong>MCQ</strong>
              <span>Focused Release 1.0</span>
            </div>
            <div className="visual-metric">
              <strong>30 sec</strong>
              <span>Response auto-save</span>
            </div>
            <div className="visual-metric">
              <strong>100%</strong>
              <span>Tenant isolated</span>
            </div>
          </div>
        </div>
      </aside>
    </div>
  );
}
