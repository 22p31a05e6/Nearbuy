package com.Echo.NearBuy.location.service;

import com.Echo.NearBuy.delivery.entity.DeliveryPerson;
import com.Echo.NearBuy.shop.entity.Shop;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Service
public class LocationService {
    private static final double EARTH_RADIUS_KM = 6371.0088;
    private static final String ROUTES_FIELD_MASK =
            "routes.distanceMeters,routes.duration,routes.polyline.encodedPolyline";

    private final String googleMapsApiKey;
    private final RestClient restClient;

    public LocationService(@Value("${app.google-maps.routes-api-key:}") String googleMapsApiKey) {
        this.googleMapsApiKey = googleMapsApiKey;
        this.restClient = RestClient.builder()
                .baseUrl("https://routes.googleapis.com")
                .build();
    }

    public double distanceCustomerToShop(
            BigDecimal customerLatitude,
            BigDecimal customerLongitude,
            Shop shop) {
        if (shop == null) {
            throw new IllegalArgumentException("Shop must not be null");
        }
        return distanceKm(
                customerLatitude,
                customerLongitude,
                shop.getLatitude(),
                shop.getLongitude());
    }

    public double distanceDeliveryPersonToShop(DeliveryPerson deliveryPerson, Shop shop) {
        if (deliveryPerson == null || shop == null) {
            throw new IllegalArgumentException("Delivery person and shop must not be null");
        }
        return distanceKm(
                deliveryPerson.getCurrentLatitude(),
                deliveryPerson.getCurrentLongitude(),
                shop.getLatitude(),
                shop.getLongitude());
    }

    public double distanceKm(
            BigDecimal originLatitude,
            BigDecimal originLongitude,
            BigDecimal destinationLatitude,
            BigDecimal destinationLongitude) {
        validateCoordinates(originLatitude, originLongitude, "origin");
        validateCoordinates(destinationLatitude, destinationLongitude, "destination");

        double originLatRadians = Math.toRadians(originLatitude.doubleValue());
        double destinationLatRadians = Math.toRadians(destinationLatitude.doubleValue());
        double latitudeDelta = destinationLatRadians - originLatRadians;
        double longitudeDelta = Math.toRadians(
                destinationLongitude.doubleValue() - originLongitude.doubleValue());
        double haversine = Math.pow(Math.sin(latitudeDelta / 2), 2)
                + Math.cos(originLatRadians)
                * Math.cos(destinationLatRadians)
                * Math.pow(Math.sin(longitudeDelta / 2), 2);
        double centralAngle = 2 * Math.atan2(Math.sqrt(haversine), Math.sqrt(1 - haversine));
        return EARTH_RADIUS_KM * centralAngle;
    }

    public RouteResult routeCustomerToShop(
            BigDecimal customerLatitude,
            BigDecimal customerLongitude,
            Shop shop) {
        if (shop == null) {
            throw new IllegalArgumentException("Shop must not be null");
        }
        return computeRoute(
                customerLatitude,
                customerLongitude,
                shop.getLatitude(),
                shop.getLongitude());
    }

    public RouteResult routeDeliveryPersonToShop(DeliveryPerson deliveryPerson, Shop shop) {
        if (deliveryPerson == null || shop == null) {
            throw new IllegalArgumentException("Delivery person and shop must not be null");
        }
        return computeRoute(
                deliveryPerson.getCurrentLatitude(),
                deliveryPerson.getCurrentLongitude(),
                shop.getLatitude(),
                shop.getLongitude());
    }

    public RouteResult computeRoute(
            BigDecimal originLatitude,
            BigDecimal originLongitude,
            BigDecimal destinationLatitude,
            BigDecimal destinationLongitude) {
        requireApiKey();
        validateCoordinates(originLatitude, originLongitude, "origin");
        validateCoordinates(destinationLatitude, destinationLongitude, "destination");

        Map<String, Object> request = Map.of(
                "origin", waypoint(originLatitude, originLongitude),
                "destination", waypoint(destinationLatitude, destinationLongitude),
                "travelMode", "DRIVE",
                "routingPreference", "TRAFFIC_UNAWARE");
        try {
            RoutesResponse response = restClient.post()
                    .uri("/directions/v2:computeRoutes")
                    .header("X-Goog-Api-Key", googleMapsApiKey)
                    .header("X-Goog-FieldMask", ROUTES_FIELD_MASK)
                    .body(request)
                    .retrieve()
                    .body(RoutesResponse.class);
            if (response == null || response.routes() == null || response.routes().isEmpty()) {
                throw new IllegalStateException("Google Routes API returned no route");
            }
            Route route = response.routes().get(0);
            if (route.distanceMeters() == null || route.duration() == null) {
                throw new IllegalStateException("Google Routes API returned incomplete route data");
            }
            return new RouteResult(
                    route.distanceMeters(),
                    route.duration(),
                    route.polyline() == null ? null : route.polyline().encodedPolyline());
        } catch (RestClientResponseException exception) {
            throw new IllegalStateException(
                    "Google Routes API request failed with HTTP status "
                            + exception.getStatusCode().value(),
                    exception);
        }
    }

    private Map<String, Object> waypoint(BigDecimal latitude, BigDecimal longitude) {
        return Map.of(
                "location",
                Map.of(
                        "latLng",
                        Map.of(
                                "latitude", latitude,
                                "longitude", longitude)));
    }

    private void validateCoordinates(BigDecimal latitude, BigDecimal longitude, String pointName) {
        if (latitude == null || latitude.compareTo(BigDecimal.valueOf(-90)) < 0
                || latitude.compareTo(BigDecimal.valueOf(90)) > 0) {
            throw new IllegalArgumentException(pointName + " latitude must be between -90 and 90");
        }
        if (longitude == null || longitude.compareTo(BigDecimal.valueOf(-180)) < 0
                || longitude.compareTo(BigDecimal.valueOf(180)) > 0) {
            throw new IllegalArgumentException(pointName + " longitude must be between -180 and 180");
        }
    }

    private void requireApiKey() {
        if (googleMapsApiKey == null || googleMapsApiKey.isBlank()) {
            throw new IllegalStateException(
                    "Google Routes API is not configured; set GOOGLE_MAPS_ROUTES_API_KEY");
        }
    }

    public record RouteResult(long distanceMeters, String duration, String encodedPolyline) {}

    private record RoutesResponse(List<Route> routes) {}

    private record Route(
            Long distanceMeters,
            String duration,
            Polyline polyline) {}

    private record Polyline(String encodedPolyline) {}
}
