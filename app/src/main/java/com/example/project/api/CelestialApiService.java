package com.example.project.api;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface CelestialApiService {
    @POST("/celestial")
    Call<CelestialResponse> getCelestialCoordinates(@Body CelestialRequest request);
}
