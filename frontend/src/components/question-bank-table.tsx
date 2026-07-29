"use client";

import Link from "next/link";
import { ChevronLeft, ChevronRight, Search } from "lucide-react";
import { useMemo, useState } from "react";
import { StatusBadge } from "./status-badge";
import type { Question } from "@/lib/types";

export function QuestionBankTable({ questions }: { questions: Question[] }) {
  const [query, setQuery] = useState("");
  const [subject, setSubject] = useState("ALL");
  const [status, setStatus] = useState("ALL");

  const filtered = useMemo(
    () =>
      questions.filter((question) => {
        const text = `${question.code} ${question.stem} ${question.topic}`.toLowerCase();
        return (
          text.includes(query.toLowerCase()) &&
          (subject === "ALL" || question.subject === subject) &&
          (status === "ALL" || question.status === status)
        );
      }),
    [query, questions, status, subject],
  );

  return (
    <>
      <div className="toolbar">
        <div className="search-wrap">
          <Search size={17} />
          <input
            className="search-input"
            placeholder="Search code, question, or topic"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            aria-label="Search questions"
          />
        </div>
        <select
          className="filter-select"
          value={subject}
          onChange={(event) => setSubject(event.target.value)}
          aria-label="Filter by subject"
        >
          <option value="ALL">All subjects</option>
          {[...new Set(questions.map((question) => question.subject))].map(
            (value) => (
              <option value={value} key={value}>{value}</option>
            ),
          )}
        </select>
        <select
          className="filter-select"
          value={status}
          onChange={(event) => setStatus(event.target.value)}
          aria-label="Filter by status"
        >
          <option value="ALL">All statuses</option>
          <option value="DRAFT">Draft</option>
          <option value="UNDER_REVIEW">Under review</option>
          <option value="APPROVED">Approved</option>
          <option value="PUBLISHED">Published</option>
          <option value="RETIRED">Retired</option>
        </select>
      </div>

      <div className="table-card">
        <table className="data-table">
          <thead>
            <tr>
              <th>Question</th>
              <th>Type</th>
              <th>Subject / Topic</th>
              <th>Difficulty</th>
              <th>Marks</th>
              <th>Status</th>
              <th>Version</th>
            </tr>
          </thead>
          <tbody>
            {filtered.map((question) => (
              <tr key={question.id}>
                <td className="question-cell">
                  <Link href={`/question-bank/${question.id}`}>
                    <strong>{question.stem}</strong>
                    <span>{question.code} · Updated {question.updatedAt}</span>
                  </Link>
                </td>
                <td>
                  {question.type === "SINGLE_CORRECT"
                    ? "Single Correct"
                    : "Multiple Correct"}
                </td>
                <td>
                  <strong>{question.subject}</strong>
                  <div className="muted">{question.topic}</div>
                </td>
                <td>
                  <StatusBadge
                    status={
                      question.difficulty === "EASY"
                        ? "APPROVED"
                        : question.difficulty === "HARD"
                          ? "RETIRED"
                          : "UNDER_REVIEW"
                    }
                  >
                    {question.difficulty}
                  </StatusBadge>
                </td>
                <td>
                  {question.marks}
                  <span className="muted"> / −{question.negativeMarks}</span>
                </td>
                <td><StatusBadge status={question.status} /></td>
                <td>v{question.version}</td>
              </tr>
            ))}
          </tbody>
        </table>
        <div className="pager">
          <span>Showing {filtered.length} of {questions.length} questions</span>
          <div className="pager-buttons">
            <button className="pager-button" disabled aria-label="Previous page">
              <ChevronLeft size={14} />
            </button>
            <button className="pager-button" aria-current="page">1</button>
            <button className="pager-button" disabled aria-label="Next page">
              <ChevronRight size={14} />
            </button>
          </div>
        </div>
      </div>
    </>
  );
}
