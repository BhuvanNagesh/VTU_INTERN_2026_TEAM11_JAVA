package com.wealthwise.entity;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "fund_holdings")
@Data
public class FundHolding {

    @Id
    @GeneratedValue
    private UUID id;

    private String schemeId;

    private String stockIsin;

    private String stockName;

    private String sector;

    private Double weightPct;

    private LocalDate asOfDate;

    private LocalDate createdAt;

    private LocalDate updatedAt;
}
