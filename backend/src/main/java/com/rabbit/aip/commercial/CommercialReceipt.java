package com.rabbit.aip.commercial;

import com.rabbit.aip.common.domain.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "commercial_receipts")
public class CommercialReceipt extends TenantEntity {

    @Column(name = "payment_id", nullable = false, unique = true)
    private UUID paymentId;
    @Column(name = "invoice_id", nullable = false)
    private UUID invoiceId;
    @Column(name = "receipt_number", nullable = false, length = 80)
    private String receiptNumber;
    @Column(name = "amount_paise", nullable = false)
    private long amountPaise;
    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;
    @Column(name = "issued_by_user_id", nullable = false)
    private UUID issuedByUserId;

    protected CommercialReceipt() {
    }

    public CommercialReceipt(
            UUID organisationId,
            UUID paymentId,
            UUID invoiceId,
            String receiptNumber,
            long amountPaise,
            Instant issuedAt,
            UUID issuedByUserId
    ) {
        super(organisationId);
        this.paymentId = paymentId;
        this.invoiceId = invoiceId;
        this.receiptNumber = receiptNumber;
        this.amountPaise = amountPaise;
        this.issuedAt = issuedAt;
        this.issuedByUserId = issuedByUserId;
    }

    public UUID getPaymentId() { return paymentId; }
    public UUID getInvoiceId() { return invoiceId; }
    public String getReceiptNumber() { return receiptNumber; }
    public long getAmountPaise() { return amountPaise; }
    public Instant getIssuedAt() { return issuedAt; }
    public UUID getIssuedByUserId() { return issuedByUserId; }
}
