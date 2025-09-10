package org.example.trucksy.Controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.trucksy.Api.ApiResponse;
import org.example.trucksy.DTO.OwnerDTO;
import org.example.trucksy.Model.User;
import org.example.trucksy.Service.OwnerService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/owner")
@RequiredArgsConstructor
public class OwnerController {

    private final OwnerService ownerService;

    @PostMapping("/add")
    public ResponseEntity<?> registerOwner(@Valid @RequestBody OwnerDTO ownerDTO) {
        ownerService.registerOwner(ownerDTO);
        return ResponseEntity.status(200).body(new ApiResponse("Owner registered successfully"));
    }

    @PutMapping("/update")
    public ResponseEntity<?> updateOwner(@AuthenticationPrincipal User user, @Valid @RequestBody OwnerDTO ownerDTO) {
        ownerService.updateOwner(user.getId(), ownerDTO);
        return ResponseEntity.status(200).body(new ApiResponse("Owner updated successfully"));
    }

    @DeleteMapping("/delete")
    public ResponseEntity<?> deleteOwner(@AuthenticationPrincipal User user) {
        ownerService.deleteOwner(user.getId());
        return ResponseEntity.status(200).body(new ApiResponse("Owner deleted successfully"));
    }

    // Subscription payment endpoint
    @PostMapping("/subscribe")
    public ResponseEntity<?> subscribeOwner(@AuthenticationPrincipal User user) {
        return ownerService.ownerSubscribePayment(user.getId());
    }

    // Callback endpoint - GET method to match Moyasar's callback pattern
    @GetMapping("/callback/{ownerId}")
    public ResponseEntity<?> subscriptionCallback(@PathVariable Integer ownerId,
                                                  @RequestParam(name = "id") String transaction_id,
                                                  @RequestParam(name = "status") String status,
                                                  @RequestParam(name = "message") String message) {
        // Following the same pattern as your working BA implementation
        // Moyasar sends: ?id=transaction_id&status=paid&message=APPROVED
        return ownerService.handleSubscriptionPaymentCallback(ownerId, transaction_id, status, message);
    }

    // Get subscription status
    @GetMapping("/subscription/status")
    public ResponseEntity<?> getSubscriptionStatus(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ownerService.getSubscriptionStatus(user.getId()));
    }

    // Cancel subscription
    @PutMapping("/subscription/cancel")
    public ResponseEntity<?> cancelSubscription(@AuthenticationPrincipal User user) {
        ownerService.cancelSubscription(user.getId());
        return ResponseEntity.ok(new ApiResponse("Subscription canceled successfully"));
    }
}