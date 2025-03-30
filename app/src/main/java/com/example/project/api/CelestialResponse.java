package com.example.project.api;

import com.google.gson.annotations.SerializedName;
import java.util.Map;

public class CelestialResponse {
    @SerializedName("timestamp")
    private String timestamp;

    @SerializedName("observer")
    private Observer observer;

    @SerializedName("celestial_bodies")
    private Map<String, CelestialBodyPosition> celestialBodies;

    public static class Observer {
        @SerializedName("latitude")
        private double latitude;

        @SerializedName("longitude")
        private double longitude;

        @SerializedName("altitude")
        private double altitude;
    }

    public static class CelestialBodyPosition {
        @SerializedName("ra")
        public CoordinateData ra;

        @SerializedName("dec")
        public CoordinateData dec;
    }

    public static class CoordinateData {
        @SerializedName("hours")
        private double hours;

        @SerializedName("degrees")
        private double degrees;

        @SerializedName("string")
        private String string;

        // Add these getter methods
        public double getHours() { return hours; }
        public double getDegrees() { return degrees; }
        public String getString() { return string; }
    }

    public String getTimestamp() { return timestamp; }
    public Observer getObserver() { return observer; }
    public Map<String, CelestialBodyPosition> getCelestialBodies() { return celestialBodies; }
}
