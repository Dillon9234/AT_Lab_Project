package com.example.project.api;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class DetectionResponse {
    @SerializedName("status")
    private String status;

    @SerializedName("detected_bodies")
    private List<CelestialBody> detectedBodies;

    @SerializedName("result_image")
    private String resultImage;

    @SerializedName("error")
    private String error;

    // Getters
    public String getStatus() { return status; }
    public List<CelestialBody> getDetectedBodies() { return detectedBodies; }
    public String getResultImage() { return resultImage; }
    public String getError() { return error; }
}
