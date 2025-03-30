package com.example.project.api;

import com.google.gson.annotations.SerializedName;

public class HealthResponse {
    @SerializedName("status")
    private String status;

    @SerializedName("message")
    private String message;

    // Getters
    public String getStatus() { return status; }
    public String getMessage() { return message; }
}
