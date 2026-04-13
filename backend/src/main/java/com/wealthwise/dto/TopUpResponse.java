package com.wealthwise.dto;

public class TopUpResponse {

    private double monthlyAmount;   // Current SIP amount (from DB)
    private double withoutTopUp;    // Corpus with flat SIP
    private double withTopUp;       // Corpus with 10% annual step-up
    private double difference;      // Extra wealth from stepping up
    private double stepUpPct;       // Step-up % used (10%)
    private int years;              // Projection horizon

    public TopUpResponse(double monthlyAmount, double withoutTopUp,
                         double withTopUp, double difference,
                         double stepUpPct, int years) {
        this.monthlyAmount = monthlyAmount;
        this.withoutTopUp = withoutTopUp;
        this.withTopUp = withTopUp;
        this.difference = difference;
        this.stepUpPct = stepUpPct;
        this.years = years;
    }

    public double getMonthlyAmount() { return monthlyAmount; }
    public double getWithoutTopUp()  { return withoutTopUp; }
    public double getWithTopUp()     { return withTopUp; }
    public double getDifference()    { return difference; }
    public double getStepUpPct()     { return stepUpPct; }
    public int getYears()            { return years; }
}
