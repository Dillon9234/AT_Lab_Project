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
import android.view.Surface;
import android.view.WindowManager;
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
    private float roll, pitch, yaw;
    private double latitude = 0.0, longitude = 0.0, altitude = 0.0;
    private WindowManager windowManager;

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
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

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
        CameraHelper.SensorData currentSensorData = new CameraHelper.SensorData(
                roll, pitch, yaw, latitude, longitude, altitude
        );

        cameraHelper.takePicture(currentSensorData, new CameraHelper.CaptureCallback() {
            @Override
            public void onImageCaptured(Uri imageUri) {
                try {
                    Intent intent = new Intent(MainActivity.this, ImageDetailsActivity.class);
                    intent.putExtra("image_uri", imageUri);
                    intent.putExtra("latitude", latitude);
                    intent.putExtra("longitude", longitude);
                    intent.putExtra("altitude", altitude);
                    intent.putExtra("roll", roll);
                    intent.putExtra("pitch", pitch);
                    intent.putExtra("yaw", yaw);
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
            float[] rotationMatrix = new float[9];
            float[] orientationAngles = new float[3];
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
            SensorManager.getOrientation(rotationMatrix, orientationAngles);

            yaw = (float) Math.toDegrees(orientationAngles[0]);
            pitch = (float) Math.toDegrees(orientationAngles[1]);
            roll = (float) Math.toDegrees(orientationAngles[2]);

            int rotation = windowManager.getDefaultDisplay().getRotation();
            if (rotation == Surface.ROTATION_90) {
                float temp = roll;
                roll = -pitch;
                pitch = temp;
            } else if (rotation == Surface.ROTATION_180) {
                pitch = -pitch;
                roll = -roll;
            } else if (rotation == Surface.ROTATION_270) {
                float temp = roll;
                roll = pitch;
                pitch = -temp;
            }
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
        String data = String.format(
                "Orientation:\nRoll: %.2f°\nPitch: %.2f°\nYaw: %.2f°\n\n" +
                        "GPS Location:\nLatitude: %.6f\nLongitude: %.6f\nAltitude: %.2f m",
                roll, pitch, yaw, latitude, longitude, altitude
        );
        sensorData.setText(data);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
        gpsHelper.stopLocationUpdates();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (allPermissionsGranted()) {
            gpsHelper.requestLocationUpdates();
            cameraHelper.startCamera();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraHelper.release();
    }
}