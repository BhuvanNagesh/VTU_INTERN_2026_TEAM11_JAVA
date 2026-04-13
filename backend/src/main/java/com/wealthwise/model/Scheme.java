package com.wealthwise.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "scheme_master", indexes = {
    @Index(name = "idx_scheme_amfi_code", columnList = "amfi_code"),
    @Index(name = "idx_scheme_name", columnList = "scheme_name"),
    @Index(name = "idx_scheme_amc", columnList = "amc_name"),
    @Index(name = "idx_scheme_category", columnList = "broad_category")
})
public class Scheme {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "amfi_code", unique = true, nullable = false, length = 20)
    private String amfiCode;

    @Column(name = "isin_growth", length = 20)
    private String isinGrowth;

    @Column(name = "isin_idcw", length = 20)
    private String isinIdcw;

    @Column(name = "scheme_name", nullable = false, length = 500)
    private String schemeName;

    @Column(name = "amc_name", length = 200)
    private String amcName;

    @Column(name = "fund_family", length = 200)
    private String fundFamily;

    @Column(name = "plan_type", length = 20)
    private String planType; // DIRECT, REGULAR, UNKNOWN

    @Column(name = "option_type", length = 30)
    private String optionType; // GROWTH, IDCW_PAYOUT, IDCW_REINVESTMENT, UNKNOWN

    @Column(name = "fund_type", length = 30)
    private String fundType; // OPEN_ENDED, CLOSE_ENDED, INTERVAL, UNKNOWN

    @Column(name = "sebi_category", length = 100)
    private String sebiCategory;

    @Column(name = "broad_category", length = 20)
    private String broadCategory; // EQUITY, DEBT, HYBRID, SOLUTION, OTHER

    @Column(name = "risk_level")
    private Integer riskLevel; // 1-6 SEBI riskometer

    @Column(name = "last_nav", precision = 18, scale = 4)
    private BigDecimal lastNav;

    @Column(name = "last_nav_date")
    private LocalDate lastNavDate;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    // ─── Constructors ────────────────────────────────────────────────────────

    public Scheme() {}

    // ─── Getters & Setters ───────────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getAmfiCode() { return amfiCode; }
    public void setAmfiCode(String amfiCode) { this.amfiCode = amfiCode; }

    public String getIsinGrowth() { return isinGrowth; }
    public void setIsinGrowth(String isinGrowth) { this.isinGrowth = isinGrowth; }

    public String getIsinIdcw() { return isinIdcw; }
    public void setIsinIdcw(String isinIdcw) { this.isinIdcw = isinIdcw; }

    public String getSchemeName() { return schemeName; }
    public void setSchemeName(String schemeName) { this.schemeName = schemeName; }

    public String getAmcName() { return amcName; }
    public void setAmcName(String amcName) { this.amcName = amcName; }

    public String getFundFamily() { return fundFamily; }
    public void setFundFamily(String fundFamily) { this.fundFamily = fundFamily; }

    public String getPlanType() { return planType; }
    public void setPlanType(String planType) { this.planType = planType; }

    public String getOptionType() { return optionType; }
    public void setOptionType(String optionType) { this.optionType = optionType; }

    public String getFundType() { return fundType; }
    public void setFundType(String fundType) { this.fundType = fundType; }

    public String getSebiCategory() { return sebiCategory; }
    public void setSebiCategory(String sebiCategory) { this.sebiCategory = sebiCategory; }

    public String getBroadCategory() { return broadCategory; }
    public void setBroadCategory(String broadCategory) { this.broadCategory = broadCategory; }

    public Integer getRiskLevel() { return riskLevel; }
    public void setRiskLevel(Integer riskLevel) { this.riskLevel = riskLevel; }

    public BigDecimal getLastNav() { return lastNav; }
    public void setLastNav(BigDecimal lastNav) { this.lastNav = lastNav; }

    public LocalDate getLastNavDate() { return lastNavDate; }
    public void setLastNavDate(LocalDate lastNavDate) { this.lastNavDate = lastNavDate; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
