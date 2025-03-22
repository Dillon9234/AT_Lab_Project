package com.example.project.api;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class DetectionRequest {
    @SerializedName("roll")
    private float roll;

    @SerializedName("pitch")
    private float pitch;

    @SerializedName("yaw")
    private float yaw;

    @SerializedName("latitude")
    private double latitude;

    @SerializedName("longitude")
    private double longitude;

    @SerializedName("timestamp")
    private String timestamp;

    @SerializedName("image")
    private String imageBase64;

    // Constructors
    public DetectionRequest() {}

    public DetectionRequest(float roll, float pitch, float yaw, double latitude,
                            double longitude, String timestamp, String imageBase64) {
        this.roll = roll;
        this.pitch = pitch;
        this.yaw = yaw;
        this.latitude = latitude;
        this.longitude = longitude;
        this.timestamp = timestamp;
        this.imageBase64 = imageBase64;
    }

    // Getters and setters
    public float getRoll() { return roll; }
    public void setRoll(float roll) { this.roll = roll; }

    public float getPitch() { return pitch; }
    public void setPitch(float pitch) { this.pitch = pitch; }

    public float getYaw() { return yaw; }
    public void setYaw(float yaw) { this.yaw = yaw; }

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

    public String getImageBase64() { return imageBase64; }
    public void setImageBase64(String imageBase64) { this.imageBase64 = imageBase64; }
}

