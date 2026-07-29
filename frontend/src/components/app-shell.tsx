"use client";

import Image from "next/image";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import {
  BarChart3,
  Bell,
  BookOpenCheck,
  Building2,
  ClipboardList,
  FileQuestion,
  LayoutDashboard,
  LogOut,
  Menu,
  Search,
  Settings,
  Users,
  X,
  type LucideIcon,
} from "lucide-react";
import { useState } from "react";
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
    disabled: true,
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
      <div
        className={`mobile-overlay ${mobileOpen ? "open" : ""}`}
        onClick={() => setMobileOpen(false)}
      />
      <aside className={`sidebar ${mobileOpen ? "open" : ""}`}>
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
              >
                <item.icon size={17} />
                {item.label}
              </Link>
            );
          })}
        </nav>

        <div className="nav-section-label">Manage</div>
        <nav className="nav-list" aria-label="Management navigation">
          <span className="nav-item" aria-disabled="true">
            <BookOpenCheck size={17} />
            Academic setup
            <span className="badge badge-neutral">M2</span>
          </span>
          <span className="nav-item" aria-disabled="true">
            <Settings size={17} />
            Settings
            <span className="badge badge-neutral">M2</span>
          </span>
        </nav>

        <div className="sidebar-footer">
          <div className="user-mini">
            <span className="avatar">AR</span>
            <div>
              <strong>Ananya Rao</strong>
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
          >
            <Menu size={20} />
          </button>
          <div className="topbar-context">
            <strong>Rabbit Demo Academy</strong>
            <span>Academic Year 2026–27 · Chennai</span>
          </div>
        </div>
        <div className="topbar-actions">
          <button className="icon-button" aria-label="Search">
            <Search size={18} />
          </button>
          <button className="icon-button" aria-label="Notifications">
            <Bell size={18} />
          </button>
        </div>
      </header>
      <main>{children}</main>
    </div>
  );
}
