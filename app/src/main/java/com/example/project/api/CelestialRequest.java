package com.example.project.api;

import com.google.gson.annotations.SerializedName;

public class CelestialRequest {
    @SerializedName("latitude")
    private double latitude;

    @SerializedName("longitude")
    private double longitude;

    @SerializedName("altitude")
    private double altitude;

    @SerializedName("timestamp")
    private long timestamp;

    public CelestialRequest(double latitude, double longitude, double altitude, long timestamp) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.altitude = altitude;
        this.timestamp = timestamp;
    }
}
