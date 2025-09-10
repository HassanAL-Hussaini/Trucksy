package org.example.trucksy.Model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.*;

import java.time.LocalDate;
import java.util.Set;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
public class Owner {

    @Id
    private Integer id;

    private Boolean subscribed;
    private LocalDate subscriptionStartDate;
    private LocalDate subscriptionEndDate;
    @OneToOne
    @MapsId
//    @JoinColumn(name = "user_id")
    @JsonIgnore
    private User user;


    @OneToMany(cascade = CascadeType.ALL , mappedBy = "owner")
    private Set<FoodTruck> foodTrucks;


    @OneToOne(cascade = CascadeType.ALL , mappedBy = "owner")
    @PrimaryKeyJoinColumn
    private Dashboard dashboard;
}
