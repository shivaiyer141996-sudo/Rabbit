package com.rabbit.aip.commercial;

import com.rabbit.aip.commercial.CommercialTypes.InvoiceStatus;
import com.rabbit.aip.commercial.CommercialTypes.PlanCode;
import com.rabbit.aip.common.domain.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "commercial_invoices")
public class CommercialInvoice extends TenantEntity {

    @Column(name = "invoice_number", nullable = false, length = 80)
    private String invoiceNumber;
    @Enumerated(EnumType.STRING)
    @Column(name = "plan_code", nullable = false, length = 20)
    private PlanCode plan;
    @Column(name = "student_limit", nullable = false)
    private int studentLimit;
    @Column(name = "period_starts_at", nullable = false)
    private Instant periodStartsAt;
    @Column(name = "period_ends_at", nullable = false)
    private Instant periodEndsAt;
    @Column(name = "subtotal_paise", nullable = false)
    private long subtotalPaise;
    @Column(name = "tax_paise", nullable = false)
    private long taxPaise;
    @Column(name = "total_paise", nullable = false)
    private long totalPaise;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InvoiceStatus status;
    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;
    @Column(name = "due_at", nullable = false)
    private Instant dueAt;
    @Column(name = "paid_at")
    private Instant paidAt;
    @Column(length = 1000)
    private String note;
    @Column(name = "created_by_user_id", nullable = false)
    private UUID createdByUserId;
    @Column(name = "updated_by_user_id", nullable = false)
    private UUID updatedByUserId;

    protected CommercialInvoice() {
    }

    public CommercialInvoice(
            UUID organisationId,
            String invoiceNumber,
            PlanCode plan,
            int studentLimit,
            Instant periodStartsAt,
            Instant periodEndsAt,
            long taxPaise,
            Instant issuedAt,
            Instant dueAt,
            String note,
            UUID createdByUserId
    ) {
        this(organisationId, invoiceNumber, plan, studentLimit,
                CommercialTypes.monthlyPricePaise(plan, studentLimit),
                periodStartsAt, periodEndsAt, taxPaise, issuedAt, dueAt,
                note, createdByUserId);
    }

    public CommercialInvoice(
            UUID organisationId,
            String invoiceNumber,
            PlanCode plan,
            int studentLimit,
            long configuredSubtotalPaise,
            Instant periodStartsAt,
            Instant periodEndsAt,
            long taxPaise,
            Instant issuedAt,
            Instant dueAt,
            String note,
            UUID createdByUserId
    ) {
        super(organisationId);
        this.invoiceNumber = invoiceNumber.trim();
        this.plan = plan;
        this.studentLimit = studentLimit;
        this.periodStartsAt = periodStartsAt;
        this.periodEndsAt = periodEndsAt;
        this.subtotalPaise = configuredSubtotalPaise;
        this.taxPaise = taxPaise;
        this.totalPaise = subtotalPaise + taxPaise;
        this.status = InvoiceStatus.ISSUED;
        this.issuedAt = issuedAt;
        this.dueAt = dueAt;
        this.note = note == null || note.isBlank() ? null : note.trim();
        this.createdByUserId = createdByUserId;
        this.updatedByUserId = createdByUserId;
    }

    public void markPaid(Instant paidAt, UUID updatedByUserId) {
        if (status != InvoiceStatus.ISSUED) {
            throw new IllegalStateException("Only an issued invoice can be paid.");
        }
        status = InvoiceStatus.PAID;
        this.paidAt = paidAt;
        this.updatedByUserId = updatedByUserId;
    }

    public void voidInvoice(UUID updatedByUserId) {
        if (status != InvoiceStatus.ISSUED) {
            throw new IllegalStateException("Only an unpaid issued invoice can be voided.");
        }
        status = InvoiceStatus.VOID;
        this.updatedByUserId = updatedByUserId;
    }

    public String getInvoiceNumber() { return invoiceNumber; }
    public PlanCode getPlan() { return plan; }
    public int getStudentLimit() { return studentLimit; }
    public Instant getPeriodStartsAt() { return periodStartsAt; }
    public Instant getPeriodEndsAt() { return periodEndsAt; }
    public long getSubtotalPaise() { return subtotalPaise; }
    public long getTaxPaise() { return taxPaise; }
    public long getTotalPaise() { return totalPaise; }
    public InvoiceStatus getStatus() { return status; }
    public Instant getIssuedAt() { return issuedAt; }
    public Instant getDueAt() { return dueAt; }
    public Instant getPaidAt() { return paidAt; }
    public String getNote() { return note; }
    public UUID getCreatedByUserId() { return createdByUserId; }
    public UUID getUpdatedByUserId() { return updatedByUserId; }
}
