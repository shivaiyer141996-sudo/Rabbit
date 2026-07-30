"use client";

import { AlertTriangle, LoaderCircle, RefreshCw } from "lucide-react";

export function LoadingState({ label = "Loading live data…" }: { label?: string }) {
  return (
    <div className="data-state" role="status">
      <LoaderCircle className="spin" size={20} />
      <span>{label}</span>
    </div>
  );
}

export function ErrorState({
  message,
  retry,
}: {
  message: string;
  retry?: () => void;
}) {
  return (
    <div className="data-state data-state-error" role="alert">
      <AlertTriangle size={20} />
      <div>
        <strong>Live data could not be loaded</strong>
        <span>{message}</span>
      </div>
      {retry && (
        <button className="button button-secondary" onClick={retry} type="button">
          <RefreshCw size={14} /> Retry
        </button>
      )}
    </div>
  );
}
