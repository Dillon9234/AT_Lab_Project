package com.example.project;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity implements SensorEventListener, GPSHelper.LocationListener {
    private SensorManager sensorManager;
    private Sensor rotationVectorSensor;
    private TextView sensorData;
    private PreviewView cameraPreview;
    private Button captureButton;
    private CameraHelper cameraHelper;
    private GPSHelper gpsHelper;
    private float qx, qy, qz, qw;
    private double latitude = 0.0, longitude = 0.0, altitude = 0.0;

    private static final int PERMISSIONS_REQUEST_CODE = 10;
    private String[] REQUIRED_PERMISSIONS;

    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // Android 13+
            REQUIRED_PERMISSIONS = new String[]{
                    Manifest.permission.CAMERA,
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.ACCESS_FINE_LOCATION
            };
        } else {
            REQUIRED_PERMISSIONS = new String[]{
                    Manifest.permission.CAMERA,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.ACCESS_FINE_LOCATION
            };
        }
    }

    private final ActivityResultLauncher<String[]> permissionsLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(),
            permissionsResult -> {
                boolean allGranted = true;
                for (Boolean isGranted : permissionsResult.values()) {
                    allGranted = allGranted && isGranted;
                }
                if (allGranted) {
                    cameraHelper.startCamera();
                    gpsHelper.requestLocationUpdates();
                } else {
                    Toast.makeText(this, "Permissions required!", Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        sensorData = findViewById(R.id.gyroData);
        cameraPreview = findViewById(R.id.cameraPreview);
        captureButton = findViewById(R.id.captureButton);

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (sensorManager != null) {
            rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
            if (rotationVectorSensor != null)
                sensorManager.registerListener(this, rotationVectorSensor, SensorManager.SENSOR_DELAY_UI);
        }

        cameraHelper = new CameraHelper(this, cameraPreview);
        gpsHelper = new GPSHelper(this, this);

        if (allPermissionsGranted()) {
            cameraHelper.startCamera();
            gpsHelper.requestLocationUpdates();
        } else {
            requestPermissions();
        }

        captureButton.setOnClickListener(view -> takePicture());
    }

    private boolean allPermissionsGranted() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private void requestPermissions() {
        permissionsLauncher.launch(REQUIRED_PERMISSIONS);
    }

    private void takePicture() {

        cameraHelper.takePicture(new CameraHelper.CaptureCallback() {
            @Override
            public void onImageCaptured(Uri imageUri) {
                try {
                    Intent intent = new Intent(MainActivity.this, ImageDetailsActivity.class);
                    intent.putExtra("image_uri", imageUri);
                    intent.putExtra("latitude", latitude);
                    intent.putExtra("longitude", longitude);
                    intent.putExtra("altitude", altitude);
                    intent.putExtra("quaternion_x", qx);
                    intent.putExtra("quaternion_y", qy);
                    intent.putExtra("quaternion_z", qz);
                    intent.putExtra("quaternion_w", qw);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(intent);
                } catch (Exception e) {
                    Log.e("MainActivity", "Error launching image details activity", e);
                    Toast.makeText(MainActivity.this, "Error launching details: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onError(String message) {
                Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }


    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            // Get quaternion directly from rotation vector
            float[] quaternion = new float[4];
            SensorManager.getQuaternionFromVector(quaternion, event.values);

            // Store quaternion components to class variables
            this.qw = quaternion[0];
            this.qx = quaternion[1];
            this.qy = quaternion[2];
            this.qz = quaternion[3];

            updateDisplay();
        }
    }

    @Override
    public void onLocationUpdated(double lat, double lon, double alt) {
        latitude = lat;
        longitude = lon;
        altitude = alt;
        updateDisplay();
    }

    private void updateDisplay() {
        AstronomicalCalculator.CelestialCoordinates coords =
                AstronomicalCalculator.calculateCoordinatesFromQuaternion(
                        latitude, longitude, altitude, qx, qy, qz, qw);

//        String data = String.format(
//                        "Quaternion:\nX: %.4f\nY: %.4f\nZ: %.4f\nW: %.4f\n\n"+
//                        "GPS Location:\nLatitude: %.6f\nLongitude: %.6f\nAltitude: %.2f m\n\n" +
//                        "Celestial Coordinates:\nRA: %.2f hours\nDec: %.2f°\nAz: %.2f°\nAlt: %.2f°",
//                qx, qy, qz, qw,latitude, longitude, altitude,
//                coords.rightAscension, coords.declination, coords.azimuth, coords.altitude
//        );
        String data = String.format(
                        "Celestial Coordinates:\nRA: %.2f hours\nDec: %.2f°\nAz: %.2f°\nAlt: %.2f°",
                coords.rightAscension, coords.declination, coords.azimuth, coords.altitude
        );
        sensorData.setText(data);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Safely unregister sensor listener
        if (sensorManager != null && rotationVectorSensor != null) {
            sensorManager.unregisterListener(this);
        }

        // Stop GPS updates
        gpsHelper.stopLocationUpdates();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (allPermissionsGranted()) {
            // Re-register sensor listener
            if (rotationVectorSensor != null) {
                sensorManager.registerListener(this, rotationVectorSensor, SensorManager.SENSOR_DELAY_UI);
            }

            // Request GPS location updates
            gpsHelper.requestLocationUpdates();

            // Restart camera
            cameraHelper.startCamera();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraHelper.release();
    }
}