package com.wealthwise.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "transactions", indexes = {
    @Index(name = "idx_txn_user", columnList = "user_id"),
    @Index(name = "idx_txn_user_scheme", columnList = "user_id,scheme_amfi_code"),
    @Index(name = "idx_txn_folio", columnList = "user_id,folio_number"),
    @Index(name = "idx_txn_date", columnList = "transaction_date")
})
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_ref", unique = true, nullable = false, length = 50)
    private String transactionRef;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "folio_number", length = 50)
    private String folioNumber;

    @Column(name = "scheme_amfi_code", nullable = false, length = 20)
    private String schemeAmfiCode;

    @Column(name = "scheme_name", length = 500)
    private String schemeName; // Denormalized for display speed

    @Column(name = "transaction_type", nullable = false, length = 30)
    private String transactionType; // PURCHASE_LUMPSUM, PURCHASE_SIP, REDEMPTION, SWITCH_IN, SWITCH_OUT, DIVIDEND_PAYOUT, DIVIDEND_REINVEST, SWP, STP_IN, STP_OUT, REVERSAL

    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    @Column(name = "amount", precision = 18, scale = 4)
    private BigDecimal amount;

    @Column(name = "units", precision = 18, scale = 6)
    private BigDecimal units;

    @Column(name = "nav", precision = 18, scale = 4)
    private BigDecimal nav;

    @Column(name = "stamp_duty", precision = 18, scale = 4)
    private BigDecimal stampDuty;

    @Column(name = "source", length = 30)
    private String source = "MANUAL"; // MANUAL, CAS_IMPORT, SYSTEM_GENERATED

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "reversal_of")
    private Long reversalOf; // References id of original transaction for REVERSAL type

    @Column(name = "switch_pair_id", length = 50)
    private String switchPairId; // Links SWITCH_IN and SWITCH_OUT

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    // ─── Constructors ────────────────────────────────────────────────────────

    public Transaction() {}

    // ─── Getters & Setters ───────────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTransactionRef() { return transactionRef; }
    public void setTransactionRef(String transactionRef) { this.transactionRef = transactionRef; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getFolioNumber() { return folioNumber; }
    public void setFolioNumber(String folioNumber) { this.folioNumber = folioNumber; }

    public String getSchemeAmfiCode() { return schemeAmfiCode; }
    public void setSchemeAmfiCode(String schemeAmfiCode) { this.schemeAmfiCode = schemeAmfiCode; }

    public String getSchemeName() { return schemeName; }
    public void setSchemeName(String schemeName) { this.schemeName = schemeName; }

    public String getTransactionType() { return transactionType; }
    public void setTransactionType(String transactionType) { this.transactionType = transactionType; }

    public LocalDate getTransactionDate() { return transactionDate; }
    public void setTransactionDate(LocalDate transactionDate) { this.transactionDate = transactionDate; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public BigDecimal getUnits() { return units; }
    public void setUnits(BigDecimal units) { this.units = units; }

    public BigDecimal getNav() { return nav; }
    public void setNav(BigDecimal nav) { this.nav = nav; }

    public BigDecimal getStampDuty() { return stampDuty; }
    public void setStampDuty(BigDecimal stampDuty) { this.stampDuty = stampDuty; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public Long getReversalOf() { return reversalOf; }
    public void setReversalOf(Long reversalOf) { this.reversalOf = reversalOf; }

    public String getSwitchPairId() { return switchPairId; }
    public void setSwitchPairId(String switchPairId) { this.switchPairId = switchPairId; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
