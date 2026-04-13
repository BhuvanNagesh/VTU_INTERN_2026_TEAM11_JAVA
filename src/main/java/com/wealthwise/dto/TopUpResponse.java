package com.wealthwise.dto;

public class TopUpResponse {

    private double withoutTopUp;
    private double withTopUp;
    private double difference;

    public TopUpResponse(double withoutTopUp, double withTopUp, double difference) {
        this.withoutTopUp = withoutTopUp;
        this.withTopUp = withTopUp;
        this.difference = difference;
    }

    public double getWithoutTopUp() {
        return withoutTopUp;
    }

    public double getWithTopUp() {
        return withTopUp;
    }

    public double getDifference() {
        return difference;
    }
}