package org.example.trucksy.Service;

import lombok.RequiredArgsConstructor;
import org.example.trucksy.Api.ApiException;
import org.example.trucksy.DTOOut.*;
import org.example.trucksy.Model.Dashboard;
import org.example.trucksy.Model.Order;
import org.example.trucksy.Model.Owner;
import org.example.trucksy.Repository.DashboardRepository;
import org.example.trucksy.Repository.OrderRepository;
import org.example.trucksy.Repository.OwnerRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DashboardService {

    // Add the new BestSpotAnalyzerService dependency
    private final BestSpotAnalyzerService bestSpotAnalyzerService;
    private final AiDashboardAnalyzerService aiDashboardAnalyzerService;
    private final DashboardRepository dashboardRepository;
    private final OrderRepository orderRepository;
    private final AiReviewAnalyzerService aiReviewAnalyzerService;
    private final OwnerRepository ownerRepository;

    // Existing methods remain the same...
    public void refreshDashboard(Integer owner_id) {
        Dashboard dash = dashboardRepository.findDashboardById(owner_id);
        Integer total = orderRepository.countOrdersByOwner(owner_id);
        Integer totalCompletedOrder = orderRepository.countCompletedOrdersByOwner(owner_id);
        Double revenue = orderRepository.sumRevenueByOwner(owner_id);
//        String peak = computePeakHourLabel(ownerUserId);
//        String topItems = computeTopItems(ownerUserId);

        dash.setTotalOrders((int) total);
        dash.setTotalRevenue(revenue != null ? revenue : 0.0);
        dash.setTotalCompletedOrders((int) totalCompletedOrder);
//        dash.setPredictedOrders(predict(total));
//        dash.setPeakOrders(peak);
//        dash.setTopSellingItems(topItems);

        dashboardRepository.save(dash);
    }

    public OwnerDashboardDTO getOwnerDashboard(Integer ownerId) {
        Dashboard dash = dashboardRepository.findDashboardById(ownerId);
        if (dash == null) {
            throw new ApiException("Dashboard not found");
        }

        //this from method refresh
        refreshDashboard(ownerId);

        Owner owner = dash.getOwner();

        return new OwnerDashboardDTO(
                owner.getUser().getUsername(),
                dash.getTotalOrders(),
                dash.getTotalRevenue(),
                dash.getTotalCompletedOrders(),
                dash.getPredictedOrders(),
                dash.getPeakOrders(),
                dash.getTopSellingItems(),
                dash.getUpdateDate()
        );
    }

    public List<OrderDashboardDTOOut> getOrdersByFoodTruck(Integer foodTruckId) {
        List<Order> orders = orderRepository.findByFoodTruck_IdOrderByIdDesc(foodTruckId);

        return orders.stream()
                .map(r -> new OrderDashboardDTOOut(
                        r.getId(),
                        r.getOrderDate(),
                        r.getClient().getUser().getUsername(),
                        r.getTotalPrice(),
                        r.getStatus()
                )).toList();
    }

    public ResponseEntity<ReviewAnalyzerDtoOut> reviewAnalyzer(Integer ownerId, Integer foodTruckId) {
        try {
            ReviewAnalyzerDtoOut analysis = aiReviewAnalyzerService.analyzeReviewsByFoodTruckId(ownerId, foodTruckId);
            return ResponseEntity.ok(analysis);
        } catch (ApiException e) {
            throw e; // Re-throw API exceptions as-is
        } catch (Exception e) {
            throw new ApiException("Failed to analyze reviews: " + e.getMessage());
        }
    }

    // Updated method to use the new AiDashboardAnalyzerService
    public ResponseEntity<DashBoardAnalyzerDtoOut> analyzeDashboard(Integer userId) {
        DashBoardAnalyzerDtoOut result = aiDashboardAnalyzerService.analyzeDashboardByOwnerId(userId);
        return ResponseEntity.ok(result);
    }

    // New method for best spot analysis
    public ResponseEntity<BestSpotAnalyzerDtoOut> analyzeBestSpot(Integer userId) {
        BestSpotAnalyzerDtoOut result = bestSpotAnalyzerService.analyzeBestSpotByOwnerId(userId);
        return ResponseEntity.ok(result);
    }

    public List<OrderDashboardDTOOut> getPLACEDOrdersByOwner(Integer ownerId) {
        Owner owner = ownerRepository.findOwnerById(ownerId);
        if (owner == null) {
            throw new ApiException("Owner not found");
        }
        List<Order> placedOrders = orderRepository.findAllByStatus("PLACED");

        return placedOrders.stream()
                .map(o-> new OrderDashboardDTOOut(
                        o.getId(),
                        o.getOrderDate(),
                        o.getClient().getUser().getUsername(),
                        o.getTotalPrice(),
                        o.getStatus()
                )).toList();
    }

    public List<OrderDashboardDTOOut> getReadyOrdersByOwner(Integer ownerId) {
        Owner owner = ownerRepository.findOwnerById(ownerId);
        if (owner == null) {
            throw new ApiException("Owner not found");
        }
        List<Order> readyOrders = orderRepository.findAllByStatus("READY");

        return readyOrders.stream()
                .map(o-> new OrderDashboardDTOOut(
                        o.getId(),
                        o.getOrderDate(),
                        o.getClient().getUser().getUsername(),
                        o.getTotalPrice(),
                        o.getStatus()
                )).toList();
    }

    public List<OrderDashboardDTOOut> getCompletedOrdersByOwner(Integer ownerId) {
        Owner owner = ownerRepository.findOwnerById(ownerId);
        if (owner == null) {
            throw new ApiException("Owner not found");
        }
        List<Order> completedOrders = orderRepository.findAllByStatus("COMPLETED");

        return completedOrders.stream()
                .map(o-> new OrderDashboardDTOOut(
                        o.getId(),
                        o.getOrderDate(),
                        o.getClient().getUser().getUsername(),
                        o.getTotalPrice(),
                        o.getStatus()
                )).toList();
    }
}