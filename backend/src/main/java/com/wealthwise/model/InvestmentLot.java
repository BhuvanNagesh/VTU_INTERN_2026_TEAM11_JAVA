package com.wealthwise.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "investment_lots", indexes = {
    @Index(name = "idx_lot_user", columnList = "user_id"),
    @Index(name = "idx_lot_user_scheme", columnList = "user_id,scheme_amfi_code"),
    @Index(name = "idx_lot_folio", columnList = "user_id,folio_number"),
    @Index(name = "idx_lot_date", columnList = "purchase_date")
})
public class InvestmentLot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_id", nullable = false)
    private Long transactionId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "scheme_amfi_code", nullable = false, length = 20)
    private String schemeAmfiCode;

    @Column(name = "scheme_name", length = 500)
    private String schemeName;

    @Column(name = "folio_number", length = 50)
    private String folioNumber;

    @Column(name = "purchase_date", nullable = false)
    private LocalDate purchaseDate;

    @Column(name = "purchase_nav", precision = 18, scale = 4)
    private BigDecimal purchaseNav;

    @Column(name = "purchase_amount", precision = 18, scale = 4)
    private BigDecimal purchaseAmount;

    @Column(name = "units_original", nullable = false, precision = 18, scale = 6)
    private BigDecimal unitsOriginal;

    @Column(name = "units_remaining", nullable = false, precision = 18, scale = 6)
    private BigDecimal unitsRemaining;

    @Column(name = "is_elss")
    private Boolean isElss = false;

    @Column(name = "elss_lock_until")
    private LocalDate elssLockUntil;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    // ─── Constructors ────────────────────────────────────────────────────────

    public InvestmentLot() {}

    // ─── Getters & Setters ───────────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTransactionId() { return transactionId; }
    public void setTransactionId(Long transactionId) { this.transactionId = transactionId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getSchemeAmfiCode() { return schemeAmfiCode; }
    public void setSchemeAmfiCode(String schemeAmfiCode) { this.schemeAmfiCode = schemeAmfiCode; }

    public String getSchemeName() { return schemeName; }
    public void setSchemeName(String schemeName) { this.schemeName = schemeName; }

    public String getFolioNumber() { return folioNumber; }
    public void setFolioNumber(String folioNumber) { this.folioNumber = folioNumber; }

    public LocalDate getPurchaseDate() { return purchaseDate; }
    public void setPurchaseDate(LocalDate purchaseDate) { this.purchaseDate = purchaseDate; }

    public BigDecimal getPurchaseNav() { return purchaseNav; }
    public void setPurchaseNav(BigDecimal purchaseNav) { this.purchaseNav = purchaseNav; }

    public BigDecimal getPurchaseAmount() { return purchaseAmount; }
    public void setPurchaseAmount(BigDecimal purchaseAmount) { this.purchaseAmount = purchaseAmount; }

    public BigDecimal getUnitsOriginal() { return unitsOriginal; }
    public void setUnitsOriginal(BigDecimal unitsOriginal) { this.unitsOriginal = unitsOriginal; }

    public BigDecimal getUnitsRemaining() { return unitsRemaining; }
    public void setUnitsRemaining(BigDecimal unitsRemaining) { this.unitsRemaining = unitsRemaining; }

    public Boolean getIsElss() { return isElss; }
    public void setIsElss(Boolean isElss) { this.isElss = isElss; }

    public LocalDate getElssLockUntil() { return elssLockUntil; }
    public void setElssLockUntil(LocalDate elssLockUntil) { this.elssLockUntil = elssLockUntil; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
