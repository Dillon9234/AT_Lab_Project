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
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;

import com.google.android.gms.location.DeviceOrientation;
import com.google.android.gms.location.DeviceOrientationListener;
import com.google.android.gms.location.DeviceOrientationRequest;
import com.google.android.gms.location.FusedOrientationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.Task;

public class MainActivity extends AppCompatActivity implements SensorEventListener, GPSHelper.LocationListener {
    private static final String TAG = "MainActivity";

    private SensorManager sensorManager;
    private Sensor rotationVectorSensor;
    private TextView sensorData;
    private CameraHelper cameraHelper;
    private GPSHelper gpsHelper;
    private float qx, qy, qz, qw;
    private double latitude = 0.0, longitude = 0.0, altitude = 0.0;
    private final String[] REQUIRED_PERMISSIONS;

    // Fused Orientation Provider components
    // Fused Orientation Provider components
    private FusedOrientationProviderClient fusedOrientationClient;
    private DeviceOrientationListener deviceOrientationListener;
    private boolean useFusedOrientation = false;

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
                    initOrientationSensors();
                } else {
                    Toast.makeText(this, "Permissions required!", Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        sensorData = findViewById(R.id.gyroData);
        PreviewView cameraPreview = findViewById(R.id.cameraPreview);
        Button captureButton = findViewById(R.id.captureButton);

        cameraHelper = new CameraHelper(this, cameraPreview);
        gpsHelper = new GPSHelper(this, this);

        if (allPermissionsGranted()) {
            cameraHelper.startCamera();
            gpsHelper.requestLocationUpdates();
            initOrientationSensors();
        } else {
            requestPermissions();
        }

        captureButton.setOnClickListener(view -> takePicture());
    }

    private void initOrientationSensors() {
        // Check if Google Play Services is available
        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        int resultCode = apiAvailability.isGooglePlayServicesAvailable(this);

        if (resultCode == ConnectionResult.SUCCESS) {
            // Google Play Services is available, use FOP
            initFusedOrientationProvider();
        } else {
            // Fall back to standard sensors
            initStandardSensors();
            useFusedOrientation = false;

            if (resultCode == ConnectionResult.SERVICE_MISSING ||
                    resultCode == ConnectionResult.SERVICE_UPDATING ||
                    resultCode == ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED) {
                Log.w(TAG, "Google Play Services unavailable, using standard sensors");
            }
        }
    }


    private void initFusedOrientationProvider() {
        // Get the FusedOrientationProviderClient instance
        fusedOrientationClient = LocationServices.getFusedOrientationProviderClient(this);

        // Create a device orientation listener
        deviceOrientationListener = new DeviceOrientationListener() {
            @Override
            public void onDeviceOrientationChanged(DeviceOrientation deviceOrientation) {
                // Extract quaternion from device orientation
                float[] quaternion = deviceOrientation.getAttitude();

                // The quaternion is in the format [qx, qy, qz, qw]
                qx = quaternion[0];
                qy = quaternion[1];
                qz = quaternion[2];
                qw = quaternion[3];

                updateDisplay();
            }
        };


        // Create orientation request with default update period
        DeviceOrientationRequest request = new DeviceOrientationRequest.Builder(
                DeviceOrientationRequest.OUTPUT_PERIOD_DEFAULT)
                .build();

        // Register for orientation updates
        Task<Void> task = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            task = fusedOrientationClient.requestOrientationUpdates(
                    request,
                    getMainExecutor(), // Or use a custom executor
                    deviceOrientationListener
            );
        }

        task.addOnSuccessListener(result -> {
            Log.d(TAG, "Successfully registered for FOP updates");
            useFusedOrientation = true;
        });

        task.addOnFailureListener(e -> {
            Log.e(TAG, "Failed to register for FOP updates", e);
            // Fall back to standard sensors
            initStandardSensors();
            useFusedOrientation = false;
        });
    }


    private void initStandardSensors() {
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (sensorManager != null) {
            rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
            if (rotationVectorSensor != null) {
                sensorManager.registerListener(this, rotationVectorSensor, SensorManager.SENSOR_DELAY_UI);
                Log.d(TAG, "Using standard rotation vector sensor");
            } else {
                Log.e(TAG, "Rotation vector sensor not available");
            }
        }
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
                    Log.e(TAG, "Error launching image details activity", e);
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
        // Only process sensor events if we're not using FOP
        if (!useFusedOrientation && event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
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

        String data = String.format(
                "Celestial Coordinates:\nRA: %.2f hours\nDec: %.2f°\nAz: %.2f°\nAlt: %.2f°\n" +
                        "Using: %s",
                coords.rightAscension, coords.declination, coords.azimuth, coords.altitude,
                useFusedOrientation ? "Fused Orientation Provider" : "Standard Sensors"
        );
        sensorData.setText(data);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Handle accuracy changes if needed
    }

    @Override
    protected void onPause() {
        super.onPause();

        // Safely unregister sensor listener
        if (!useFusedOrientation && sensorManager != null && rotationVectorSensor != null) {
            sensorManager.unregisterListener(this);
        }

        // Stop FOP updates if using them
        if (useFusedOrientation && fusedOrientationClient != null && deviceOrientationListener != null) {
            fusedOrientationClient.removeOrientationUpdates(deviceOrientationListener);
        }

        // Stop GPS updates
        gpsHelper.stopLocationUpdates();
    }


    @Override
    protected void onResume() {
        super.onResume();
        if (allPermissionsGranted()) {
            // Re-register appropriate sensor listeners
            if (!useFusedOrientation && rotationVectorSensor != null) {
                sensorManager.registerListener(this, rotationVectorSensor, SensorManager.SENSOR_DELAY_UI);
            } else if (useFusedOrientation && fusedOrientationClient != null && deviceOrientationListener != null) {
                // Restart FOP updates
                DeviceOrientationRequest request = new DeviceOrientationRequest.Builder(
                        DeviceOrientationRequest.OUTPUT_PERIOD_DEFAULT)
                        .build();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    fusedOrientationClient.requestOrientationUpdates(request, getMainExecutor(), deviceOrientationListener);
                }
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

        // Clean up any remaining listeners
        if (!useFusedOrientation && sensorManager != null && rotationVectorSensor != null) {
            sensorManager.unregisterListener(this);
        }

        if (useFusedOrientation && fusedOrientationClient != null && deviceOrientationListener != null) {
            fusedOrientationClient.removeOrientationUpdates(deviceOrientationListener);
        }
    }
}
