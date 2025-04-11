package com.example.project;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.example.project.api.ApiClient;
import com.example.project.api.CelestialApiService;
import com.example.project.api.CelestialRequest;
import com.example.project.api.CelestialResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CenterSquareDetectionActivity extends AppCompatActivity {
    private static final String TAG = "CenterSquareDetection";

    private ImageView centerSquareImageView;
    private TextView detectedBodyTextView;
    private Button detectButton;
    private ProgressBar progressBar;

    private Uri imageUri;
    private Bitmap originalBitmap;
    private Bitmap processedBitmap;

    private float quaternionX;
    private float quaternionY;
    private float quaternionZ;
    private float quaternionW;
    private double latitude;
    private double longitude;
    private double altitude;

    private Rect centerSquare;
    private PixelToCelestialConverter converter;
    private CheckBox detectPlanetsCheckBox;
    private EditText serverUrlEditText;
    private View serverUrlContainer;
    private static final float SQUARE_SIZE_RATIO = 0.1f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_center_square_detection);

        // Initialize views
        centerSquareImageView = findViewById(R.id.centerSquareImageView);
        detectedBodyTextView = findViewById(R.id.detectedBodyTextView);
        detectButton = findViewById(R.id.detectButton);
        progressBar = findViewById(R.id.progressBar);
        // Initialize server URL and checkbox components
        detectPlanetsCheckBox = findViewById(R.id.detectPlanetsCheckBox);
        serverUrlEditText = findViewById(R.id.serverUrlEditText);
        serverUrlContainer = findViewById(R.id.serverUrlContainer);

        // Set initial visibility based on checkbox state
        serverUrlContainer.setVisibility(detectPlanetsCheckBox.isChecked() ? View.VISIBLE : View.GONE);

        // Initialize server URL field with current value from ApiClient
        serverUrlEditText.setText(ApiClient.getBaseUrl(this));

        // Add checkbox listener to toggle URL input visibility
        detectPlanetsCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            serverUrlContainer.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });


        // Get data from intent
        if (getIntent() != null && getIntent().getExtras() != null) {
            // Get image URI
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                imageUri = getIntent().getParcelableExtra("image_uri", Uri.class);
            } else {
                imageUri = getIntent().getParcelableExtra("image_uri");
            }

            // Get sensor data
            latitude = getIntent().getDoubleExtra("latitude", 0.0);
            longitude = getIntent().getDoubleExtra("longitude", 0.0);
            altitude = getIntent().getDoubleExtra("altitude", 0.0);

            quaternionX = getIntent().getFloatExtra("quaternion_x", 0.0f);
            quaternionY = getIntent().getFloatExtra("quaternion_y", 0.0f);
            quaternionZ = getIntent().getFloatExtra("quaternion_z", 0.0f);
            quaternionW = getIntent().getFloatExtra("quaternion_w", 0.0f);
        }

        // Load the image
        loadImage();

        // Set up detect button click listener
        detectButton.setOnClickListener(v -> detectCelestialBodiesInSquare());
    }

    private void loadImage() {
        try {
            if (imageUri != null) {
                originalBitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), imageUri);
                centerSquareImageView.setImageBitmap(originalBitmap);
            } else {
                Toast.makeText(this, "Error: Image URI is null", Toast.LENGTH_SHORT).show();
            }
        } catch (IOException e) {
            Log.e(TAG, "Error loading image", e);
            Toast.makeText(this, "Error loading image: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void detectCelestialBodiesInSquare() {
        // Show loading state
        progressBar.setVisibility(View.VISIBLE);
        detectButton.setEnabled(false);
        detectPlanetsCheckBox.setEnabled(false);
        serverUrlContainer.setEnabled(false);
        serverUrlEditText.setEnabled(false);
        detectedBodyTextView.setVisibility(View.GONE);

        // Save the server URL if planets detection is enabled
        if (detectPlanetsCheckBox.isChecked()) {
            String serverUrl = serverUrlEditText.getText().toString().trim();
            if (!serverUrl.isEmpty()) {
                ApiClient.setBaseUrl(this, serverUrl);
            }
        }

        try {
            // Create a copy of the bitmap to draw on
            processedBitmap = originalBitmap.copy(originalBitmap.getConfig(), true);
            Canvas canvas = new Canvas(processedBitmap);

            // Draw center square
            int width = processedBitmap.getWidth();
            int height = processedBitmap.getHeight();
            int squareSize = (int) (Math.min(width, height) * SQUARE_SIZE_RATIO);
            int left = (width - squareSize) / 2;
            int top = (height - squareSize) / 2;
            centerSquare = new Rect(left, top, left + squareSize, top + squareSize);

            Paint squarePaint = new Paint();
            squarePaint.setColor(Color.RED);
            squarePaint.setStyle(Paint.Style.STROKE);
            squarePaint.setStrokeWidth(5);
            canvas.drawRect(centerSquare, squarePaint);

            // Calculate center of the square
            Point squareCenter = new Point(centerSquare.centerX(), centerSquare.centerY());

            // Get the celestial coordinates at the center of the image from quaternion
            AstronomicalCalculator.CelestialCoordinates centerCoords =
                    AstronomicalCalculator.calculateCoordinatesFromQuaternion(
                            latitude, longitude, altitude,
                            quaternionX, quaternionY, quaternionZ, quaternionW);

            // Estimate field of view based on device camera
            double fovDegrees = 66.0;

            // Create the WCS converter
            converter = new PixelToCelestialConverter(
                    width, height, centerCoords, fovDegrees);

            // First detect stars using the local database
            List<CelestialBody> detectedBodies = new ArrayList<>();

            // Get stars that should be visible in this field of view
            List<StarDatabase.Star> visibleStars = StarDatabase.getStarsInFieldOfView(
                    centerCoords.rightAscension, centerCoords.declination,
                    fovDegrees, fovDegrees * height / width, 4.0);

            // Add stars to the detected bodies list
            for (StarDatabase.Star star : visibleStars) {
                detectedBodies.add(new CelestialBody(star.name, star.rightAscension, star.declination, CelestialBodyType.STAR));
            }

            // Now get planets, sun, and moon using the API
            fetchPlanetsSunMoon(detectedBodies, canvas, squareCenter);

        } catch (Exception e) {
            Log.e(TAG, "Error in celestial detection", e);
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            hideLoadingState();
        }
    }

    private void fetchPlanetsSunMoon(List<CelestialBody> detectedBodies, Canvas canvas, Point squareCenter) {
        // Only fetch planets if the checkbox is checked
        if (!detectPlanetsCheckBox.isChecked()) {
            // Skip API call and process with just stars
            processDetectedBodies(detectedBodies, canvas, squareCenter);
            return;
        }

        try {
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
                        Map<String, CelestialResponse.CelestialBodyPosition> bodies = response.body().getCelestialBodies();

                        // Add planets, sun, and moon to the detected bodies list
                        for (Map.Entry<String, CelestialResponse.CelestialBodyPosition> entry : bodies.entrySet()) {
                            String bodyName = entry.getKey();
                            CelestialResponse.CelestialBodyPosition position = entry.getValue();

                            CelestialBodyType type;
                            if ("sun".equals(bodyName)) {
                                type = CelestialBodyType.SUN;
                            } else if ("moon".equals(bodyName)) {
                                type = CelestialBodyType.MOON;
                            } else {
                                type = CelestialBodyType.PLANET;
                            }

                            detectedBodies.add(new CelestialBody(
                                    bodyName, position.ra.getHours(), position.dec.getDegrees(), type));
                        }

                        // Process all detected bodies
                        processDetectedBodies(detectedBodies, canvas, squareCenter);
                    } else {
                        String errorMsg = "API error: " + (response.errorBody() != null ?
                                response.errorBody().toString() : "Unknown error");
                        Log.e(TAG, errorMsg);
                        Toast.makeText(CenterSquareDetectionActivity.this,
                                "Server error. Continuing with stars only.", Toast.LENGTH_LONG).show();

                        // Continue with just stars, similar to ImageDetailsActivity
                        processDetectedBodies(detectedBodies, canvas, squareCenter);
                    }
                }

                @Override
                public void onFailure(retrofit2.Call<CelestialResponse> call, Throwable t) {
                    Log.e(TAG, "API call failed", t);
                    Toast.makeText(CenterSquareDetectionActivity.this,
                            "Server unavailable. Continuing with stars only.", Toast.LENGTH_LONG).show();

                    // Continue with just stars, similar to ImageDetailsActivity
                    processDetectedBodies(detectedBodies, canvas, squareCenter);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error fetching planets data", e);
            Toast.makeText(this, "Error connecting to server. Using stars only.", Toast.LENGTH_LONG).show();

            // Continue with just stars if there's any exception
            processDetectedBodies(detectedBodies, canvas, squareCenter);
        }
    }

    private void processDetectedBodies(List<CelestialBody> detectedBodies, Canvas canvas, Point squareCenter) {
        CelestialBody bodyInSquare = null;
        CelestialBody closestBody = null;
        int minDistance = Integer.MAX_VALUE;

        // Create paints for different celestial bodies
        Paint starPaint = new Paint();
        starPaint.setColor(Color.GREEN);
        starPaint.setStyle(Paint.Style.STROKE);
        starPaint.setStrokeWidth(2);

        Paint planetPaint = new Paint();
        planetPaint.setColor(Color.RED);
        planetPaint.setStyle(Paint.Style.STROKE);
        planetPaint.setStrokeWidth(2);

        Paint sunPaint = new Paint();
        sunPaint.setColor(Color.YELLOW);
        sunPaint.setStyle(Paint.Style.STROKE);
        sunPaint.setStrokeWidth(3);

        Paint moonPaint = new Paint();
        moonPaint.setColor(Color.WHITE);
        moonPaint.setStyle(Paint.Style.STROKE);
        moonPaint.setStrokeWidth(3);

        Paint textPaint = new Paint();
        textPaint.setColor(Color.CYAN);
        textPaint.setTextSize(24);

        for (CelestialBody body : detectedBodies) {
            double[] pixelCoords = converter.celestialToPixel(body.rightAscension, body.declination);

            // Skip if outside the image
            if (Double.isNaN(pixelCoords[0]) || Double.isNaN(pixelCoords[1]) ||
                    pixelCoords[0] < 0 || pixelCoords[0] >= originalBitmap.getWidth() ||
                    pixelCoords[1] < 0 || pixelCoords[1] >= originalBitmap.getHeight()) {
                continue;
            }

            Point bodyPosition = new Point((int)pixelCoords[0], (int)pixelCoords[1]);

            // Choose appropriate paint based on body type
            Paint paint;
            int radius;

            switch (body.type) {
                case SUN:
                    paint = sunPaint;
                    radius = 35;
                    break;
                case MOON:
                    paint = moonPaint;
                    radius = 30;
                    break;
                case PLANET:
                    paint = planetPaint;
                    radius = 25;
                    break;
                case STAR:
                default:
                    paint = starPaint;
                    radius = 20;
                    break;
            }

            // Check if the body is inside the center square
            if (centerSquare.contains(bodyPosition.x, bodyPosition.y)) {
                bodyInSquare = body;
                bodyInSquare.pixelX = bodyPosition.x;
                bodyInSquare.pixelY = bodyPosition.y;
                bodyInSquare.paint = paint;
                bodyInSquare.radius = radius;

                canvas.drawCircle(bodyPosition.x, bodyPosition.y, radius, paint);
                canvas.drawText(body.name, bodyPosition.x - 10, bodyPosition.y - radius - 5, textPaint);
                // Found a body in the square, break out of the loop
                break;
            } else {
                // Calculate distance to square center
                int distance = calculateDistance(squareCenter, bodyPosition);
                if (distance < minDistance) {
                    minDistance = distance;
                    closestBody = body;
                    closestBody.pixelX = bodyPosition.x;
                    closestBody.pixelY = bodyPosition.y;
                    closestBody.paint = paint;
                    closestBody.radius = radius;
                }
            }
        }

// Draw only one celestial body - either the one in the square or the closest one
        if (bodyInSquare != null) {
            // Draw the body in the square
            canvas.drawCircle(bodyInSquare.pixelX, bodyInSquare.pixelY, bodyInSquare.radius, bodyInSquare.paint);
            canvas.drawText(bodyInSquare.name, bodyInSquare.pixelX - 10, bodyInSquare.pixelY - bodyInSquare.radius - 5, textPaint);
        } else if (closestBody != null) {
            // Draw the closest body
            canvas.drawCircle(closestBody.pixelX, closestBody.pixelY, closestBody.radius, closestBody.paint);
            canvas.drawText(closestBody.name, closestBody.pixelX - 10, closestBody.pixelY - closestBody.radius - 5, textPaint);
        }

        // Update UI with results
        updateDetectionResults(bodyInSquare, closestBody, squareCenter, minDistance);
    }

    private int calculateDistance(Point p1, Point p2) {
        return (int) Math.sqrt(Math.pow(p2.x - p1.x, 2) + Math.pow(p2.y - p1.y, 2));
    }

    private void updateDetectionResults(CelestialBody bodyInSquare, CelestialBody closestBody,
                                        Point squareCenter, int minDistance) {
        runOnUiThread(() -> {
            // Update the image view with the processed bitmap
            centerSquareImageView.setImageBitmap(processedBitmap);

            StringBuilder resultText = new StringBuilder();

            if (bodyInSquare != null) {
                resultText.append("Detected in center square: ").append(bodyInSquare.name.toUpperCase());
                resultText.append("\nType: ").append(bodyInSquare.type.toString());
                resultText.append("\nPosition: (").append(bodyInSquare.pixelX).append(", ")
                        .append(bodyInSquare.pixelY).append(")");
            } else if (closestBody != null) {
                resultText.append("No celestial bodies in center square.\n\n");
                resultText.append("Closest body: ").append(closestBody.name.toUpperCase());
                resultText.append("\nType: ").append(closestBody.type.toString());
                resultText.append("\nDistance from center: ").append(minDistance).append(" pixels");
                resultText.append("\nPosition: (").append(closestBody.pixelX).append(", ")
                        .append(closestBody.pixelY).append(")");
            } else {
                resultText.append("No celestial bodies detected in the image.");
            }

            detectedBodyTextView.setText(resultText.toString());
            detectedBodyTextView.setVisibility(View.VISIBLE);

            hideLoadingState();
        });
    }

    private void hideLoadingState() {
        progressBar.setVisibility(View.GONE);
        detectButton.setEnabled(true);
        detectPlanetsCheckBox.setEnabled(true);
        serverUrlContainer.setEnabled(true);
        serverUrlEditText.setEnabled(true);
    }

    // Helper class to represent celestial body types
    private enum CelestialBodyType {
        STAR, PLANET, SUN, MOON
    }

    // Helper class to represent celestial bodies
    private static class CelestialBody {
        public Paint paint;
        public float radius;
        String name;
        double rightAscension;
        double declination;
        CelestialBodyType type;
        int pixelX;
        int pixelY;

        CelestialBody(String name, double rightAscension, double declination, CelestialBodyType type) {
            this.name = name;
            this.rightAscension = rightAscension;
            this.declination = declination;
            this.type = type;
        }
    }
}
