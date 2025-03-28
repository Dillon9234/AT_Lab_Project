package com.example.project;

import android.content.ContentValues;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.project.api.ApiClient;
import com.example.project.api.CelestialBody;
import com.example.project.api.CelestialBodyApiService;
import com.example.project.api.DetectionRequest;
import com.example.project.api.DetectionResponse;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ImageDetailsActivity extends AppCompatActivity {
    private static final String TAG = "ImageDetailsActivity";
    private ImageView detailImageView;
    private TextView detailDataTextView;
    private TextView celestialCoordinatesTextView;
    private ProgressBar progressBar;
    private Button detectButton;
    private ImageView resultImageView;
    private TextView detectedBodiesTextView;
    private TextView serverUrlTextView;

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
    private PixelToCelestialConverter converter;
    private TextView starInfoTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main2);

        // Initialize views
        detailImageView = findViewById(R.id.detailImageView);
        detailDataTextView = findViewById(R.id.detailDataTextView);
        progressBar = findViewById(R.id.progressBar);
        detectButton = findViewById(R.id.detectButton);
        resultImageView = findViewById(R.id.resultImageView);
        detectedBodiesTextView = findViewById(R.id.detectedBodiesTextView);
        serverUrlTextView = findViewById(R.id.serverUrlTextView);

        // Display the current server URL
        String currentServerUrl = ApiClient.getBaseUrl(this);
        serverUrlTextView.setText("Server: " + currentServerUrl);

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

                // Set up detect button
                detectButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        detectCelestialBodies();
                    }
                });
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
            int width = originalBitmap.getWidth();
            int height = originalBitmap.getHeight();

            // Create a copy of the bitmap to draw on
            starMapBitmap = originalBitmap.copy(originalBitmap.getConfig(), true);
            Canvas canvas = new Canvas(starMapBitmap);

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

            // Estimate field of view based on device camera (this should be calibrated)
            double fovDegrees = 66.0; // Example value, should be calibrated for your device

            // Create the WCS converter
            PixelToCelestialConverter converter = new PixelToCelestialConverter(
                    width, height, centerCoords, fovDegrees);

            // Get stars that should be visible in this field of view
            List<StarDatabase.Star> visibleStars = StarDatabase.getStarsInFieldOfView(
                    centerCoords.rightAscension, centerCoords.declination,
                    fovDegrees, fovDegrees * height / width, 4.0); // Show stars up to magnitude 4

            // Build a string with star information
            StringBuilder starInfo = new StringBuilder("Potential stars in view:\n");

            // For each star, find its pixel position and mark it
            for (StarDatabase.Star star : visibleStars) {
                // Use the proper WCS transformation to get pixel coordinates
                double[] pixelCoords = converter.celestialToPixel(star.rightAscension, star.declination);

                // Check if the star is within the image bounds
                if (!Double.isNaN(pixelCoords[0]) && !Double.isNaN(pixelCoords[1]) &&
                        pixelCoords[0] >= 0 && pixelCoords[0] < width &&
                        pixelCoords[1] >= 0 && pixelCoords[1] < height) {

                    // Draw a circle around the star
                    canvas.drawCircle((float)pixelCoords[0], (float)pixelCoords[1], 20, starPaint);

                    // Draw the star name
                    canvas.drawText(star.name, (float)pixelCoords[0] - 10, (float)pixelCoords[1] - 25, textPaint);

                    // Add to info text
                    starInfo.append(star.name)
                            .append(" (Mag: ")
                            .append(String.format("%.2f", star.magnitude))
                            .append(")\n");
                }
            }

            // Display the annotated image
            resultImageView.setImageBitmap(starMapBitmap);
            resultImageView.setVisibility(View.VISIBLE);

            // Display star information
            detectedBodiesTextView.setText(starInfo.toString());
            detectedBodiesTextView.setVisibility(View.VISIBLE);
            saveImageToGallery(starMapBitmap);

        } catch (Exception e) {
            Log.e(TAG, "Error identifying stars", e);
            Toast.makeText(this, "Error identifying stars: " + e.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }
}
