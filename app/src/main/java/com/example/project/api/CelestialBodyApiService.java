package com.example.project.api;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;

public interface CelestialBodyApiService {
    @POST("/detect")
    Call<DetectionResponse> detectCelestialBodies(@Body DetectionRequest request);

    @GET("/health")
    Call<HealthResponse> checkHealth();
}