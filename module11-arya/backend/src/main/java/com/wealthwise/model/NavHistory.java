package com.wealthwise.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Persists historical NAV data for each scheme in the database.
 * This is a write-through cache: every NAV fetched from mfapi.in
 * is stored here so data survives server restarts and reduces
 * external API dependency.
 *
 * Unique key: (amfi_code, nav_date) — one NAV per scheme per day.
 */
@Entity
@Table(
    name = "nav_history",
    uniqueConstraints = @UniqueConstraint(columnNames = {"amfi_code", "nav_date"})
)
public class NavHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "amfi_code", nullable = false, length = 20)
    private String amfiCode;

    @Column(name = "nav_date", nullable = false)
    private LocalDate navDate;

    @Column(name = "nav_value", nullable = false, precision = 15, scale = 4)
    private BigDecimal navValue;

    // ─── Constructors ─────────────────────────────────────────────────────────

    public NavHistory() {}

    public NavHistory(String amfiCode, LocalDate navDate, BigDecimal navValue) {
        this.amfiCode = amfiCode;
        this.navDate = navDate;
        this.navValue = navValue;
    }

    // ─── Getters & Setters ────────────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getAmfiCode() { return amfiCode; }
    public void setAmfiCode(String amfiCode) { this.amfiCode = amfiCode; }

    public LocalDate getNavDate() { return navDate; }
    public void setNavDate(LocalDate navDate) { this.navDate = navDate; }

    public BigDecimal getNavValue() { return navValue; }
    public void setNavValue(BigDecimal navValue) { this.navValue = navValue; }
}
