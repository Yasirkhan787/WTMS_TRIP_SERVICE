package com.yasirkhan.trip.utils;

import com.google.maps.internal.PolylineEncoding;
import com.google.maps.model.LatLng;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.io.WKTReader;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class SpatialUtils {

    private static final int SRID = 4326;
    private static final GeometryFactory factory = new GeometryFactory(new PrecisionModel(), SRID);
    private static final WKTReader reader = new WKTReader(factory);

    public static LineString toLineString(List<Coordinate> coordinates) {
        if (coordinates == null || coordinates.isEmpty()) {
            return null; // Or return factory.createLineString() if you prefer empty geometries
        }

        // A valid JTS LineString MUST have at least 2 points.
        // If the tracker only pinged once, duplicate the point to create a zero-length valid line.
        if (coordinates.size() == 1) {
            Coordinate singlePoint = coordinates.get(0);
            return factory.createLineString(new Coordinate[]{singlePoint, singlePoint});
        }

        // Convert the list to an array and generate the LineString
        return factory.createLineString(coordinates.toArray(new Coordinate[0]));
    }

    public static String toPolyLine(LineString path) {
        if (path == null || path.isEmpty()) {
            return "";
        }

        // Extract JTS Coordinates (Lng, Lat)
        // Map and Swap back to Google Format: JTS(Lng, Lat) -> Google(Lat, Lng)
        List<LatLng> googlePoints =
                Arrays
                        .stream(path.getCoordinates())
                        .map(coordinate -> new LatLng(coordinate.y, coordinate.x)) // y is Latitude, x is Longitude
                        .collect(Collectors.toList());

        // Encode the list of points into a single Polyline string
        return PolylineEncoding.encode(googlePoints);
    }

    // Earth's radius in meters
    private static final double EARTH_RADIUS = 6371000;

    public static Point createPoint(Double lat, Double lng) {
        if (lat == null || lng == null) return null;
        return factory.createPoint(new Coordinate(lng, lat)); // X=lng, Y=lat
    }

    public static LineString parseLineString(String wkt) {
        try {
            if (wkt == null || wkt.isEmpty()) return null;
            return (LineString) reader.read(wkt);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Calculates the distance between two points in meters using the Haversine Formula
     */
    public static double calculateDistanceInMeters(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS * c;
    }
}
