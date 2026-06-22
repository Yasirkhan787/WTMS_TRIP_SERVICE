package com.yasirkhan.trip.utils;

import com.google.maps.internal.PolylineEncoding;
import com.google.maps.model.LatLng;
import org.locationtech.jts.geom.*;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class SpatialUtils {

    private static final int SRID = 4326;
    private static final GeometryFactory factory = new GeometryFactory(new PrecisionModel(), SRID);

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
}
