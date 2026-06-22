package com.yasirkhan.trip.integrations.asktrack;

import lombok.Data;
import java.util.List;

@Data
public class TrackerResponseDto {
    private boolean success;
    private TrackerDataDto data;

    @Data
    public static class TrackerDataDto {
        private List<TrackPointDto> track;
        private double totalKilometers;
        private int totalPoints;
        private String trackerId;
    }

    @Data
    public static class TrackPointDto {
        private double lat;
        private double lng;
        private String time;
        private int direction;
        private double speed;
    }
}