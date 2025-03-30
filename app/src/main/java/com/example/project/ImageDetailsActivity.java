package com.example.project;

import android.content.ContentValues;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.example.project.api.ApiClient;
import com.example.project.api.CelestialApiService;
import com.example.project.api.CelestialRequest;
import com.example.project.api.CelestialResponse;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ImageDetailsActivity extends AppCompatActivity {
    private static final String TAG = "ImageDetailsActivity";
    private ImageView detailImageView;
    private TextView detailDataTextView;
    private Button detectButton;
    private ImageView resultImageView;
    private TextView detectedBodiesTextView;

    private Uri imageUri;
    // Replace roll, pitch, yaw with quaternion components
    private float quaternionX;
    private float quaternionY;
    private float quaternionZ;
    private float quaternionW;
    private double latitude;
    private double longitude;
    private double altitude;
    private Bitmap originalBitmap;
    private Bitmap starMapBitmap;
    private ProgressBar progressBar;
    private CheckBox detectPlanetsCheckBox;
    private boolean starsDetected = false;
    private StringBuilder allCelestialInfo = new StringBuilder();
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main2);

        // Initialize views
        detailImageView = findViewById(R.id.detailImageView);
        detailDataTextView = findViewById(R.id.detailDataTextView);
        detectButton = findViewById(R.id.detectButton);
        resultImageView = findViewById(R.id.resultImageView);
        detectedBodiesTextView = findViewById(R.id.detectedBodiesTextView);
        progressBar = findViewById(R.id.progressBar);
        detectPlanetsCheckBox = findViewById(R.id.detectPlanetsCheckBox);

        detectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Reset state
                starsDetected = false;
                allCelestialInfo = new StringBuilder();

                // Show loading state
                progressBar.setVisibility(View.VISIBLE);
                detectButton.setEnabled(false);
                detectPlanetsCheckBox.setEnabled(false);
                resultImageView.setVisibility(View.GONE);
                detectedBodiesTextView.setVisibility(View.GONE);

                detectCelestialBodies();
            }
        });

        try {
            // Get data from intent
            if (getIntent() != null && getIntent().getExtras() != null) {
                // Use getParcelableExtra with type token for Android 13+ compatibility
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    imageUri = getIntent().getParcelableExtra("image_uri", Uri.class);
                } else {
                    imageUri = getIntent().getParcelableExtra("image_uri");
                }

                latitude = getIntent().getDoubleExtra("latitude", 0.0);
                longitude = getIntent().getDoubleExtra("longitude", 0.0);
                altitude = getIntent().getDoubleExtra("altitude", 0.0);

                // Get quaternion components instead of roll, pitch, yaw
                quaternionX = getIntent().getFloatExtra("quaternion_x", 0.0f);
                quaternionY = getIntent().getFloatExtra("quaternion_y", 0.0f);
                quaternionZ = getIntent().getFloatExtra("quaternion_z", 0.0f);
                quaternionW = getIntent().getFloatExtra("quaternion_w", 0.0f);

                // Display image
                if (imageUri != null) {
                    try {
                        Log.d(TAG, "Loading image from URI: " + imageUri);

                        // More robust image loading
                        detailImageView.setImageURI(null); // Clear any previous image
                        detailImageView.setImageURI(imageUri);

                        // If the above fails, try with bitmap as fallback
                        if (detailImageView.getDrawable() == null) {
                            Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), imageUri);
                            detailImageView.setImageBitmap(bitmap);
                        }
                    } catch (IOException e) {
                        Log.e(TAG, "Error loading image", e);
                        Toast.makeText(this, "Error loading image: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        Log.e(TAG, "Unexpected error", e);
                        Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Log.e(TAG, "Image URI is null");
                    Toast.makeText(this, "Error: Image URI is null", Toast.LENGTH_SHORT).show();
                }

                // Display sensor data with quaternion values
                String data = String.format(
                        "Orientation (Quaternion):\nX: %.4f\nY: %.4f\nZ: %.4f\nW: %.4f\n\n" +
                                "GPS Location:\nLatitude: %.6f\nLongitude: %.6f\nAltitude: %.2f m",
                        quaternionX, quaternionY, quaternionZ, quaternionW, latitude, longitude, altitude
                );
                detailDataTextView.setText(data);
                Log.d(TAG, "Data loaded successfully");
            } else {
                Log.e(TAG, "Intent or extras are null");
                Toast.makeText(this, "No data provided", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Critical error in onCreate", e);
            Toast.makeText(this, "Error initializing activity: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void saveImageToGallery(Bitmap bitmap) {
        try {
            // Create a filename with timestamp
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            String imageFileName = "CELESTIAL_" + timeStamp + ".jpg";

            // For Android 10 (API 29) and above
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME, imageFileName);
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CelestialDetector");

                Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (uri != null) {
                    try (OutputStream outputStream = getContentResolver().openOutputStream(uri)) {
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream);
                        Toast.makeText(this, "Image saved to gallery", Toast.LENGTH_SHORT).show();
                    }
                }
            }
            // For older Android versions
            else {
                String imagesDir = Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_PICTURES).toString() + "/CelestialDetector";

                // Create the directory if it doesn't exist
                File directory = new File(imagesDir);
                if (!directory.exists()) {
                    directory.mkdirs();
                }

                File file = new File(imagesDir, imageFileName);
                try (FileOutputStream outputStream = new FileOutputStream(file)) {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream);

                    // Add to gallery
                    MediaScannerConnection.scanFile(this,
                            new String[]{file.getAbsolutePath()},
                            new String[]{"image/jpeg"},
                            null);

                    Toast.makeText(this, "Image saved to gallery", Toast.LENGTH_SHORT).show();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error saving image to gallery", e);
            Toast.makeText(this, "Failed to save image: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void detectCelestialBodies() {
        try {
            // Load the image
            originalBitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), imageUri);

            // Create a copy of the bitmap to draw on
            starMapBitmap = originalBitmap.copy(originalBitmap.getConfig(), true);
            Canvas canvas = new Canvas(starMapBitmap);

            // First, detect stars using the existing system
            detectStarsUsingLocalDatabase(canvas);

            // Then, if checkbox is checked, detect planets, Sun, and Moon using the API
            if (detectPlanetsCheckBox.isChecked()) {
                detectPlanetsSunMoonUsingApi(canvas);
            } else {
                // If not detecting planets, complete the process now
                finishDetection();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error identifying celestial bodies", e);
            Toast.makeText(this, "Error identifying celestial bodies: " + e.getMessage(),
                    Toast.LENGTH_SHORT).show();
            hideLoadingState();
        }
    }

    private void detectStarsUsingLocalDatabase(Canvas canvas) {
        try {
            if (!StarDatabase.isStarDataLoaded()) {
                Toast.makeText(this, "Star database not loaded. Please try again later.",
                        Toast.LENGTH_LONG).show();
                hideLoadingState();
                return;
            }

            int width = originalBitmap.getWidth();
            int height = originalBitmap.getHeight();

            // Create paint for drawing stars
            Paint starPaint = new Paint();
            starPaint.setColor(Color.GREEN);
            starPaint.setStyle(Paint.Style.STROKE);
            starPaint.setStrokeWidth(2);

            Paint textPaint = new Paint();
            textPaint.setColor(Color.YELLOW);
            textPaint.setTextSize(24);

            // Get the celestial coordinates at the center of the image from quaternion
            AstronomicalCalculator.CelestialCoordinates centerCoords =
                    AstronomicalCalculator.calculateCoordinatesFromQuaternion(
                            latitude, longitude, altitude,
                            quaternionX, quaternionY, quaternionZ, quaternionW);

            // Estimate field of view based on device camera
            double fovDegrees = 66.0;

            // Create the WCS converter
            PixelToCelestialConverter converter = new PixelToCelestialConverter(
                    width, height, centerCoords, fovDegrees);

            // Get stars that should be visible in this field of view
            List<StarDatabase.Star> visibleStars = StarDatabase.getStarsInFieldOfView(
                    centerCoords.rightAscension, centerCoords.declination,
                    fovDegrees, fovDegrees * height / width, 4.0);

            // Build a string with star information
            allCelestialInfo.append("Stars in view:\n");

            // For each star, find its pixel position and mark it
            for (StarDatabase.Star star : visibleStars) {
                double[] pixelCoords = converter.celestialToPixel(star.rightAscension, star.declination);

                if (!Double.isNaN(pixelCoords[0]) && !Double.isNaN(pixelCoords[1]) &&
                        pixelCoords[0] >= 0 && pixelCoords[0] < width &&
                        pixelCoords[1] >= 0 && pixelCoords[1] < height) {

                    canvas.drawCircle((float)pixelCoords[0], (float)pixelCoords[1], 20, starPaint);
                    canvas.drawText(star.name, (float)pixelCoords[0] - 10, (float)pixelCoords[1] - 25, textPaint);
                    allCelestialInfo.append(star.name).append("\n");
                }
            }

            starsDetected = true;

            // If not detecting planets, finish the process
            if (!detectPlanetsCheckBox.isChecked()) {
                finishDetection();
            }

        } catch (Exception e) {
            Log.e(TAG, "Error identifying stars", e);
            Toast.makeText(this, "Error identifying stars: " + e.getMessage(),
                    Toast.LENGTH_SHORT).show();
            hideLoadingState();
        }
    }

    private void detectPlanetsSunMoonUsingApi(final Canvas canvas) {
        try {
            // Show loading indicator
            Toast.makeText(this, "Fetching celestial data...", Toast.LENGTH_SHORT).show();

            // Get current timestamp in seconds
            long timestamp = System.currentTimeMillis() / 1000;

            // Create request object
            CelestialRequest request = new CelestialRequest(
                    latitude, longitude, altitude, timestamp);

            // Call API
            CelestialApiService apiService = ApiClient.getCelestialApiService(this);
            apiService.getCelestialCoordinates(request).enqueue(new retrofit2.Callback<CelestialResponse>() {
                @Override
                public void onResponse(retrofit2.Call<CelestialResponse> call, retrofit2.Response<CelestialResponse> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        processCelestialData(response.body(), canvas);
                    } else {
                        String errorMsg = "API error: " + (response.errorBody() != null ?
                                response.errorBody().toString() : "Unknown error");
                        Log.e(TAG, errorMsg);
                        Toast.makeText(ImageDetailsActivity.this, errorMsg, Toast.LENGTH_LONG).show();
                        finishDetection();
                    }
                }

                @Override
                public void onFailure(retrofit2.Call<CelestialResponse> call, Throwable t) {
                    Log.e(TAG, "API call failed", t);
                    Toast.makeText(ImageDetailsActivity.this,
                            "Failed to get celestial data: " + t.getMessage(), Toast.LENGTH_LONG).show();
                    finishDetection();
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Error identifying celestial bodies", e);
            Toast.makeText(this, "Error identifying celestial bodies: " + e.getMessage(),
                    Toast.LENGTH_SHORT).show();
            hideLoadingState();
        }
    }

    private void processCelestialData(CelestialResponse celestialData, Canvas canvas) {
        Map<String, CelestialResponse.CelestialBodyPosition> bodies = celestialData.getCelestialBodies();

        // Create paints for different celestial bodies
        Paint sunPaint = new Paint();
        sunPaint.setColor(Color.YELLOW);
        sunPaint.setStyle(Paint.Style.STROKE);
        sunPaint.setStrokeWidth(3);

        Paint moonPaint = new Paint();
        moonPaint.setColor(Color.WHITE);
        moonPaint.setStyle(Paint.Style.STROKE);
        moonPaint.setStrokeWidth(3);

        Paint planetPaint = new Paint();
        planetPaint.setColor(Color.RED);
        planetPaint.setStyle(Paint.Style.STROKE);
        planetPaint.setStrokeWidth(2);

        Paint textPaint = new Paint();
        textPaint.setColor(Color.CYAN);
        textPaint.setTextSize(24);

        // Get the celestial coordinates at the center of the image from quaternion
        AstronomicalCalculator.CelestialCoordinates centerCoords =
                AstronomicalCalculator.calculateCoordinatesFromQuaternion(
                        latitude, longitude, altitude,
                        quaternionX, quaternionY, quaternionZ, quaternionW);

        // Create the WCS converter
        int width = originalBitmap.getWidth();
        int height = originalBitmap.getHeight();
        double fovDegrees = 66.0;
        PixelToCelestialConverter converter = new PixelToCelestialConverter(
                width, height, centerCoords, fovDegrees);

        // Append to the existing text
        allCelestialInfo.append("\nPlanets, Sun & Moon:\n");

        // Draw each celestial body on the image
        for (Map.Entry<String, CelestialResponse.CelestialBodyPosition> entry : bodies.entrySet()) {
            String bodyName = entry.getKey();
            CelestialResponse.CelestialBodyPosition position = entry.getValue();

            // Convert RA/Dec to pixel coordinates
            double[] pixelCoords = converter.celestialToPixel(
                    position.ra.getHours(), position.dec.getDegrees());

            if (!Double.isNaN(pixelCoords[0]) && !Double.isNaN(pixelCoords[1]) &&
                    pixelCoords[0] >= 0 && pixelCoords[0] < width &&
                    pixelCoords[1] >= 0 && pixelCoords[1] < height) {

                Paint paint;
                int radius;

                // Choose appropriate paint and size based on body type
                if ("sun".equals(bodyName)) {
                    paint = sunPaint;
                    radius = 35;
                } else if ("moon".equals(bodyName)) {
                    paint = moonPaint;
                    radius = 30;
                } else {
                    paint = planetPaint;
                    radius = 25;
                }

                // Draw circle around the celestial body
                canvas.drawCircle((float)pixelCoords[0], (float)pixelCoords[1], radius, paint);

                // Draw the name
                canvas.drawText(bodyName.toUpperCase(),
                        (float)pixelCoords[0] - 15, (float)pixelCoords[1] - radius - 10, textPaint);

                // Add to info text
                allCelestialInfo.append(bodyName).append("\n");
            }
        }

        // Complete the detection process
        finishDetection();
    }

    private void finishDetection() {
        // Update the image and text view
        runOnUiThread(() -> {
            resultImageView.setImageBitmap(starMapBitmap);
            resultImageView.setVisibility(View.VISIBLE);
            detectedBodiesTextView.setText(allCelestialInfo.toString());
            detectedBodiesTextView.setVisibility(View.VISIBLE);

            String message = starsDetected ? "Celestial bodies identified successfully" : "No celestial bodies detected";
            Toast.makeText(ImageDetailsActivity.this, message, Toast.LENGTH_SHORT).show();

            hideLoadingState();
        });

        // Save the updated image
        saveImageToGallery(starMapBitmap);
    }

    private void hideLoadingState() {
        runOnUiThread(() -> {
            progressBar.setVisibility(View.GONE);
            detectButton.setEnabled(true);
            detectPlanetsCheckBox.setEnabled(true);
        });
    }
}
