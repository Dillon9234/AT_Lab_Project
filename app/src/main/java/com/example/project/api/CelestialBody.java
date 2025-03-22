package com.example.project.api;

import com.google.gson.annotations.SerializedName;

public class CelestialBody {
    @SerializedName("name")
    private String name;

    @SerializedName("x")
    private int x;

    @SerializedName("y")
    private int y;

    @SerializedName("angular_distance")
    private float angularDistance;

    @SerializedName("magnitude")
    private float magnitude;

    // Getters
    public String getName() { return name; }
    public int getX() { return x; }
    public int getY() { return y; }
    public float getAngularDistance() { return angularDistance; }
    public float getMagnitude() { return magnitude; }
}
