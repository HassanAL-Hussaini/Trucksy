package org.example.trucksy.DTOOut;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DashBoardAnalyzerDtoOut {
    // Removed bestSpotToPark - now handled by BestSpotAnalyzerDtoOut
    private String adviceBasedOnTheDashboard;
    private String adviceOnItemDescription;
    private Integer totalOrders;
    private Integer totalCompletedOrders;
    private Integer predictedOrders;
    private Double totalRevenue;
    private Double avgOrderValue;
    private Double grossMarginPct;
    private Double repeatCustomerRate;
    private Double conversionRate;
    private Double cancelRate;
    private Double avgPrepTimeSec;
    private Double queueLenAvg;
    private Double tipsTotal;
    private Double weatherImpactIndex;
    private Double eventImpactIndex;
    private Double confidence;
    private Boolean riyadhOnly;
    private String analysisPeriodFrom;
    private String analysisPeriodTo;
}