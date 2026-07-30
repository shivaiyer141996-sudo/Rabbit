"use client";

import Link from "next/link";
import { Download, Plus, Upload } from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import { ErrorState, LoadingState } from "@/components/data-state";
import { PageHeader } from "@/components/page-header";
import { QuestionBankTable } from "@/components/question-bank-table";
import { apiErrorMessage, apiFetch } from "@/lib/api";
import {
  mapQuestion,
  type AcademicCatalog,
  type ApiQuestion,
} from "@/lib/live-types";
import type { Question } from "@/lib/types";

function csvCell(value: unknown) {
  return `"${String(value ?? "").replaceAll('"', '""')}"`;
}

export function LiveQuestionBank() {
  const [questions, setQuestions] = useState<Question[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const [rows, catalog] = await Promise.all([
        apiFetch<ApiQuestion[]>("/questions"),
        apiFetch<AcademicCatalog>("/academic-catalog"),
      ]);
      setQuestions(rows.map((question) => mapQuestion(question, catalog)));
    } catch (requestError) {
      setQuestions([]);
      setError(apiErrorMessage(requestError, "Question Bank could not be loaded."));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    const initial = window.setTimeout(() => void load(), 0);
    return () => window.clearTimeout(initial);
  }, [load]);

  function exportCsv() {
    const header = [
      "Code",
      "Question",
      "Type",
      "Subject",
      "Topic",
      "Difficulty",
      "Marks",
      "Negative marks",
      "Status",
      "Version",
    ];
    const rows = questions.map((question) => [
      question.code,
      question.stem,
      question.type,
      question.subject,
      question.topic,
      question.difficulty,
      question.marks,
      question.negativeMarks,
      question.status,
      question.version,
    ]);
    const blob = new Blob(
      [[header, ...rows].map((row) => row.map(csvCell).join(",")).join("\n")],
      { type: "text/csv;charset=utf-8" },
    );
    const link = document.createElement("a");
    link.href = URL.createObjectURL(blob);
    link.download = `rabbit-question-bank-${new Date().toISOString().slice(0, 10)}.csv`;
    link.click();
    URL.revokeObjectURL(link.href);
  }

  return (
    <div className="page">
      <PageHeader
        eyebrow="Question governance · Live"
        title="Question Bank"
        description="Author, review, and reuse persisted MCQs with complete metadata and controlled lifecycle states."
        actions={
          <>
            <button
              className="button button-secondary"
              disabled={!questions.length}
              onClick={exportCsv}
              type="button"
            >
              <Download size={15} /> Export
            </button>
            <button
              className="button button-secondary"
              disabled
              title="Bulk imports remain off until the pilot template and data-governance controls are approved."
              type="button"
            >
              <Upload size={15} /> Bulk import
            </button>
            <Link className="button button-primary" href="/question-bank/new">
              <Plus size={15} /> New question
            </Link>
          </>
        }
      />
      {loading && <LoadingState label="Loading the live question bank…" />}
      {!loading && error && <ErrorState message={error} retry={() => void load()} />}
      {!loading && !error && <QuestionBankTable questions={questions} />}
    </div>
  );
}
