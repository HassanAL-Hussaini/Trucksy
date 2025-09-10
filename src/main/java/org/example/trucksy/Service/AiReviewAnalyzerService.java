package org.example.trucksy.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.example.trucksy.Api.ApiException;
import org.example.trucksy.DTOOut.ReviewAnalyzerDtoOut;
import org.example.trucksy.Model.FoodTruck;
import org.example.trucksy.Model.Review;
import org.example.trucksy.Repository.FoodTruckRepository;
import org.example.trucksy.Repository.OwnerRepository;
import org.example.trucksy.Repository.ReviewRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AiReviewAnalyzerService {

    private final ReviewRepository reviewRepository;
    private final FoodTruckRepository foodTruckRepository;
    private final OwnerRepository ownerRepository;
    private final AiService aiService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ReviewAnalyzerDtoOut analyzeReviewsByFoodTruckId(Integer ownerId, Integer foodTruckId) {
        // 1) Verify owner exists and is subscribed
        var owner = ownerRepository.findOwnerById(ownerId);
        if (owner == null) {
            throw new ApiException("Owner not found");
        }
        if (!owner.getSubscribed()) {
            throw new ApiException("Owner is not subscribed. AI services are only available for subscribers");
        }

        // 2) Verify food truck exists and belongs to owner
        FoodTruck foodTruck = foodTruckRepository.findFoodTruckById(foodTruckId);
        if (foodTruck == null) {
            throw new ApiException("Food truck not found");
        }
        if (foodTruck.getOwner() == null || !foodTruck.getOwner().getId().equals(ownerId)) {
            throw new ApiException("Owner does not own this food truck");
        }

        // 3) Get reviews with comments for this food truck
        List<Review> reviews = reviewRepository.findByFoodTruckIdWithComments(foodTruckId);
        if (reviews.isEmpty()) {
            throw new ApiException("No reviews with comments found for this food truck");
        }

        // 4) Build prompt for AI analysis
        String prompt = buildReviewAnalysisPrompt(reviews, foodTruck.getName());

        // 5) Call AI service
        String aiResponse = aiService.chat(prompt);
        if (aiResponse == null || aiResponse.isBlank()) {
            throw new ApiException("AI returned empty response");
        }

        // 6) Parse AI response
        return parseAiResponse(aiResponse);
    }

    private String buildReviewAnalysisPrompt(List<Review> reviews, String foodTruckName) {
        StringBuilder reviewsData = new StringBuilder();
        for (int i = 0; i < reviews.size(); i++) {
            Review review = reviews.get(i);
            reviewsData.append(String.format(
                    "Review %d: Rating=%d, Comment=\"%s\"\n",
                    i + 1,
                    review.getRating(),
                    sanitizeComment(review.getComment())
            ));
        }

        return """
        You are a professional review analyzer for food truck businesses. Analyze the provided reviews and provide insights to help the business improve.

        STRICT OUTPUT FORMAT:
        Return ONLY a single JSON object with EXACTLY these fields:
        {
          "positive_comments": <number>,
          "negative_comments": <number>,
          "true_negative_reviews": <number>,
          "true_positive_reviews": <number>,
          "advice_based_on_reviews": "<string up to 100 words>",
          "most_complaint_point": "<string>"
        }

        ANALYSIS RULES:
        - "positive_comments": Count comments with positive sentiment (regardless of rating)
        - "negative_comments": Count comments with negative sentiment (regardless of rating)
        - "true_negative_reviews": Count reviews where BOTH rating is low (1-2 stars) AND comment is negative
        - "true_positive_reviews": Count reviews where BOTH rating is high (4-5 stars) AND comment is positive
        - "advice_based_on_reviews": Provide actionable business advice based on patterns in reviews (max 100 words)
        - "most_complaint_point": Identify the most frequently mentioned complaint/issue. If no clear pattern, return "No major complaints identified"

        FOOD TRUCK: %s

        REVIEWS TO ANALYZE:
        %s

        Focus on constructive analysis. Look for patterns in complaints, praise, and suggestions. Consider food quality, service speed, pricing, cleanliness, staff behavior, and location convenience.

        Return only the JSON object.
        """.formatted(foodTruckName, reviewsData.toString());
    }

    private String sanitizeComment(String comment) {
        if (comment == null) return "";
        // Remove potential JSON-breaking characters and limit length
        return comment.replaceAll("[\"\\\\]", "'")
                .replaceAll("\\n", " ")
                .substring(0, Math.min(comment.length(), 200));
    }

    private ReviewAnalyzerDtoOut parseAiResponse(String aiResponse) {
        JsonNode node;
        try {
            node = objectMapper.readTree(aiResponse.trim());
        } catch (Exception e) {
            throw new ApiException("AI response is not valid JSON: " + aiResponse);
        }

        // Extract and validate fields
        int positiveComments = node.path("positive_comments").asInt(-1);
        int negativeComments = node.path("negative_comments").asInt(-1);
        int trueNegativeReviews = node.path("true_negative_reviews").asInt(-1);
        int truePositiveReviews = node.path("true_positive_reviews").asInt(-1);
        String advice = node.path("advice_based_on_reviews").asText("");
        String mostComplaintPoint = node.path("most_complaint_point").asText("");

        // Validate required fields
        if (positiveComments < 0) {
            throw new ApiException("AI: positive_comments must be >= 0");
        }
        if (negativeComments < 0) {
            throw new ApiException("AI: negative_comments must be >= 0");
        }
        if (trueNegativeReviews < 0) {
            throw new ApiException("AI: true_negative_reviews must be >= 0");
        }
        if (truePositiveReviews < 0) {
            throw new ApiException("AI: true_positive_reviews must be >= 0");
        }
        if (advice.isBlank()) {
            advice = "Continue monitoring customer feedback for business improvement opportunities.";
        }
        if (mostComplaintPoint.isBlank()) {
            mostComplaintPoint = "No major complaints identified";
        }

        // Limit field lengths
        advice = limitWords(advice, 100);
        mostComplaintPoint = limitChars(mostComplaintPoint, 100);

        return new ReviewAnalyzerDtoOut(
                positiveComments,
                negativeComments,
                trueNegativeReviews,
                truePositiveReviews,
                advice,
                mostComplaintPoint
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