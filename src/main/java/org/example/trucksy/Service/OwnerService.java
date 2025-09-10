package org.example.trucksy.Service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.example.trucksy.Api.ApiException;
import org.example.trucksy.DTO.OwnerDTO;
import org.example.trucksy.DTO.SubscriptionDTO;
import org.example.trucksy.Model.*;
import org.example.trucksy.Repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.json.JSONObject;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OwnerService {

    private final OwnerRepository ownerRepository;
    private final FoodTruckRepository foodTruckRepository;
    private final AuthRepository authRepository;
    private final FoodTruckService foodTruckService;
    private final DashboardService dashboardService;
    private final DashboardRepository dashboardRepository;
    private final BankCardRepository bankCardRepository;
    private final PdfService pdfService;
    private final PdfMailService pdfMailService;

    @Value("${moyasar.api.key}")
    private String apiKey;

    private static final String MOYASAR_API_URL = "https://api.moyasar.com/v1/payments";
    private final Integer subscriptionAmount = 30; // Monthly subscription fee

    public void registerOwner(OwnerDTO ownerDTO) {
        User user = new User();
        user.setUsername(ownerDTO.getUsername());
        user.setPassword(new BCryptPasswordEncoder().encode(ownerDTO.getPassword()));
        user.setEmail(ownerDTO.getEmail());
        user.setPhoneNumber(ownerDTO.getPhone());
        user.setRole("OWNER");
        authRepository.save(user);

        Owner owner = new Owner();
        owner.setSubscribed(false);
        owner.setUser(user);
        ownerRepository.save(owner);

        // Create dashboard for owner
        Dashboard dash = new Dashboard();
        dash.setOwner(owner);
        dash.setTotalOrders(0);
        dash.setTotalRevenue(0.0);
        dash.setPredictedOrders(0);
        dash.setPeakOrders("N/A");
        dash.setTopSellingItems(null);
        dash.setUpdateDate(LocalDate.now());
        owner.setDashboard(dash);
        dashboardRepository.save(dash);
    }

    public void updateOwner(Integer id, OwnerDTO ownerDTO) {
        Owner owner = ownerRepository.findOwnerById(id);
        if (owner == null) {
            throw new ApiException("Owner not found");
        }
        User user = owner.getUser();
        user.setUsername(ownerDTO.getUsername());
        user.setPassword(new BCryptPasswordEncoder().encode(ownerDTO.getPassword()));
        user.setEmail(ownerDTO.getEmail());
        user.setPhoneNumber(ownerDTO.getPhone());
        ownerRepository.save(owner);
    }

    public void deleteOwner(Integer id) {
        Owner owner = ownerRepository.findOwnerById(id);
        if (owner == null) {
            throw new ApiException("Owner not found");
        }
        authRepository.delete(owner.getUser());
    }

    @Transactional
    public ResponseEntity<?> ownerSubscribePayment(Integer ownerId) {
        Owner owner = ownerRepository.findOwnerById(ownerId);
        if (owner == null) {
            throw new ApiException("Owner not found");
        }

        User user = owner.getUser();
        if (user.getBankCard() == null) {
            throw new ApiException("Add Bank card to continue Payment");
        }

        // Check if already subscribed and subscription is still active
        if (owner.getSubscribed() && owner.getSubscriptionEndDate() != null &&
                owner.getSubscriptionEndDate().isAfter(LocalDate.now())) {
            throw new ApiException("Error, the current subscription is still active (not expired)");
        }

        if (user.getBankCard().getAmount() < subscriptionAmount) {
            throw new ApiException("Insufficient funds. Required: " + subscriptionAmount + " SAR for Subscription");
        }

        // Update callback URL to match your working pattern
        String callbackUrl = "http://trucksy-test-application-env.eba-vm3jvf3z.us-east-1.elasticbeanstalk.com/api/v1/owner/callback/" + owner.getId();

        // Create request body following the same pattern as your working implementation
        String requestBody = String.format(
                "source[type]=card&source[name]=%s&source[number]=%s&source[cvc]=%s&source[month]=%s&source[year]=%s&amount=%d&currency=%s&callback_url=%s",
                user.getBankCard().getName(),
                user.getBankCard().getNumber(),
                user.getBankCard().getCvc(),
                user.getBankCard().getMonth(),
                user.getBankCard().getYear(),
                (subscriptionAmount * 100), // Convert to cents
                "SAR",
                callbackUrl
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(apiKey, "");
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);
        RestTemplate restTemplate = new RestTemplate();

        ResponseEntity<JsonNode> response = restTemplate.exchange(
                MOYASAR_API_URL,
                HttpMethod.POST,
                entity,
                JsonNode.class
        );

        // Return the response body from Moyasar (includes payment process URL as JSON)
        return ResponseEntity.status(response.getStatusCode()).body(response.getBody().toString());
    }

    public String getPaymentStatus(String paymentId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(apiKey, "");
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> entity = new HttpEntity<>(headers);
        RestTemplate restTemplate = new RestTemplate();

        ResponseEntity<String> response = restTemplate.exchange(
                MOYASAR_API_URL + "/" + paymentId,
                HttpMethod.GET,
                entity,
                String.class
        );

        return response.getBody();
    }

    @Transactional
    public ResponseEntity<?> handleSubscriptionPaymentCallback(Integer ownerId, String transaction_id, String status, String message) {
        Owner owner = ownerRepository.findOwnerById(ownerId);
        if (owner == null) {
            throw new ApiException("Error, the owner does not exist");
        }

        // Check the payment status from Moyasar to verify it's a legitimate callback
        String response = getPaymentStatus(transaction_id);
        JSONObject paymentStatus = new JSONObject(response);

        String moyasarStatus = paymentStatus.getString("status");

        if (!status.equalsIgnoreCase(moyasarStatus)) {
            throw new ApiException("Error, the status received is inconsistent with moyasar");
        }

        // Check the payment amount if it is correct
        Integer moyasarAmount = paymentStatus.getInt("amount") / 100; // Convert back from cents

        if (!moyasarAmount.equals(subscriptionAmount)) {
            throw new ApiException("Error, the amount " + moyasarAmount + " is not enough for a subscription of " + subscriptionAmount);
        }

        // Verify the payment is actually paid
        if (!moyasarStatus.equalsIgnoreCase("PAID")) {
            throw new ApiException("Error, the invoice was not paid");
        }

        // Deduct amount from bank card
        User user = owner.getUser();
        BankCard bankCard = user.getBankCard();
        double newAmount = bankCard.getAmount() - subscriptionAmount;
        bankCard.setAmount(newAmount);
        bankCardRepository.save(bankCard);

        // Create subscription status
        SubscriptionDTO subscriptionStatus = new SubscriptionDTO();
        subscriptionStatus.setStatus("Subscribed successfully: Monthly, status: " + message);
        subscriptionStatus.setIsSubscribed(true);
        subscriptionStatus.setSubscriptionStartDate(LocalDate.now());
        subscriptionStatus.setSubscriptionEndDate(LocalDate.now().plusMonths(1));

        // Update owner subscription
        owner.setSubscribed(subscriptionStatus.getIsSubscribed());
        owner.setSubscriptionStartDate(subscriptionStatus.getSubscriptionStartDate());
        owner.setSubscriptionEndDate(subscriptionStatus.getSubscriptionEndDate());
        ownerRepository.save(owner);

        // Send subscription invoice email
        try {
            String ownerName = (user.getUsername() != null) ? user.getUsername() : "Owner";
            String ownerEmail = user.getEmail();

            if (ownerEmail != null && !ownerEmail.isBlank()) {
                Map<String, Object> templateVars = new HashMap<>();
                templateVars.put("subscriptionId", "SUB-" + ownerId + "-" + LocalDate.now().getYear());
                templateVars.put("subscriptionDate", LocalDate.now().toString());
                templateVars.put("paymentId", transaction_id);
                templateVars.put("subscriptionStatus", "ACTIVE");
                templateVars.put("ownerName", ownerName);
                templateVars.put("ownerEmail", ownerEmail);
                templateVars.put("subscriptionPlan", "Monthly Premium");
                templateVars.put("subscriptionFee", String.format("%.2f SAR", (double) subscriptionAmount));
                templateVars.put("nextBillingDate", LocalDate.now().plusMonths(1).toString());
                templateVars.put("benefits", "Premium Dashboard Analytics, Priority Support, Advanced Reporting");

                byte[] pdf = pdfService.generateSubscriptionInvoicePdf(templateVars);
                String filename = "Trucksy-Subscription-Invoice-" + ownerId + ".pdf";
                String subject = "Your Trucksy Subscription Invoice - Welcome to Premium!";

                String html = String.format("""
                    <div style="font-family:Arial,Helvetica,sans-serif">
                      <h2 style="margin:0 0 8px 0;color:#ff6b35">Welcome to Trucksy Premium!</h2>
                      <p style="margin:0 0 12px 0">Your subscription payment has been processed successfully.</p>
                      <p style="margin:0 0 12px 0">Subscription ID: <b>SUB-%s-%s</b></p>
                      <p style="margin:0 0 12px 0">Amount paid: <b>%.2f SAR</b></p>
                      <p style="margin:0 0 12px 0">Next billing date: <b>%s</b></p>
                      <p style="margin:0 0 12px 0">We've attached your subscription invoice as a PDF.</p>
                      <h3 style="color:#ff6b35;margin:20px 0 8px 0">Premium Benefits:</h3>
                      <ul style="margin:0 0 12px 20px">
                        <li>Advanced dashboard analytics</li>
                        <li>Priority customer support</li>
                        <li>Detailed reporting features</li>
                        <li>Enhanced food truck management tools</li>
                      </ul>
                      <p style="color:#6b7280;font-size:12px;margin:16px 0 0 0">
                        If you didn't authorize this payment, please contact support immediately.
                      </p>
                    </div>
                    """, ownerId, LocalDate.now().getYear(), (double) subscriptionAmount, LocalDate.now().plusMonths(1).toString());

                pdfMailService.sendHtmlEmailWithAttachment(ownerEmail, subject, html, filename, pdf);
            }
        } catch (Exception mailErr) {
            System.err.println("Failed to send subscription invoice email: " + mailErr.getMessage());
        }

        return ResponseEntity.ok(subscriptionStatus);
    }

    public SubscriptionDTO getSubscriptionStatus(Integer ownerId) {
        Owner owner = ownerRepository.findOwnerById(ownerId);
        if (owner == null) {
            throw new ApiException("Error, the owner does not exist");
        }

        SubscriptionDTO subscriptionStatus = new SubscriptionDTO();

        // Check and invalidate subscription if it has ended
        if (owner.getSubscriptionEndDate() != null && owner.getSubscriptionEndDate().isBefore(LocalDate.now())) {
            owner.setSubscribed(false);
            ownerRepository.save(owner);
        }

        if (owner.getSubscribed()) {
            subscriptionStatus.setStatus("Subscription is valid");
        } else if (owner.getSubscriptionEndDate() == null) {
            subscriptionStatus.setStatus("You are not subscribed yet");
        } else {
            subscriptionStatus.setStatus("Subscription ended, please subscribe again");
        }

        subscriptionStatus.setIsSubscribed(owner.getSubscribed());
        subscriptionStatus.setSubscriptionStartDate(owner.getSubscriptionStartDate());
        subscriptionStatus.setSubscriptionEndDate(owner.getSubscriptionEndDate());

        return subscriptionStatus;
    }

    public void cancelSubscription(Integer ownerId) {
        Owner owner = ownerRepository.findOwnerById(ownerId);
        if (owner == null) {
            throw new ApiException("Error, the owner does not exist");
        }

        owner.setSubscribed(false);
        ownerRepository.save(owner);
    }
}