package com.yasirkhan.trip.integrations.asktrack;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AskTrackerTripsResponseDto {

    private String status;
    private List<AskTrackSlipDto> data;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AskTrackSlipDto {
        private Long webData_Id;
        private String slipID_Load;
        private String loadDate;
        private String loadTime;
        private String emptyDate;
        private String emptyTime;
        private String vehicle_Name;
        private Double loadWeight;
        private Double emptyWeight;
        private Double netWeight;
        private String fWB_SlipID;
        private Object images;
    }
}