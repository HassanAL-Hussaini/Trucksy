package org.example.trucksy.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.example.trucksy.Api.ApiException;
import org.example.trucksy.DTOOut.DashBoardAnalyzerDtoOut;
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
public class AiDashboardAnalyzerService {

    private final DashboardRepository dashboardRepository;
    private final OrderRepository orderRepository;
    private final FoodTruckRepository foodTruckRepository;
    private final OwnerRepository ownerRepository;
    private final AiService aiService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DashBoardAnalyzerDtoOut analyzeDashboardByOwnerId(Integer ownerId) {
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
        String prompt = buildDashboardAnalysisPrompt(dashboard, foodTrucks, recentOrders);

        // 5) Call AI service
        String aiResponse = aiService.chat(prompt);
        if (aiResponse == null || aiResponse.isBlank()) {
            throw new ApiException("AI returned empty response");
        }

        // 6) Parse AI response
        return parseAiResponse(aiResponse);
    }

    private String buildDashboardAnalysisPrompt(Dashboard dashboard, List<FoodTruck> foodTrucks, List<Order> recentOrders) {
        // Calculate additional metrics from orders
        double totalRevenue = recentOrders.stream()
                .filter(o -> "COMPLETED".equals(o.getStatus()))
                .mapToDouble(Order::getTotalPrice)
                .sum();

        long completedOrders = recentOrders.stream()
                .filter(o -> "COMPLETED".equals(o.getStatus()))
                .count();

        long cancelledOrders = recentOrders.stream()
                .filter(o -> "CANCELLED".equals(o.getStatus()))
                .count();

        long paidOrders = recentOrders.stream()
                .filter(o -> "PAID".equals(o.getStatus()) || "READY".equals(o.getStatus()) || "COMPLETED".equals(o.getStatus()))
                .count();

        // Build food truck info
        StringBuilder trucksInfo = new StringBuilder();
        for (int i = 0; i < foodTrucks.size(); i++) {
            FoodTruck truck = foodTrucks.get(i);
            trucksInfo.append(String.format(
                    "Truck %d: Name=\"%s\", Category=\"%s\", Status=\"%s\", Location=\"%s, %s\"\n",
                    i + 1,
                    truck.getName() != null ? truck.getName() : "Unnamed",
                    truck.getCategory() != null ? truck.getCategory() : "Unknown",
                    truck.getStatus() != null ? truck.getStatus() : "Unknown",
                    truck.getCity() != null ? truck.getCity() : "Unknown",
                    truck.getDistrict() != null ? truck.getDistrict() : "Unknown"
            ));
        }

        return String.format("""
        You are a professional food truck business analyst specializing in the Saudi Arabian market, specifically Riyadh city operations.

        STRICT OUTPUT FORMAT:
        Return ONLY a single JSON object with EXACTLY these fields:
        {
          "adviceBasedOnTheDashboard": "<string up to 150 words>",
          "adviceOnItemDescription": "<string>",
          "totalOrders": <number>,
          "totalCompletedOrders": <number>,
          "predictedOrders": <number>,
          "totalRevenue": <number with 2 decimal places>,
          "avgOrderValue": <number with 2 decimal places>,
          "grossMarginPct": <number with 2 decimal places>,
          "repeatCustomerRate": <number with 2 decimal places>,
          "conversionRate": <number with 2 decimal places>,
          "cancelRate": <number with 2 decimal places>,
          "avgPrepTimeSec": <number>,
          "queueLenAvg": <number with 2 decimal places>,
          "tipsTotal": <number with 2 decimal places>,
          "weatherImpactIndex": <number with 2 decimal places>,
          "eventImpactIndex": <number with 2 decimal places>,
          "confidence": <number with 2 decimal places>,
          "riyadhOnly": true,
          "analysisPeriodFrom": "<YYYY-MM-DD>",
          "analysisPeriodTo": "<YYYY-MM-DD>"
        }

        ANALYSIS CONTEXT - RIYADH, SAUDI ARABIA ONLY:
        This analysis is exclusively for food truck operations in Riyadh, Kingdom of Saudi Arabia. Consider:
        - Local Saudi dining preferences and cultural habits
        - Riyadh's climate impact (extreme heat in summer, mild winters)
        - Major districts: Olaya, King Fahd, Diplomatic Quarter, Al Malaz, etc.
        - Local events, holidays, and Ramadan impacts
        - Saudi working hours and meal times
        - Weekend patterns (Friday-Saturday weekends)

        CALCULATION RULES:
        - Use provided data as base, but estimate realistic values for missing metrics
        - "adviceBasedOnTheDashboard": Business improvement advice specific to Saudi market (max 150 words)
        - "adviceOnItemDescription": Suggest item improvements for local tastes
        - Predicted orders: Base on trends, season, and Riyadh market patterns
        - Conversion rate: (Completed Orders / Total Orders) * 100
        - Cancel rate: (Cancelled Orders / Total Orders) * 100
        - Gross margin: Estimate 35-65%% based on food truck category
        - Weather impact: 0.0-10.0 (higher = more weather sensitive, consider Riyadh's extreme heat)
        - Event impact: 0.0-10.0 (higher = more event-dependent)
        - Confidence: 0.0-100.0 (your confidence in analysis accuracy)
        - All rates as percentages (0-100)
        - Analysis period: Recent 30 days from today

        DASHBOARD DATA:
        Total Orders: %d
        Total Completed Orders: %d
        Total Revenue: %.2f SAR
        Predicted Orders (Current): %s

        RECENT ORDERS ANALYSIS:
        Recent Completed Orders: %d
        Recent Cancelled Orders: %d
        Recent Paid Orders: %d
        Recent Total Revenue: %.2f SAR

        FOOD TRUCKS:
        %s

        Focus on actionable insights for the Saudi Arabian food truck market. Consider local competition, cultural preferences, seasonal impacts, and growth strategies specific to the Kingdom.

        Return only the JSON object.
        """,
                dashboard.getTotalOrders() != null ? dashboard.getTotalOrders() : 0,
                dashboard.getTotalCompletedOrders() != null ? dashboard.getTotalCompletedOrders() : 0,
                dashboard.getTotalRevenue() != null ? dashboard.getTotalRevenue() : 0.0,
                dashboard.getPredictedOrders() != null ? dashboard.getPredictedOrders().toString() : "Not set",
                completedOrders,
                cancelledOrders,
                paidOrders,
                totalRevenue,
                trucksInfo.toString()
        );
    }

