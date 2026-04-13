package com.wealthwise.dto;

public class SIPDashboardResponse {

    private int totalActiveSIPs;
    private double monthlyOutflow;
    private String nextStepUpDate;
    private double projectedAmount;
    private String sipStreak;
    private String alert;

    // ✅ EMPTY CONSTRUCTOR (REQUIRED)
    public SIPDashboardResponse() {}

    // ✅ GETTERS + SETTERS

    public int getTotalActiveSIPs() {
        return totalActiveSIPs;
    }

    public void setTotalActiveSIPs(int totalActiveSIPs) {
        this.totalActiveSIPs = totalActiveSIPs;
    }

    public double getMonthlyOutflow() {
        return monthlyOutflow;
    }

    public void setMonthlyOutflow(double monthlyOutflow) {
        this.monthlyOutflow = monthlyOutflow;
    }

    public String getNextStepUpDate() {
        return nextStepUpDate;
    }

    public void setNextStepUpDate(String nextStepUpDate) {
        this.nextStepUpDate = nextStepUpDate;
    }

    public double getProjectedAmount() {
        return projectedAmount;
    }

    public void setProjectedAmount(double projectedAmount) {
        this.projectedAmount = projectedAmount;
    }

    public String getSipStreak() {
        return sipStreak;
    }

    public void setSipStreak(String sipStreak) {
        this.sipStreak = sipStreak;
    }

    public String getAlert() {
        return alert;
    }

    public void setAlert(String alert) {
        this.alert = alert;
    }
}