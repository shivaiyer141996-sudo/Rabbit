"use client";

import Image from "next/image";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import {
  Activity,
  BarChart3,
  BookOpenCheck,
  Building2,
  ClipboardList,
  FileQuestion,
  LayoutDashboard,
  LogOut,
  Menu,
  Rocket,
  Search,
  ShieldCheck,
  Settings,
  Users,
  X,
  type LucideIcon,
} from "lucide-react";
import { useEffect, useState } from "react";
import { NotificationPopover } from "@/components/notification-popover";
import { apiFetch } from "@/lib/api";
import { initials, type MeProfile } from "@/lib/live-types";
import type { UserRole } from "@/lib/types";

interface NavigationItem {
  href: string;
  label: string;
  icon: LucideIcon;
  roles: Array<UserRole | "ALL">;
  disabled?: boolean;
}

const navigation: NavigationItem[] = [
  { href: "/dashboard", label: "Dashboard", icon: LayoutDashboard, roles: ["ALL"] },
  {
    href: "/question-bank",
    label: "Question Bank",
    icon: FileQuestion,
    roles: ["SUPER_ADMIN", "ORG_ADMIN", "ACADEMIC_HEAD", "FACULTY", "REVIEWER"],
  },
  {
    href: "/approvals",
    label: "Approvals",
    icon: BookOpenCheck,
    roles: ["SUPER_ADMIN", "ORG_ADMIN", "ACADEMIC_HEAD", "REVIEWER"],
  },
  {
    href: "/assessments",
    label: "Assessments",
    icon: ClipboardList,
    roles: ["SUPER_ADMIN", "ORG_ADMIN", "ACADEMIC_HEAD", "FACULTY"],
  },
  {
    href: "/users",
    label: "Users",
    icon: Users,
    roles: ["SUPER_ADMIN", "ORG_ADMIN"],
  },
  {
    href: "/organisations",
    label: "Organisation",
    icon: Building2,
    roles: ["SUPER_ADMIN", "ORG_ADMIN"],
  },
  {
    href: "/reports",
    label: "Reports",
    icon: BarChart3,
    roles: ["SUPER_ADMIN", "ORG_ADMIN", "ACADEMIC_HEAD", "FACULTY"],
  },
  {
    href: "/audit-logs",
    label: "Audit Logs",
    icon: ShieldCheck,
    roles: ["SUPER_ADMIN", "ORG_ADMIN"],
  },
  {
    href: "/operations",
    label: "Operations",
    icon: Activity,
    roles: ["SUPER_ADMIN", "ORG_ADMIN"],
  },
  {
    href: "/pilot-readiness",
    label: "Pilot readiness",
    icon: Rocket,
    roles: ["SUPER_ADMIN", "ORG_ADMIN"],
  },
];

export function AppShell({
  children,
  role,
}: {
  children: React.ReactNode;
  role: UserRole;
}) {
  const pathname = usePathname();
  const router = useRouter();
  const [mobileOpen, setMobileOpen] = useState(false);
  const [profile, setProfile] = useState<MeProfile | null>(null);

  useEffect(() => {
    let active = true;
    apiFetch<MeProfile>("/auth/me")
      .then((value) => {
        if (active) setProfile(value);
      })
      .catch(() => {
        if (active) setProfile(null);
      });
    return () => {
      active = false;
    };
  }, []);

  const visibleNavigation = navigation.filter(
    (item) => item.roles.includes("ALL") || item.roles.includes(role),
  );

  async function logout() {
    await fetch("/gateway/auth/logout", { method: "POST" }).catch(() => undefined);
    router.replace("/login");
    router.refresh();
  }

  return (
    <div className="app-frame">
      <a className="skip-link" href="#main-content">Skip to main content</a>
      <div
        className={`mobile-overlay ${mobileOpen ? "open" : ""}`}
        onClick={() => setMobileOpen(false)}
        aria-hidden="true"
      />
      <aside
        className={`sidebar ${mobileOpen ? "open" : ""}`}
        id="workspace-navigation"
      >
        <div className="sidebar-brand">
          <Image src="/rabbit-mark.svg" width={42} height={42} alt="" />
          <div className="brand-copy">
            <strong>Rabbit AiP</strong>
            <span>Student progress, understood</span>
          </div>
          <button
            className="icon-button mobile-menu"
            onClick={() => setMobileOpen(false)}
            aria-label="Close navigation"
          >
            <X size={18} />
          </button>
        </div>

        <div className="nav-section-label">Workspace</div>
        <nav className="nav-list" aria-label="Main navigation">
          {visibleNavigation.map((item) => {
            const active =
              pathname === item.href ||
              (item.href !== "/dashboard" && pathname.startsWith(item.href));
            if (item.disabled) {
              return (
                <span className="nav-item" key={item.href} aria-disabled="true">
                  <item.icon size={17} />
                  {item.label}
                  <span className="badge badge-neutral">M2</span>
                </span>
              );
            }
            return (
              <Link
                className={`nav-item ${active ? "active" : ""}`}
                href={item.href}
                key={item.href}
                onClick={() => setMobileOpen(false)}
                aria-current={active ? "page" : undefined}
              >
                <item.icon size={17} />
                {item.label}
              </Link>
            );
          })}
        </nav>

        {(["SUPER_ADMIN", "ORG_ADMIN"] as UserRole[]).includes(role) && (
          <>
            <div className="nav-section-label">Manage</div>
            <nav className="nav-list" aria-label="Management navigation">
              <Link
                className={`nav-item ${pathname.startsWith("/settings") ? "active" : ""}`}
                href="/settings"
              >
                <Settings size={17} />
                Settings
              </Link>
            </nav>
          </>
        )}

        <div className="sidebar-footer">
          <div className="user-mini">
            <span className="avatar">
              {profile ? initials(profile.firstName, profile.lastName) : "…"}
            </span>
            <div>
              <strong>
                {profile
                  ? `${profile.firstName} ${profile.lastName}`
                  : "Loading profile…"}
              </strong>
              <span>{role.replaceAll("_", " ")}</span>
            </div>
            <button className="icon-button" onClick={logout} aria-label="Log out">
              <LogOut size={16} />
            </button>
          </div>
        </div>
      </aside>

      <header className="topbar">
        <div className="topbar-actions">
          <button
            className="icon-button mobile-menu"
            onClick={() => setMobileOpen(true)}
            aria-label="Open navigation"
            aria-controls="workspace-navigation"
            aria-expanded={mobileOpen}
          >
            <Menu size={20} />
          </button>
          <div className="topbar-context">
            <strong>{profile?.organisationName ?? "Loading organisation…"}</strong>
            <span>
              {profile
                ? `${profile.organisationCode} · ${profile.timezone}`
                : "Validating live session"}
            </span>
          </div>
        </div>
        <div className="topbar-actions">
          <button className="icon-button" aria-label="Search">
            <Search size={18} />
          </button>
          <NotificationPopover />
        </div>
      </header>
      <main id="main-content" tabIndex={-1}>{children}</main>
    </div>
  );
}
