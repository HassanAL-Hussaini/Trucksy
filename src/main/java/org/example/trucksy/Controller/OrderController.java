package org.example.trucksy.Controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.trucksy.Api.ApiResponse;
import org.example.trucksy.DTO.LiensDtoIn;
import org.example.trucksy.DTO.OrderDtoIn;
import org.example.trucksy.DTO.OrderDtoOut;
import org.example.trucksy.Model.User;
import org.example.trucksy.Service.OrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping("/add/{foodTruckId}")
    public ResponseEntity<?> addOrder(@AuthenticationPrincipal User user,
                                      @PathVariable Integer foodTruckId,
                                      @Valid @RequestBody Set<LiensDtoIn> liensDtoIn) {
        // Return service ResponseEntity directly to avoid double-wrapping
        return orderService.addOrder(user.getId(), foodTruckId, liensDtoIn);
    }

    // Fixed callback endpoint - GET method to match Moyasar's callback pattern
    @GetMapping("/callback/{orderId}")
    public ResponseEntity<?> orderPaymentCallback(@PathVariable Integer orderId,
                                                  @RequestParam(name = "id") String transaction_id,
                                                  @RequestParam(name = "status") String status,
                                                  @RequestParam(name = "message") String message) {
        // Following the same pattern as your working BA implementation
        // Moyasar sends: ?id=transaction_id&status=paid&message=APPROVED
        return orderService.handlePaymentCallback(orderId, transaction_id, status, message);
    }

    // Get payment status
    @GetMapping("/payment/status/{paymentId}")
    public ResponseEntity<?> getPaymentStatus(@PathVariable String paymentId) {
        return ResponseEntity.ok(orderService.getPaymentStatus(paymentId));
    }

    // تغيير الحالة إلى READY (من Path Variables)
    @PutMapping("/status/ready/{foodTruckId}/{orderId}")
    public ResponseEntity<ApiResponse> markOrderReady(@AuthenticationPrincipal User user,
                                                      @PathVariable Integer foodTruckId,
                                                      @PathVariable Integer orderId) {
        orderService.changeOrderStatusToReady(user.getId(), foodTruckId, orderId);
        return ResponseEntity.status(200).body(new ApiResponse(
                "Order #" + orderId + " marked as READY"
        ));
    }

    // تغيير الحالة إلى COMPLETED (من Path Variables)
    @PutMapping("/status/completed/{foodTruckId}/{orderId}")
    public ResponseEntity<ApiResponse> markOrderCompleted(@AuthenticationPrincipal User user,
                                                          @PathVariable Integer foodTruckId,
                                                          @PathVariable Integer orderId) {
        orderService.changeOrderStatusToCompleted(user.getId(), foodTruckId, orderId);
        return ResponseEntity.status(200).body(new ApiResponse(
                "Order #" + orderId + " marked as COMPLETED"
        ));
    }

    @GetMapping("/foodtruck/{foodTruckId}")
    public ResponseEntity<List<OrderDtoOut>> getOrdersForFoodTruck(@PathVariable Integer foodTruckId) {
        return ResponseEntity.status(200).body(orderService.getOrdersForFoodTruckDto(foodTruckId));
    }

    @GetMapping("/client")
    public ResponseEntity<List<OrderDtoOut>> getOrdersForClient(@AuthenticationPrincipal User user) {
        return ResponseEntity.status(200).body(orderService.getOrdersForClientDto(user.getId()));
    }

    @GetMapping("/foodtruck/{foodTruckId}/{orderId}")
    public ResponseEntity<OrderDtoOut> getOrderForFoodTruck(@PathVariable Integer foodTruckId,
                                                            @PathVariable Integer orderId) {
        return ResponseEntity.status(200).body(orderService.getOrderForFoodTruckDto(foodTruckId, orderId));
    }
}