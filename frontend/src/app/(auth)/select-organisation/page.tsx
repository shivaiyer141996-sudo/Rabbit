import type { Metadata } from "next";
import { OrganisationSelector } from "@/components/organisation-selector";
import { Logo } from "@/components/logo";

export const metadata: Metadata = { title: "Select organisation" };

export default function SelectOrganisationPage() {
  return (
    <div className="auth-shell">
      <section className="auth-panel">
        <div className="auth-card">
          <Logo />
          <h1>Choose your workspace</h1>
          <p>
            Your access and data are isolated for the organisation you select.
          </p>
          <OrganisationSelector />
        </div>
      </section>
      <aside className="auth-visual" aria-hidden="true">
        <div className="visual-content">
          <span className="visual-eyebrow">Multi-tenant by default</span>
          <h2>One identity. Clearly separated institutions.</h2>
          <p>
            Rabbit signs the selected organisation into every session so data
            never crosses institutional boundaries.
          </p>
        </div>
      </aside>
    </div>
  );
}
