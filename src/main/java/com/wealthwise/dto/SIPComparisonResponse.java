package com.wealthwise.dto;

public class SIPComparisonResponse {

    private double sipValue;
    private double lumpsumValue;
    private String winner;
    private double difference;

    public SIPComparisonResponse(double sipValue, double lumpsumValue,
                                 String winner, double difference) {
        this.sipValue = sipValue;
        this.lumpsumValue = lumpsumValue;
        this.winner = winner;
        this.difference = difference;
    }

    public double getSipValue() { return sipValue; }
    public double getLumpsumValue() { return lumpsumValue; }
    public String getWinner() { return winner; }
    public double getDifference() { return difference; }
}