package com.yasirkhan.trip.models.enums;

public enum Status {
    AT_DUMPSITE,  // Weight-In: Truck has loaded waste and is at the origin dump site.
    IN_TRANSIT,   // Route Execution: Truck is actively on the road.
    COMPLETED,    // Weight-Out: Truck returned, empty weight logged, net weight calculated.
    CANCELLED     // Failsafe status for aborted trips.
}