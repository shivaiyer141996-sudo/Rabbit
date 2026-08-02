package com.rabbit.aip.commercial;

import com.rabbit.aip.commercial.CommercialTypes.PaymentMethod;
import com.rabbit.aip.commercial.CommercialTypes.PaymentStatus;
import com.rabbit.aip.common.domain.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "commercial_payments")
public class CommercialPayment extends TenantEntity {

    @Column(name = "invoice_id", nullable = false)
    private UUID invoiceId;
    @Column(name = "payment_reference", nullable = false, length = 120)
    private String paymentReference;
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 30)
    private PaymentMethod paymentMethod;
    @Column(name = "amount_paise", nullable = false)
    private long amountPaise;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;
    @Column(name = "paid_at", nullable = false)
    private Instant paidAt;
    @Column(name = "recorded_by_user_id", nullable = false)
    private UUID recordedByUserId;
    @Column(length = 1000)
    private String note;

    protected CommercialPayment() {
    }

    public CommercialPayment(
            UUID organisationId,
            UUID invoiceId,
            String paymentReference,
            PaymentMethod paymentMethod,
            long amountPaise,
            Instant paidAt,
            UUID recordedByUserId,
            String note
    ) {
        super(organisationId);
        this.invoiceId = invoiceId;
        this.paymentReference = paymentReference.trim();
        this.paymentMethod = paymentMethod;
        this.amountPaise = amountPaise;
        this.status = PaymentStatus.RECORDED;
        this.paidAt = paidAt;
        this.recordedByUserId = recordedByUserId;
        this.note = note == null || note.isBlank() ? null : note.trim();
    }

    public UUID getInvoiceId() { return invoiceId; }
    public String getPaymentReference() { return paymentReference; }
    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public long getAmountPaise() { return amountPaise; }
    public PaymentStatus getStatus() { return status; }
    public Instant getPaidAt() { return paidAt; }
    public UUID getRecordedByUserId() { return recordedByUserId; }
    public String getNote() { return note; }
}
