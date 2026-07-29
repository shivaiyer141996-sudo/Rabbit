import Link from "next/link";
import { Download, Plus, Upload } from "lucide-react";
import { PageHeader } from "@/components/page-header";
import { QuestionBankTable } from "@/components/question-bank-table";
import { questions } from "@/lib/demo-data";

export default function QuestionBankPage() {
  return (
    <div className="page">
      <PageHeader
        eyebrow="Question governance"
        title="Question Bank"
        description="Author, review, and reuse MCQs with complete metadata and controlled lifecycle states."
        actions={
          <>
            <button className="button button-secondary">
              <Download size={15} /> Export
            </button>
            <button className="button button-secondary" disabled title="Milestone 2">
              <Upload size={15} /> Bulk import
            </button>
            <Link className="button button-primary" href="/question-bank/new">
              <Plus size={15} /> New question
            </Link>
          </>
        }
      />
      <QuestionBankTable questions={questions} />
    </div>
  );
}
