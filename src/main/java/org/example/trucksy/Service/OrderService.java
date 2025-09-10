package org.example.trucksy.Service;

import lombok.RequiredArgsConstructor;
import org.example.trucksy.Api.ApiException;
import org.example.trucksy.DTO.*;
import org.example.trucksy.Model.*;
import org.example.trucksy.Repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.json.JSONObject;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class OrderService {
    private final ClientRepository clientRepository;
    private final ItemRepository itemRepository;
    private final FoodTruckRepository foodTruckRepository;
    private final AuthRepository authRepository;
    private final OrderRepository orderRepository;
    private final BankCardRepository bankCardRepository;
    private final WhatsAppService whatsAppService;
    private final PdfService pdfService;
    private final PdfMailService pdfMailService;

    @Value("${moyasar.api.key}")
    private String apiKey;

    private static final String MOYASAR_API_URL = "https://api.moyasar.com/v1/payments";

    @Transactional
    public ResponseEntity<?> addOrder(Integer clientId, Integer foodTruckId, Set<LiensDtoIn> liensDtoIns) {
        FoodTruck foodTruck = foodTruckRepository.findFoodTruckById(foodTruckId);
        if (foodTruck == null)
            throw new ApiException("FoodTruck not found");

        if ("CLOSED".equals(foodTruck.getStatus()))
            throw new ApiException("food truck is already closed");

        Client client = clientRepository.findClientById(clientId);
        if (client == null)
            throw new ApiException("Client not found");

        User user = authRepository.findUserById(clientId);
        if (user == null)
            throw new ApiException("User not found for this client");

        if (user.getBankCard() == null)
            throw new ApiException("Client does not have Bank Card");

        if (liensDtoIns == null || liensDtoIns.isEmpty())
            throw new ApiException("Order must contain at least one item");

        Order order = new Order();
        Set<OrderLine> lines = new LinkedHashSet<>();
        Double totalPrice = 0.0;

        // Better duplicate item handling
        for (LiensDtoIn lien : liensDtoIns) {
            Integer qty = lien.getQuantity();
            if (qty == null || qty <= 0)
                throw new ApiException("Quantity must be > 0");

            Item item = itemRepository.findItemById(lien.getItemId());
            if (item == null) {
                throw new ApiException("Item with ID " + lien.getItemId() + " not found");
            }
            if (Boolean.FALSE.equals(item.getIsAvailable())) {
                throw new ApiException("Item " + item.getName() + " is not available right now");
            }

            if (item.getFoodTruck() == null || item.getFoodTruck().getId() == null
                    || !item.getFoodTruck().getId().equals(foodTruckId)) {
                throw new ApiException("Item " + item.getName() + " does not belong to the selected FoodTruck");
            }

            // Use stream API for cleaner duplicate detection
            OrderLine existing = lines.stream()
                    .filter(ol -> ol.getItem().getId().equals(item.getId()))
                    .findFirst()
                    .orElse(null);

            if (existing == null) {
                double unitPrice = item.getPrice();
                OrderLine line = new OrderLine();
                line.setItem(item);
                line.setQuantity(qty);
                line.setUnitPriceAtPurchase(unitPrice);
                line.setOrder(order);
                lines.add(line);
                totalPrice += unitPrice * qty;
            } else {
                // Correct quantity merging and price calculation
                existing.setQuantity(existing.getQuantity() + qty);
                totalPrice += existing.getUnitPriceAtPurchase() * qty;
            }
        }

        if (user.getBankCard().getAmount() < totalPrice) {
            throw new ApiException("Insufficient funds. Required: " + totalPrice + " SAR");
        }

        order.setStatus("PLACED");
        order.setTotalPrice(totalPrice);
        order.setLines(lines);
        order.setClient(client);
        order.setFoodTruck(foodTruck);
        orderRepository.save(order);

        // Fixed callback URL to use AWS and match working pattern
        String callbackUrl = "http://trucksy-test-application-env.eba-vm3jvf3z.us-east-1.elasticbeanstalk.com/api/v1/order/callback/" + order.getId();

        // Create request body following the same pattern as your working implementation
        String requestBody = String.format(
                "source[type]=card&source[name]=%s&source[number]=%s&source[cvc]=%s&source[month]=%s&source[year]=%s&amount=%d&currency=%s&callback_url=%s",
                user.getBankCard().getName(),
                user.getBankCard().getNumber(),
                user.getBankCard().getCvc(),
                user.getBankCard().getMonth(),
                user.getBankCard().getYear(),
                (int) Math.round(totalPrice * 100), // Convert to cents
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
    public ResponseEntity<?> handlePaymentCallback(Integer orderId, String transaction_id, String status, String message) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ApiException("Error, the order does not exist"));

        // Check the payment status from Moyasar to verify it's a legitimate callback
        String response = getPaymentStatus(transaction_id);
        JSONObject paymentStatus = new JSONObject(response);

        String moyasarStatus = paymentStatus.getString("status");

        if (!status.equalsIgnoreCase(moyasarStatus)) {
            throw new ApiException("Error, the status received is inconsistent with moyasar");
        }

        // Check the payment amount if it is correct (convert from cents)
        Double moyasarAmount = paymentStatus.getInt("amount") / 100.0;

        if (!moyasarAmount.equals(order.getTotalPrice())) {
            throw new ApiException("Error, the amount " + moyasarAmount + " does not match the order total " + order.getTotalPrice());
        }

        // Verify the payment is actually paid
        if (!moyasarStatus.equalsIgnoreCase("PAID")) {
            throw new ApiException("Error, the invoice was not paid");
        }

        // Deduct amount from bank card
        Client client = order.getClient();
        User user = authRepository.findUserById(client.getId());
        if (user == null || user.getBankCard() == null) {
            throw new ApiException("User/BankCard not found");
        }

        BankCard bankCard = user.getBankCard();
        double newAmount = bankCard.getAmount() - order.getTotalPrice();
        bankCard.setAmount(newAmount);
        bankCardRepository.save(bankCard);

        // Update order status
        order.setStatus("PAID");
        orderRepository.save(order);

        // Send WhatsApp notification to food truck owner
        FoodTruck ft = order.getFoodTruck();
        if (ft != null && ft.getOwner() != null && ft.getOwner().getUser() != null) {
            String ownerPhone = ft.getOwner().getUser().getPhoneNumber();
            if (ownerPhone != null && !ownerPhone.isBlank()) {
                whatsAppService.sendText(
                        ownerPhone,
                        "🚚 New Order Received! Order #" + order.getId() + " from " + ft.getName() +
                                ". Total: " + order.getTotalPrice() + " SAR. Check your dashboard for details."
                );
            }
        }

        // Send invoice email with proper data structure
        try {
            String customerName = (client.getUser() != null && client.getUser().getUsername() != null)
                    ? client.getUser().getUsername() : "Customer";
            String customerEmail = (client.getUser() != null) ? client.getUser().getEmail() : null;

            if (customerEmail != null && !customerEmail.isBlank()) {
                // Create proper template variables for PDF generation
                Map<String, Object> templateVars = new HashMap<>();

                // Basic order info
                templateVars.put("orderId", order.getId());
                templateVars.put("creationDate", LocalDate.now().toString());
                templateVars.put("orderStatus", order.getStatus());
                templateVars.put("paymentId", transaction_id);

                // Customer info
                templateVars.put("clientName", customerName);
                templateVars.put("customerEmail", customerEmail);

                // Food truck info
                if (order.getFoodTruck() != null) {
                    templateVars.put("foodTruckName", order.getFoodTruck().getName());
                }

                // Create order lines with proper structure for template
                List<InvoiceLineDto> orderLines = new ArrayList<>();
                if (order.getLines() != null) {
                    for (OrderLine line : order.getLines()) {
                        InvoiceLineDto dto = new InvoiceLineDto();
                        dto.setItemName(line.getItem().getName());
                        dto.setQuantity(line.getQuantity());
                        dto.setUnitPrice(String.format("%.2f SAR", line.getUnitPriceAtPurchase()));
                        dto.setLineTotal(String.format("%.2f SAR", line.getUnitPriceAtPurchase() * line.getQuantity()));
                        orderLines.add(dto);
                    }
                }
                templateVars.put("orderLines", orderLines);

                // Total price formatting
                templateVars.put("totalPriceFormatted", String.format("%.2f SAR", order.getTotalPrice()));

                // Generate PDF with corrected method call
                byte[] pdf = pdfService.generateInvoicePdf(templateVars);

                String filename = "Trucksy-Invoice-" + order.getId() + ".pdf";
                String subject = "Your Trucksy order invoice #" + order.getId();

                String html = String.format("""
                        <div style="font-family:Arial,Helvetica,sans-serif">
                          <h2 style="margin:0 0 8px 0;color:#ff6b35">Thanks for your order!</h2>
                          <p style="margin:0 0 12px 0">Your Trucksy order <b>#%s</b> has been paid successfully.</p>
                          <p style="margin:0 0 12px 0">Order status: <b>%s</b></p>
                          <p style="margin:0 0 12px 0">Total amount: <b>%.2f SAR</b></p>
                          <p style="margin:0 0 12px 0">We've attached your invoice as a PDF.</p>
                          <p style="color:#6b7280;font-size:12px;margin:16px 0 0 0">
                            If you didn't authorize this payment, please contact support immediately.
                          </p>
                        </div>
                        """, order.getId(), message, order.getTotalPrice());

                pdfMailService.sendHtmlEmailWithAttachment(
                        customerEmail,
                        subject,
                        html,
                        filename,
                        pdf
                );
            }
        } catch (Exception mailErr) {
            // Don't break the success flow if email fails
            System.err.println("Failed to send invoice email: " + mailErr.getMessage());
        }

        // Create response similar to BA implementation
        Map<String, Object> result = new HashMap<>();
        result.put("orderId", order.getId());
        result.put("paymentId", transaction_id);
        result.put("status", "Order paid successfully: status: " + message);
        result.put("totalPrice", order.getTotalPrice());

        return ResponseEntity.ok(result);
    }

    @Transactional
    public void changeOrderStatusToReady(Integer OwnerId, Integer foodTruckId, Integer orderId) {
        if (OwnerId == null || foodTruckId == null || orderId == null) {
            throw new ApiException("OwnerId, foodTruckId and orderId are required");
        }

        FoodTruck ft = foodTruckRepository.findFoodTruckById(foodTruckId);
        if (ft == null) throw new ApiException("FoodTruck not found");

        // Ensure the owner actually owns this food truck
        if (ft.getOwner() == null || ft.getOwner().getUser() == null ||
                !ft.getOwner().getUser().getId().equals(OwnerId)) {
            throw new ApiException("Owner does not own this FoodTruck");
        }

        Order order = orderRepository.findByIdAndFoodTruck_Id(orderId, foodTruckId)
                .orElseThrow(() -> new ApiException("Order not found for this FoodTruck"));

        // Only PAID orders can move to READY
        if (!"PAID".equalsIgnoreCase(order.getStatus())) {
            throw new ApiException("Only PAID orders can be marked as READY");
        }

        order.setStatus("READY");
        orderRepository.save(order);

        // Notify client via WhatsApp
        Client client = order.getClient();
        if (client != null && client.getUser() != null) {
            String phone = client.getUser().getPhoneNumber();
            if (phone != null && !phone.isBlank()) {
                String truckName = (ft.getName() != null && !ft.getName().isBlank()) ? ft.getName() : "Food Truck";
                whatsAppService.sendText(
                        phone,
                        "✅ طلبك رقم #" + order.getId() + " من " + truckName + " جاهز للاستلام.\n" +
                                "✅ Your order #" + order.getId() + " from " + truckName + " is READY for pickup."
                );
            }
        }
    }

    @Transactional
    public void changeOrderStatusToCompleted(Integer OwnerId, Integer foodTruckId, Integer orderId) {
        if (OwnerId == null || foodTruckId == null || orderId == null) {
            throw new ApiException("OwnerId, foodTruckId and orderId are required");
        }

        FoodTruck ft = foodTruckRepository.findFoodTruckById(foodTruckId);
        if (ft == null) throw new ApiException("FoodTruck not found");

        // Ensure the owner actually owns this food truck
        if (ft.getOwner() == null || ft.getOwner().getUser() == null ||
                !ft.getOwner().getUser().getId().equals(OwnerId)) {
            throw new ApiException("Owner does not own this FoodTruck");
        }

        Order order = orderRepository.findByIdAndFoodTruck_Id(orderId, foodTruckId)
                .orElseThrow(() -> new ApiException("Order not found for this FoodTruck"));

        // Only READY orders can move to COMPLETED
        if (!"READY".equalsIgnoreCase(order.getStatus())) {
            throw new ApiException("Only READY orders can be marked as COMPLETED");
        }

        order.setStatus("COMPLETED");
        orderRepository.save(order);

        // Notify client via WhatsApp
        Client client = order.getClient();
        if (client != null && client.getUser() != null) {
            String phone = client.getUser().getPhoneNumber();
            if (phone != null && !phone.isBlank()) {
                String truckName = (ft.getName() != null && !ft.getName().isBlank()) ? ft.getName() : "Food Truck";
                whatsAppService.sendText(
                        phone,
                        "🎉 تم إكمال طلبك رقم #" + order.getId() + " من " + truckName + ". بالعافية! \n" +
                                "🎉 Your order #" + order.getId() + " from " + truckName + " is COMPLETED. Enjoy!"
                );
            }
        }
    }

    // Invoice Line DTO for template
    public static class InvoiceLineDto {
        private String itemName;
        private Integer quantity;
        private String unitPrice;
        private String lineTotal;

        public String getItemName() {
            return itemName;
        }

        public void setItemName(String itemName) {
            this.itemName = itemName;
        }

        public Integer getQuantity() {
            return quantity;
        }

        public void setQuantity(Integer quantity) {
            this.quantity = quantity;
        }

        public String getUnitPrice() {
            return unitPrice;
        }

        public void setUnitPrice(String unitPrice) {
            this.unitPrice = unitPrice;
        }

        public String getLineTotal() {
            return lineTotal;
        }

        public void setLineTotal(String lineTotal) {
            this.lineTotal = lineTotal;
        }
    }

    @Transactional(readOnly = true)
    public List<OrderDtoOut> getOrdersForFoodTruckDto(Integer foodTruckId) {
        if (foodTruckRepository.findFoodTruckById(foodTruckId) == null)
            throw new ApiException("FoodTruck not found");

        List<Order> orders = orderRepository.findByFoodTruck_IdOrderByIdDesc(foodTruckId);
        List<OrderDtoOut> result = new ArrayList<>();

        for (Order o : orders) {
            OrderDtoOut dto = mapOrderToDtoOut(o, true, false);
            result.add(dto);
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<OrderDtoOut> getOrdersForClientDto(Integer clientId) {
        if (clientRepository.findClientById(clientId) == null)
            throw new ApiException("Client not found");

        List<Order> orders = orderRepository.findByClient_IdOrderByIdDesc(clientId);
        List<OrderDtoOut> result = new ArrayList<>();

        for (Order o : orders) {
            OrderDtoOut dto = mapOrderToDtoOut(o, false, true);
            result.add(dto);
        }
        return result;
    }

    @Transactional(readOnly = true)
    public OrderDtoOut getOrderForFoodTruckDto(Integer foodTruckId, Integer orderId) {
        if (foodTruckRepository.findFoodTruckById(foodTruckId) == null)
            throw new ApiException("FoodTruck not found");

        Order order = orderRepository.findByIdAndFoodTruck_Id(orderId, foodTruckId)
                .orElseThrow(() -> new ApiException("Order not found for this FoodTruck"));

        return mapOrderToDtoOut(order, true, false);
    }

    private OrderDtoOut mapOrderToDtoOut(Order order, boolean includeClient, boolean includeFoodTruck) {
        OrderDtoOut dto = new OrderDtoOut();
        dto.setId(order.getId());
        dto.setStatus(order.getStatus());
        dto.setTotalPrice(order.getTotalPrice());

        if (includeClient) {
            ClientSummaryDtoOut clientDto = null;
            Client client = order.getClient();
            if (client != null) {
                clientDto = new ClientSummaryDtoOut();
                clientDto.setId(client.getId());

                User u = client.getUser();
                if (u != null) {
                    clientDto.setUsername(u.getUsername());
                    clientDto.setEmail(u.getEmail());
                    clientDto.setPhone(u.getPhoneNumber());
                }
            }
            dto.setClient(clientDto);
        } else {
            dto.setClient(null);
        }

        if (includeFoodTruck) {
            FoodTruckSummaryDtoOut truckDto = null;
            if (order.getFoodTruck() != null) {
                truckDto = new FoodTruckSummaryDtoOut();
                truckDto.setId(order.getFoodTruck().getId());
                truckDto.setName(order.getFoodTruck().getName());
                truckDto.setCategory(order.getFoodTruck().getCategory());
                truckDto.setStatus(order.getFoodTruck().getStatus());
            }
            dto.setFoodTruck(truckDto);
        } else {
            dto.setFoodTruck(null);
        }

        List<OrderLineDtoOut> lineDtos = new ArrayList<>();
        if (order.getLines() != null) {
            for (OrderLine ol : order.getLines()) {
                OrderLineDtoOut ld = new OrderLineDtoOut();
                if (ol.getItem() != null) {
                    ld.setItemId(ol.getItem().getId());
                    ld.setItemName(ol.getItem().getName());
                }
                ld.setQuantity(ol.getQuantity());
                ld.setUnitPriceAtPurchase(ol.getUnitPriceAtPurchase());
                lineDtos.add(ld);
            }
        }
        dto.setLines(lineDtos);
        return dto;
    }
}