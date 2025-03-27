package com.example.project.api;

import com.google.gson.annotations.SerializedName;

public class DetectionRequest {
    @SerializedName("quaternion_x")
    private float quaternionX;

    @SerializedName("quaternion_y")
    private float quaternionY;

    @SerializedName("quaternion_z")
    private float quaternionZ;

    @SerializedName("quaternion_w")
    private float quaternionW;

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

    public DetectionRequest(float quaternionX, float quaternionY, float quaternionZ, float quaternionW,
                            double latitude, double longitude, String timestamp, String imageBase64) {
        this.quaternionX = quaternionX;
        this.quaternionY = quaternionY;
        this.quaternionZ = quaternionZ;
        this.quaternionW = quaternionW;
        this.latitude = latitude;
        this.longitude = longitude;
        this.timestamp = timestamp;
        this.imageBase64 = imageBase64;
    }

    // Getters and setters
    public float getQuaternionX() { return quaternionX; }
    public void setQuaternionX(float quaternionX) { this.quaternionX = quaternionX; }

    public float getQuaternionY() { return quaternionY; }
    public void setQuaternionY(float quaternionY) { this.quaternionY = quaternionY; }

    public float getQuaternionZ() { return quaternionZ; }
    public void setQuaternionZ(float quaternionZ) { this.quaternionZ = quaternionZ; }

    public float getQuaternionW() { return quaternionW; }
    public void setQuaternionW(float quaternionW) { this.quaternionW = quaternionW; }

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

    public String getImageBase64() { return imageBase64; }
    public void setImageBase64(String imageBase64) { this.imageBase64 = imageBase64; }
}
