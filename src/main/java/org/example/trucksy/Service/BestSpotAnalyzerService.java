package org.example.trucksy.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.example.trucksy.Api.ApiException;
import org.example.trucksy.DTOOut.BestSpotAnalyzerDtoOut;
import org.example.trucksy.Model.Dashboard;
import org.example.trucksy.Model.FoodTruck;
import org.example.trucksy.Model.Order;
import org.example.trucksy.Repository.DashboardRepository;
import org.example.trucksy.Repository.FoodTruckRepository;
import org.example.trucksy.Repository.OrderRepository;
import org.example.trucksy.Repository.OwnerRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BestSpotAnalyzerService {

    private final DashboardRepository dashboardRepository;
    private final OrderRepository orderRepository;
    private final FoodTruckRepository foodTruckRepository;
    private final OwnerRepository ownerRepository;
    private final AiService aiService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public BestSpotAnalyzerDtoOut analyzeBestSpotByOwnerId(Integer ownerId) {
        // 1) Verify owner exists and is subscribed
        var owner = ownerRepository.findOwnerById(ownerId);
        if (owner == null) {
            throw new ApiException("Owner not found");
        }
        if (!owner.getSubscribed()) {
            throw new ApiException("Owner is not subscribed. AI services are only available for subscribers");
        }

        // 2) Get dashboard data
        Dashboard dashboard = dashboardRepository.findDashboardById(ownerId);
        if (dashboard == null) {
            throw new ApiException("Dashboard not found for this owner");
        }

        // 3) Get additional business data for analysis
        List<FoodTruck> foodTrucks = foodTruckRepository.findFoodTruckByOwnerId(ownerId);
        if (foodTrucks.isEmpty()) {
            throw new ApiException("No food trucks found for this owner");
        }

        // Get recent orders for deeper analysis - get orders by owner, not client
        List<Order> recentOrders = orderRepository.findByOwnerIdOrderByOrderDateDesc(ownerId)
                .stream()
                .limit(100)
                .collect(Collectors.toList());

        if (recentOrders.isEmpty()) {
            throw new ApiException("No recent orders found for analysis");
        }

        // 4) Build prompt for AI analysis
        String prompt = buildBestSpotAnalysisPrompt(dashboard, foodTrucks, recentOrders);

        // 5) Call AI service
        String aiResponse = aiService.chat(prompt);
        if (aiResponse == null || aiResponse.isBlank()) {
            throw new ApiException("AI returned empty response");
        }

        // 6) Parse AI response
        return parseAiResponse(aiResponse);
    }

    private String buildBestSpotAnalysisPrompt(Dashboard dashboard, List<FoodTruck> foodTrucks, List<Order> recentOrders) {
        // Calculate metrics from orders for location analysis
        double totalRevenue = recentOrders.stream()
                .filter(o -> "COMPLETED".equals(o.getStatus()))
                .mapToDouble(Order::getTotalPrice)
                .sum();

        long completedOrders = recentOrders.stream()
                .filter(o -> "COMPLETED".equals(o.getStatus()))
                .count();

        double avgOrderValue = completedOrders > 0 ? totalRevenue / completedOrders : 0.0;

        // Build food truck info
        StringBuilder trucksInfo = new StringBuilder();
        for (int i = 0; i < foodTrucks.size(); i++) {
            FoodTruck truck = foodTrucks.get(i);
            trucksInfo.append(String.format(
                    "Truck %d: Name=\"%s\", Category=\"%s\", Status=\"%s\", Current Location=\"%s, %s\"\n",
                    i + 1,
                    truck.getName() != null ? truck.getName() : "Unnamed",
                    truck.getCategory() != null ? truck.getCategory() : "Unknown",
                    truck.getStatus() != null ? truck.getStatus() : "Unknown",
                    truck.getCity() != null ? truck.getCity() : "Unknown",
                    truck.getDistrict() != null ? truck.getDistrict() : "Unknown"
            ));
        }

        return String.format("""
        You are a professional location strategist specializing in food truck placement in Riyadh, Saudi Arabia.

        STRICT OUTPUT FORMAT:
        Return ONLY a single JSON object with EXACTLY these fields:
        {
          "primarySpot": "<string>",
          "secondarySpot": "<string>",
          "thirdSpot": "<string>",
          "spotAnalysisReason": "<string up to 200 words>",
          "timeRecommendations": "<string>",
          "seasonalAdvice": "<string>",
          "competitionAnalysis": "<string>",
          "footTrafficScore": <number with 2 decimal places>,
          "accessibilityScore": <number with 2 decimal places>,
          "parkingAvailabilityScore": <number with 2 decimal places>,
          "proximityToOfficesScore": <number with 2 decimal places>,
          "proximityToUniversitiesScore": <number with 2 decimal places>,
          "proximityToMallsScore": <number with 2 decimal places>,
          "overallLocationScore": <number with 2 decimal places>,
          "confidence": <number with 2 decimal places>,
          "riyadhOnly": true,
          "analysisDate": "<YYYY-MM-DD>"
        }

        RIYADH LOCATION ANALYSIS CONTEXT:
        This analysis is exclusively for food truck operations in Riyadh, Kingdom of Saudi Arabia. Consider:

        KEY RIYADH DISTRICTS FOR FOOD TRUCKS:
        - King Fahd District: Business hub, high office density
        - Olaya District: Commercial center, shopping areas
        - Diplomatic Quarter (DQ): International community, embassies
        - Al Malaz: University area, young demographics
        - Al Naseem: Residential with commercial strips
        - King Abdullah Financial District (KAFD): New business district
        - Tahlia Street: Entertainment and dining corridor
        - Prince Sultan Street: Mixed commercial/residential
        - Al Wuroud: Growing commercial area
        - Exit 5-18 areas: Highway accessibility

        RIYADH-SPECIFIC FACTORS:
        - Extreme summer heat (May-September): Covered spots essential
        - Prayer times impact: 5 daily prayer breaks affect foot traffic
        - Weekend patterns: Friday-Saturday weekends
        - Working hours: 8 AM - 5 PM government, 9 AM - 9 PM private
        - Lunch rush: 12 PM - 2 PM
        - Evening activity: 6 PM - 10 PM (winter), 8 PM - 12 AM (summer)
        - Ramadan impact: Iftar timing changes, suhoor opportunities
        - University areas: King Saud University, Princess Nourah University
        - Major malls: Riyadh Gallery, Kingdom Centre, Panorama Mall
        - Business clusters: King Abdullah Financial District, Olaya towers

        SCORING CRITERIA (0.0 - 10.0):
        - Foot Traffic: Peak hours pedestrian count potential
        - Accessibility: Vehicle access, parking for customers
        - Parking Availability: Space for truck + customer parking
        - Proximity to Offices: Distance to business districts
        - Proximity to Universities: Student market access
        - Proximity to Malls: Shopping area foot traffic
        - Overall Score: Weighted average based on truck category

        BUSINESS PERFORMANCE DATA:
        Current Performance:
        - Total Orders: %d
        - Completed Orders: %d
        - Total Revenue: %.2f SAR
        - Average Order Value: %.2f SAR

        FOOD TRUCK DETAILS:
        %s

        ANALYSIS REQUIREMENTS:
        1. "primarySpot": Best recommended location with specific district/street
        2. "secondarySpot": Alternative high-potential location
        3. "thirdSpot": Backup location option
        4. "spotAnalysisReason": Detailed explanation for recommendations (max 200 words)
        5. "timeRecommendations": Best operating hours for each spot
        6. "seasonalAdvice": Summer vs winter location strategies
        7. "competitionAnalysis": Competition density assessment
        8. All scores based on truck category and current performance
        9. Consider Saudi cultural preferences and dining habits
        10. Factor in local regulations and permit requirements

        Focus on maximizing revenue potential while considering practical constraints like permits, competition, and seasonal variations in Riyadh.

        Return only the JSON object.
        """,
                dashboard.getTotalOrders() != null ? dashboard.getTotalOrders() : 0,
                completedOrders,
                totalRevenue,
                avgOrderValue,
                trucksInfo.toString()
        );
    }

    private BestSpotAnalyzerDtoOut parseAiResponse(String aiResponse) {
        JsonNode node;
        try {
            node = objectMapper.readTree(aiResponse.trim());
        } catch (Exception e) {
            throw new ApiException("AI response is not valid JSON: " + aiResponse);
        }

        // Extract and validate all fields
        String primarySpot = node.path("primarySpot").asText("");
        String secondarySpot = node.path("secondarySpot").asText("");
        String thirdSpot = node.path("thirdSpot").asText("");
        String spotAnalysisReason = node.path("spotAnalysisReason").asText("");
        String timeRecommendations = node.path("timeRecommendations").asText("");
        String seasonalAdvice = node.path("seasonalAdvice").asText("");
        String competitionAnalysis = node.path("competitionAnalysis").asText("");

        Double footTrafficScore = node.path("footTrafficScore").asDouble(-1.0);
        Double accessibilityScore = node.path("accessibilityScore").asDouble(-1.0);
        Double parkingAvailabilityScore = node.path("parkingAvailabilityScore").asDouble(-1.0);
        Double proximityToOfficesScore = node.path("proximityToOfficesScore").asDouble(-1.0);
        Double proximityToUniversitiesScore = node.path("proximityToUniversitiesScore").asDouble(-1.0);
        Double proximityToMallsScore = node.path("proximityToMallsScore").asDouble(-1.0);
        Double overallLocationScore = node.path("overallLocationScore").asDouble(-1.0);
        Double confidence = node.path("confidence").asDouble(-1.0);
        Boolean riyadhOnly = node.path("riyadhOnly").asBoolean();
        String analysisDate = node.path("analysisDate").asText("");

        // Validate numeric fields
        if (footTrafficScore < 0 || footTrafficScore > 10) throw new ApiException("AI: footTrafficScore must be 0-10");
        if (accessibilityScore < 0 || accessibilityScore > 10) throw new ApiException("AI: accessibilityScore must be 0-10");
        if (parkingAvailabilityScore < 0 || parkingAvailabilityScore > 10) throw new ApiException("AI: parkingAvailabilityScore must be 0-10");
        if (proximityToOfficesScore < 0 || proximityToOfficesScore > 10) throw new ApiException("AI: proximityToOfficesScore must be 0-10");
        if (proximityToUniversitiesScore < 0 || proximityToUniversitiesScore > 10) throw new ApiException("AI: proximityToUniversitiesScore must be 0-10");
        if (proximityToMallsScore < 0 || proximityToMallsScore > 10) throw new ApiException("AI: proximityToMallsScore must be 0-10");
        if (overallLocationScore < 0 || overallLocationScore > 10) throw new ApiException("AI: overallLocationScore must be 0-10");
        if (confidence < 0 || confidence > 100) throw new ApiException("AI: confidence must be 0-100");
        if (!riyadhOnly) throw new ApiException("AI: riyadhOnly must be true");

        // Provide defaults for string fields
        if (primarySpot.isBlank()) {
            primarySpot = "King Fahd District - Business Area";
        }
        if (secondarySpot.isBlank()) {
            secondarySpot = "Olaya District - Commercial Center";
        }
        if (thirdSpot.isBlank()) {
            thirdSpot = "Tahlia Street - Entertainment Area";
        }
        if (spotAnalysisReason.isBlank()) {
            spotAnalysisReason = "Recommended locations are based on high foot traffic, business density, and accessibility in Riyadh.";
        }
        if (timeRecommendations.isBlank()) {
            timeRecommendations = "Lunch: 12 PM - 2 PM, Evening: 6 PM - 10 PM";
        }
        if (seasonalAdvice.isBlank()) {
            seasonalAdvice = "Summer: Focus on covered areas and evening hours. Winter: Outdoor locations acceptable all day.";
        }
        if (competitionAnalysis.isBlank()) {
            competitionAnalysis = "Moderate competition in business districts, lower in residential areas.";
        }
        if (analysisDate.isBlank()) {
            analysisDate = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        }

        // Limit field lengths
        primarySpot = limitChars(primarySpot, 100);
        secondarySpot = limitChars(secondarySpot, 100);
        thirdSpot = limitChars(thirdSpot, 100);
        spotAnalysisReason = limitWords(spotAnalysisReason, 200);
        timeRecommendations = limitChars(timeRecommendations, 150);
        seasonalAdvice = limitChars(seasonalAdvice, 150);
        competitionAnalysis = limitChars(competitionAnalysis, 150);

        return new BestSpotAnalyzerDtoOut(
                primarySpot,
                secondarySpot,
                thirdSpot,
                spotAnalysisReason,
                timeRecommendations,
                seasonalAdvice,
                competitionAnalysis,
                footTrafficScore,
                accessibilityScore,
                parkingAvailabilityScore,
                proximityToOfficesScore,
                proximityToUniversitiesScore,
                proximityToMallsScore,
                overallLocationScore,
                confidence,
                riyadhOnly,
                analysisDate
        );
    }

    private static String limitWords(String text, int maxWords) {
        if (text == null || text.isBlank()) return "";
        String[] words = text.trim().split("\\s+");
        if (words.length <= maxWords) return text.trim();
        return String.join(" ", java.util.Arrays.copyOfRange(words, 0, maxWords)).trim();
    }

    private static String limitChars(String text, int maxChars) {
        if (text == null) return "";
        return text.length() <= maxChars ? text : text.substring(0, maxChars);
    }
}