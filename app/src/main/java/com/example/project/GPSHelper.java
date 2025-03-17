package com.example.project;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

public class GPSHelper implements SensorEventListener {
    private final Context context;
    private final LocationListener listener;
    private final FusedLocationProviderClient fusedLocationClient;
    private final LocationCallback locationCallback;

    private final SensorManager sensorManager;
    private final Sensor pressureSensor;
    private double barometricAltitude = 0.0;
    private static final double SEA_LEVEL_PRESSURE = 1013.25; // hPa standard pressure

    public interface LocationListener {
        void onLocationUpdated(double latitude, double longitude, double altitude);
    }

    public GPSHelper(Context context, LocationListener listener) {
        this.context = context;
        this.listener = listener;
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(context);

        // Initialize Sensor Manager for pressure sensor
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        pressureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE);
        if (pressureSensor != null) {
            sensorManager.registerListener(this, pressureSensor, SensorManager.SENSOR_DELAY_UI);
        }

        // Get last known location for a faster start
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
                if (location != null) {
                    double refinedAltitude = location.hasAltitude() ? location.getAltitude() : barometricAltitude;
                    listener.onLocationUpdated(location.getLatitude(), location.getLongitude(), refinedAltitude);
                }
            });
        }

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) return;
                for (Location location : locationResult.getLocations()) {
                    double refinedAltitude = location.hasAltitude() ? location.getAltitude() : barometricAltitude;
                    listener.onLocationUpdated(location.getLatitude(), location.getLongitude(), refinedAltitude);
                }
            }
        };
    }

    public void requestLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        LocationRequest locationRequest = new LocationRequest.Builder(1000) // 1-second updates
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .build();

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
    }

    public void stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback);
        sensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_PRESSURE) {
            float pressure = event.values[0]; // Pressure in hPa
            barometricAltitude = (1 - Math.pow(pressure / SEA_LEVEL_PRESSURE, 0.1903)) * 44330;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Not needed
    }
}
