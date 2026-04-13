package com.wealthwise.service;

import com.wealthwise.dto.TopUpResponse;
import com.wealthwise.dto.SIPDashboardResponse;
import com.wealthwise.dto.SIPComparisonResponse;
import org.springframework.stereotype.Service;

@Service
public class SIPService {

    // 🔹 DASHBOARD
    public SIPDashboardResponse getDashboard() {

        SIPDashboardResponse response = new SIPDashboardResponse();

        response.setTotalActiveSIPs(5);
        response.setMonthlyOutflow(10000);
        response.setNextStepUpDate("2026-05-01");
        response.setProjectedAmount(2500000);
        response.setSipStreak("12 months");
        response.setAlert("All good");

        return response;
    }

    // 🔹 COMPARE
    public SIPComparisonResponse compare() {

        double sipValue = 500000;
        double lumpsumValue = 450000;

        String winner = sipValue > lumpsumValue ? "SIP" : "Lumpsum";
        double difference = Math.abs(sipValue - lumpsumValue);

        return new SIPComparisonResponse(
                sipValue,
                lumpsumValue,
                winner,
                difference
        );
    }

    // 🔹 OPTIMIZE
    public String optimize() {
        return "Best date is 5th of every month";
    }

    // 🔹 TOP-UP (NEW MODULE F13.5)
    public TopUpResponse calculateTopUp() {

        double monthlySIP = 10000;
        double annualReturn = 0.12;
        int years = 20;

        double monthlyRate = annualReturn / 12;
        int months = years * 12;

        // WITHOUT TOP-UP
        double withoutTopUp = 0;
        for (int i = 0; i < months; i++) {
            withoutTopUp = (withoutTopUp + monthlySIP) * (1 + monthlyRate);
        }

        // WITH TOP-UP (10% yearly increase)
        double withTopUp = 0;
        double currentSIP = monthlySIP;

        for (int year = 1; year <= years; year++) {
            for (int m = 0; m < 12; m++) {
                withTopUp = (withTopUp + currentSIP) * (1 + monthlyRate);
            }
            currentSIP *= 1.10;
        }

        double difference = withTopUp - withoutTopUp;

        return new TopUpResponse(
                withoutTopUp,
                withTopUp,
                difference
        );
    }
}