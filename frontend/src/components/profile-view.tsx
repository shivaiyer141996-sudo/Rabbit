"use client";

import { Mail, ShieldCheck, UserCircle } from "lucide-react";
import { useEffect, useState } from "react";
import { ErrorState, LoadingState } from "@/components/data-state";
import { PageHeader } from "@/components/page-header";
import { apiErrorMessage, apiFetch } from "@/lib/api";
import type { MeProfile } from "@/lib/live-types";

export function ProfileView() {
  const [profile, setProfile] = useState<MeProfile | null>(null);
  const [error, setError] = useState("");
  useEffect(() => {
    let active = true;
    apiFetch<MeProfile>("/auth/me").then((value) => active && setProfile(value)).catch((requestError) => active && setError(apiErrorMessage(requestError, "Profile could not be loaded.")));
    return () => { active = false; };
  }, []);
  if (!profile && !error) return <div className="page"><LoadingState label="Loading your profile…" /></div>;
  if (!profile) return <div className="page"><ErrorState message={error} /></div>;
  return <div className="page"><PageHeader eyebrow="Personal account" title={`${profile.firstName} ${profile.lastName}`} description="Your student identity and current organisation membership." /><section className="panel profile-card"><UserCircle size={40} /><dl className="definition-list"><div className="definition-row"><dt><Mail size={14} /> Email</dt><dd>{profile.email}</dd></div><div className="definition-row"><dt><ShieldCheck size={14} /> Role</dt><dd>{profile.role.replaceAll("_", " ")}</dd></div><div className="definition-row"><dt>Organisation</dt><dd>{profile.organisationName} · {profile.organisationCode}</dd></div><div className="definition-row"><dt>Time zone</dt><dd>{profile.timezone}</dd></div></dl></section></div>;
}