    private DashBoardAnalyzerDtoOut parseAiResponse(String aiResponse) {
        JsonNode node;
        try {
            node = objectMapper.readTree(aiResponse.trim());
        } catch (Exception e) {
            throw new ApiException("AI response is not valid JSON: " + aiResponse);
        }

        // Extract and validate all fields
        String adviceBasedOnTheDashboard = node.path("adviceBasedOnTheDashboard").asText("");
        String adviceOnItemDescription = node.path("adviceOnItemDescription").asText("");

        Integer totalOrders = node.path("totalOrders").asInt(-1);
        Integer totalCompletedOrders = node.path("totalCompletedOrders").asInt(-1);
        Integer predictedOrders = node.path("predictedOrders").asInt(-1);
        Double totalRevenue = node.path("totalRevenue").asDouble(-1.0);
        Double avgOrderValue = node.path("avgOrderValue").asDouble(-1.0);
        Double grossMarginPct = node.path("grossMarginPct").asDouble(-1.0);
        Double repeatCustomerRate = node.path("repeatCustomerRate").asDouble(-1.0);
        Double conversionRate = node.path("conversionRate").asDouble(-1.0);
        Double cancelRate = node.path("cancelRate").asDouble(-1.0);
        Double avgPrepTimeSec = node.path("avgPrepTimeSec").asDouble(-1.0);
        Double queueLenAvg = node.path("queueLenAvg").asDouble(-1.0);
        Double tipsTotal = node.path("tipsTotal").asDouble(-1.0);
        Double weatherImpactIndex = node.path("weatherImpactIndex").asDouble(-1.0);
        Double eventImpactIndex = node.path("eventImpactIndex").asDouble(-1.0);
        Double confidence = node.path("confidence").asDouble(-1.0);
        Boolean riyadhOnly = node.path("riyadhOnly").asBoolean();
        String analysisPeriodFrom = node.path("analysisPeriodFrom").asText("");
        String analysisPeriodTo = node.path("analysisPeriodTo").asText("");

        // Validate required fields
        if (totalOrders < 0) throw new ApiException("AI: totalOrders must be >= 0");
        if (totalCompletedOrders < 0) throw new ApiException("AI: totalCompletedOrders must be >= 0");
        if (predictedOrders < 0) throw new ApiException("AI: predictedOrders must be >= 0");
        if (totalRevenue < 0) throw new ApiException("AI: totalRevenue must be >= 0");
        if (avgOrderValue < 0) throw new ApiException("AI: avgOrderValue must be >= 0");
        if (grossMarginPct < 0 || grossMarginPct > 100) throw new ApiException("AI: grossMarginPct must be 0-100");
        if (repeatCustomerRate < 0 || repeatCustomerRate > 100) throw new ApiException("AI: repeatCustomerRate must be 0-100");
        if (conversionRate < 0 || conversionRate > 100) throw new ApiException("AI: conversionRate must be 0-100");
        if (cancelRate < 0 || cancelRate > 100) throw new ApiException("AI: cancelRate must be 0-100");
        if (avgPrepTimeSec < 0) throw new ApiException("AI: avgPrepTimeSec must be >= 0");
        if (queueLenAvg < 0) throw new ApiException("AI: queueLenAvg must be >= 0");
        if (tipsTotal < 0) throw new ApiException("AI: tipsTotal must be >= 0");
        if (weatherImpactIndex < 0 || weatherImpactIndex > 10) throw new ApiException("AI: weatherImpactIndex must be 0-10");
        if (eventImpactIndex < 0 || eventImpactIndex > 10) throw new ApiException("AI: eventImpactIndex must be 0-10");
        if (confidence < 0 || confidence > 100) throw new ApiException("AI: confidence must be 0-100");
        if (!riyadhOnly) throw new ApiException("AI: riyadhOnly must be true");

        // Provide defaults for optional string fields
        if (adviceBasedOnTheDashboard.isBlank()) {
            adviceBasedOnTheDashboard = "Continue monitoring key performance indicators and focus on customer satisfaction to drive growth in the Riyadh market.";
        }
        if (adviceOnItemDescription.isBlank()) {
            adviceOnItemDescription = "Consider adding Arabic descriptions and highlighting local flavors";
        }
        if (analysisPeriodFrom.isBlank()) {
            analysisPeriodFrom = LocalDate.now().minusDays(30).format(DateTimeFormatter.ISO_LOCAL_DATE);
        }
        if (analysisPeriodTo.isBlank()) {
            analysisPeriodTo = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        }

        // Limit field lengths
        adviceBasedOnTheDashboard = limitWords(adviceBasedOnTheDashboard, 150);
        adviceOnItemDescription = limitChars(adviceOnItemDescription, 150);

        return new DashBoardAnalyzerDtoOut(
                adviceBasedOnTheDashboard,
                adviceOnItemDescription,
                totalOrders,
                totalCompletedOrders,
                predictedOrders,
                totalRevenue,
                avgOrderValue,
                grossMarginPct,
                repeatCustomerRate,
                conversionRate,
                cancelRate,
                avgPrepTimeSec,
                queueLenAvg,
                tipsTotal,
                weatherImpactIndex,
                eventImpactIndex,
                confidence,
                riyadhOnly,
                analysisPeriodFrom,
                analysisPeriodTo
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