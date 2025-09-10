package org.example.trucksy.DTOOut;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BestSpotAnalyzerDtoOut {
    private String primarySpot;
    private String secondarySpot;
    private String thirdSpot;
    private String spotAnalysisReason;
    private String timeRecommendations;
    private String seasonalAdvice;
    private String competitionAnalysis;
    private Double footTrafficScore;
    private Double accessibilityScore;
    private Double parkingAvailabilityScore;
    private Double proximityToOfficesScore;
    private Double proximityToUniversitiesScore;
    private Double proximityToMallsScore;
    private Double overallLocationScore;
    private Double confidence;
    private Boolean riyadhOnly;
    private String analysisDate;
}