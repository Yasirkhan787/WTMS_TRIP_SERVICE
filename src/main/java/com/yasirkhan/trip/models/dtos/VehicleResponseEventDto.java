package com.yasirkhan.trip.models.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.yasirkhan.trip.models.enums.EventStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class VehicleResponseEventDto {

    private EventStatus eventTypeStatus; // SUCCESS, FAILURE
    private String vehicleNo;
    private String tehsilId;
    private String trackingId;
    private Double mileage;
    private String status;

    @JsonProperty("vehicleData")
    private void unpackNestedVehicleData(Map<String, Object> vehicleData) {
        if (vehicleData != null) {
            this.vehicleNo = (String) vehicleData.get("vehicleNo");
            this.tehsilId = (String) vehicleData.get("tehsilId");
            this.trackingId = (String) vehicleData.get("trackingId");

            Object mileageObj = vehicleData.get("mileage");

            if (mileageObj instanceof Number) {
                this.mileage = ((Number) mileageObj).doubleValue();
            }
            this.status = (String) vehicleData.get("status");
        }
    }
}