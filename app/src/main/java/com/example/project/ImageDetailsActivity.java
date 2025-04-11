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

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDouble;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class ImageDetailsActivity extends AppCompatActivity {
    private static final String TAG = "ImageDetailsActivity";
    private Button detectButton;
    private ImageView resultImageView;
    private TextView detectedBodiesTextView;

    private Uri imageUri;
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
    private EditText serverUrlEditText;
    private View serverUrlContainer;
    private boolean starsDetected = false;
    private StringBuilder allCelestialInfo = new StringBuilder();
    static {
        if (!org.opencv.android.OpenCVLoader.initDebug()) {
            Log.e(TAG, "OpenCV initialization failed");
        } else {
            Log.d(TAG, "OpenCV initialized successfully");
        }
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main2);

        // Initialize views
        ImageView detailImageView = findViewById(R.id.detailImageView);
        TextView detailDataTextView = findViewById(R.id.detailDataTextView);
        detectButton = findViewById(R.id.detectButton);
        resultImageView = findViewById(R.id.resultImageView);
        detectedBodiesTextView = findViewById(R.id.detectedBodiesTextView);
        progressBar = findViewById(R.id.progressBar);
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
                serverUrlContainer.setEnabled(false);
                serverUrlEditText.setEnabled(false);
                resultImageView.setVisibility(View.GONE);
                detectedBodiesTextView.setVisibility(View.GONE);

                // Save the server URL if planets detection is enabled
                if (detectPlanetsCheckBox.isChecked()) {
                    String serverUrl = serverUrlEditText.getText().toString().trim();
                    if (!serverUrl.isEmpty()) {
                        ApiClient.setBaseUrl(ImageDetailsActivity.this, serverUrl);
                    }
                }

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
                        assert outputStream != null;
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
            starMapBitmap = originalBitmap.copy(Objects.requireNonNull(originalBitmap.getConfig()), true);
            Canvas canvas = new Canvas(starMapBitmap);

            // First, detect stars using the existing system
            detectStarsUsingLocalDatabase(canvas);

            // Then, if checkbox is checked, detect planets, Sun, and Moon using the API
            if (detectPlanetsCheckBox.isChecked()) {
                detectPlanetsSunMoonUsingApi(canvas);
            }
            finishDetection();
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

            List<CelestialBody> allBodies = new ArrayList<>();
            for (StarDatabase.Star star : visibleStars) {
                allBodies.add(new CelestialBody(star.name, star.rightAscension, star.declination, CelestialBodyType.STAR));
            }

            matchAndDrawStars(allBodies, canvas);

            starsDetected = true;

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
            ApiClient.setBaseUrl(this,"http://192.168.0.102:5000");
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
                    }
                }

                @Override
                public void onFailure(retrofit2.Call<CelestialResponse> call, Throwable t) {
                    Log.e(TAG, "API call failed", t);
                    Toast.makeText(ImageDetailsActivity.this,
                            "Failed to get celestial data: " + t.getMessage(), Toast.LENGTH_LONG).show();
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

        // Get stars that should be visible in this field of view
        List<StarDatabase.Star> visibleStars = StarDatabase.getStarsInFieldOfView(
                centerCoords.rightAscension, centerCoords.declination,
                fovDegrees, fovDegrees * height / width, 4.0);

        // Combine all celestial bodies (stars and planets/sun/moon)
        List<CelestialBody> allBodies = new ArrayList<>();

        // Add stars
        for (StarDatabase.Star star : visibleStars) {
            allBodies.add(new CelestialBody(star.name, star.rightAscension, star.declination, CelestialBodyType.STAR));
        }

        // Add planets, sun, and moon
        for (Map.Entry<String, CelestialResponse.CelestialBodyPosition> entry : bodies.entrySet()) {
            String bodyName = entry.getKey();
            CelestialResponse.CelestialBodyPosition position = entry.getValue();
            CelestialBodyType type = "sun".equals(bodyName) ? CelestialBodyType.SUN :
                    "moon".equals(bodyName) ? CelestialBodyType.MOON :
                            CelestialBodyType.PLANET;
            allBodies.add(new CelestialBody(bodyName, position.ra.getHours(), position.dec.getDegrees(), type));
        }

        matchAndDrawStars(allBodies, canvas);
    }

    private void matchAndDrawStars(List<CelestialBody> allBodies, Canvas canvas) {
        Paint starPaint = new Paint();
        starPaint.setColor(Color.GREEN);
        starPaint.setStyle(Paint.Style.STROKE);
        starPaint.setStrokeWidth(2);

        Paint textPaint = new Paint();
        textPaint.setColor(Color.CYAN);
        textPaint.setTextSize(24);

        Paint linePaint = new Paint();
        linePaint.setColor(Color.WHITE);
        linePaint.setStrokeWidth(2);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setAlpha(150);  // Semi-transparent

        int width = originalBitmap.getWidth();
        int height = originalBitmap.getHeight();

        // Get the celestial coordinates at the center of the image from quaternion
        AstronomicalCalculator.CelestialCoordinates centerCoords =
                AstronomicalCalculator.calculateCoordinatesFromQuaternion(
                        latitude, longitude, altitude,
                        quaternionX, quaternionY, quaternionZ, quaternionW);

        // Create the WCS converter
        double fovDegrees = 66.0;
        PixelToCelestialConverter converter = new PixelToCelestialConverter(
                width, height, centerCoords, fovDegrees);

        // Get actual detected stars
        List<Point> actualStars = detectStarsInImage(originalBitmap);

        // For each actual detected star, find the closest calculated celestial body
        for (Point actualStar : actualStars) {
            CelestialBody closestBody = null;
            double minDistance = Double.MAX_VALUE;

            for (CelestialBody body : allBodies) {
                double[] pixelCoords = converter.celestialToPixel(body.rightAscension, body.declination);

                if (!Double.isNaN(pixelCoords[0]) && !Double.isNaN(pixelCoords[1]) &&
                        pixelCoords[0] >= 0 && pixelCoords[0] < width &&
                        pixelCoords[1] >= 0 && pixelCoords[1] < height) {

                    double distance = Math.hypot(pixelCoords[0] - actualStar.x, pixelCoords[1] - actualStar.y);

                    if (distance < minDistance) {
                        minDistance = distance;
                        closestBody = body;
                        closestBody.pixelX = (int) pixelCoords[0];
                        closestBody.pixelY = (int) pixelCoords[1];
                    }
                }
            }

            // If a closest body was found, draw it and connect to the actual star
            if (closestBody != null) {
                // Draw the calculated celestial body
                canvas.drawCircle(closestBody.pixelX, closestBody.pixelY, 20, starPaint);
                canvas.drawText(closestBody.name, closestBody.pixelX - 10, closestBody.pixelY - 25, textPaint);

                // Draw the line connecting the actual star to the calculated position
                canvas.drawLine((float)actualStar.x, (float)actualStar.y, closestBody.pixelX, closestBody.pixelY, linePaint);

                // Draw the actual detected star
                canvas.drawCircle((float)actualStar.x, (float)actualStar.y, 5, starPaint);
            }
        }

        updateDetectedBodiesTextView(actualStars.size());
    }

    private void updateDetectedBodiesTextView(int matchedStarsCount) {

        runOnUiThread(() -> {
            String info = String.format("Detected %d stars in the image.\nMatched with closest calculated celestial bodies.", matchedStarsCount);
            resultImageView.setImageBitmap(starMapBitmap);
            resultImageView.setVisibility(View.VISIBLE);
            detectedBodiesTextView.setText(info);
            detectedBodiesTextView.setVisibility(View.VISIBLE);
            hideLoadingState();
        });
    }


    private void finishDetection() {
        String message = starsDetected ? "Celestial bodies identified successfully" : "No celestial bodies detected";
        Toast.makeText(ImageDetailsActivity.this, message, Toast.LENGTH_SHORT).show();
        saveImageToGallery(starMapBitmap);
    }

    private void hideLoadingState() {
        runOnUiThread(() -> {
            progressBar.setVisibility(View.GONE);
            detectButton.setEnabled(true);
            detectPlanetsCheckBox.setEnabled(true);
            serverUrlContainer.setEnabled(true);
            serverUrlEditText.setEnabled(true);
        });
    }

    private List<Point> detectStarsInImage(Bitmap bitmap) {
        List<Point> starCentroids = new ArrayList<>();
        Mat srcMat = new Mat();
        Utils.bitmapToMat(bitmap, srcMat);

        try {
            // Convert to grayscale and blur
            Mat grayMat = new Mat();
            Imgproc.cvtColor(srcMat, grayMat, Imgproc.COLOR_RGB2GRAY);
            Imgproc.GaussianBlur(grayMat, grayMat, new Size(3, 3), 0);

            // Adaptive thresholding with MEAN_C
            Mat thresholdMat = new Mat();
            Imgproc.adaptiveThreshold(grayMat, thresholdMat, 255,
                    Imgproc.ADAPTIVE_THRESH_MEAN_C, Imgproc.THRESH_BINARY, 11, -10);

            // Find contours
            List<MatOfPoint> contours = new ArrayList<>();
            Mat hierarchy = new Mat();
            Imgproc.findContours(thresholdMat, contours, hierarchy,
                    Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

            // Process contours
            for (MatOfPoint contour : contours) {
                // Area filter
                double area = Imgproc.contourArea(contour);
                if (area < 0.8 || area > 25) continue;

                // Bounding rectangle checks
                Rect rect = Imgproc.boundingRect(contour);
                double aspectRatio = Math.max(rect.width, rect.height) /
                        (Math.min(rect.width, rect.height) + 1e-5);
                if (aspectRatio > 4) continue;

                // Region of interest analysis
                int margin = 5;
                int x1 = Math.max(rect.x - margin, 0);
                int y1 = Math.max(rect.y - margin, 0);
                int x2 = Math.min(rect.x + rect.width + margin, grayMat.cols());
                int y2 = Math.min(rect.y + rect.height + margin, grayMat.rows());
                Mat roi = grayMat.submat(y1, y2, x1, x2);

                // Intensity analysis
                Scalar meanVal = Core.mean(roi);
                MatOfDouble stdDev = new MatOfDouble();
                Core.meanStdDev(roi, new MatOfDouble(), stdDev);
                double maxVal = Core.minMaxLoc(roi).maxVal;

                // Heuristic checks
                if (stdDev.toArray()[0] > 10 && maxVal > 160 && meanVal.val[0] < 100) {
                    Point center = new Point(
                            rect.x + rect.width/2.0,
                            rect.y + rect.height/2.0
                    );

                    // Non-maximum suppression check
                    boolean tooClose = false;
                    for (Point existing : starCentroids) {
                        if (Math.hypot(center.x - existing.x, center.y - existing.y) < 8) {
                            tooClose = true;
                            break;
                        }
                    }
                    if (!tooClose) {
                        starCentroids.add(center);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Star detection error", e);
        }
        return starCentroids;
    }
    private enum CelestialBodyType {
        STAR, PLANET, SUN, MOON
    }
    private static class CelestialBody {
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

