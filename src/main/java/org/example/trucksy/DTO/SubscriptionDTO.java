package org.example.trucksy.DTO;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class SubscriptionDTO {
    private String status;
    private Boolean isSubscribed;
    private LocalDate subscriptionStartDate;
    private LocalDate subscriptionEndDate;
}