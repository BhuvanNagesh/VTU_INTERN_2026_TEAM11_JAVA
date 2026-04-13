package com.wealthwise.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Represents a single stock holding within a mutual fund scheme.
 * Populated by FundHoldingsIngestionService on demand using SEBI-category-mapped
 * real Nifty/BSE index constituents.
 *
 * SEBI mandates category allocation rules (e.g. Large Cap ≥80% from Nifty 100,
 * Mid Cap ≥65% from ranks 101-250) so the index stocks ARE the actual holdings.
 */
@Entity
@Table(name = "fund_holdings", indexes = {
    @Index(name = "idx_fh_scheme", columnList = "scheme_amfi_code"),
    @Index(name = "idx_fh_stock", columnList = "stock_name")
})
public class FundHolding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "scheme_amfi_code", nullable = false, length = 20)
    private String schemeAmfiCode;

    @Column(name = "stock_isin", length = 20)
    private String stockIsin;

    @Column(name = "stock_name", nullable = false, length = 300)
    private String stockName;

    @Column(name = "sector", length = 100)
    private String sector;

    /** Portfolio weight of this stock within the fund (0-100) */
    @Column(name = "weight_pct")
    private Double weightPct;

    /** Date the holdings data was generated (SEBI-category based) */
    @Column(name = "as_of_date")
    private LocalDate asOfDate;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    // ─── Constructors ──────────────────────────────────────────────────────────

    public FundHolding() {}

    public FundHolding(String schemeAmfiCode, String stockIsin, String stockName,
                       String sector, Double weightPct, LocalDate asOfDate) {
        this.schemeAmfiCode = schemeAmfiCode;
        this.stockIsin      = stockIsin;
        this.stockName      = stockName;
        this.sector         = sector;
        this.weightPct      = weightPct;
        this.asOfDate       = asOfDate;
    }

    // ─── Getters & Setters ────────────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSchemeAmfiCode() { return schemeAmfiCode; }
    public void setSchemeAmfiCode(String schemeAmfiCode) { this.schemeAmfiCode = schemeAmfiCode; }

    public String getStockIsin() { return stockIsin; }
    public void setStockIsin(String stockIsin) { this.stockIsin = stockIsin; }

    public String getStockName() { return stockName; }
    public void setStockName(String stockName) { this.stockName = stockName; }

    public String getSector() { return sector; }
    public void setSector(String sector) { this.sector = sector; }

    public Double getWeightPct() { return weightPct; }
    public void setWeightPct(Double weightPct) { this.weightPct = weightPct; }

    public LocalDate getAsOfDate() { return asOfDate; }
    public void setAsOfDate(LocalDate asOfDate) { this.asOfDate = asOfDate; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
