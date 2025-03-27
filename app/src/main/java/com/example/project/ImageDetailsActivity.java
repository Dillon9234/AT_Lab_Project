package com.example.project;

import android.content.ContentValues;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
            progressBar.setVisibility(View.VISIBLE);
            detectButton.setEnabled(false);

            // Convert image to base64
            Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), imageUri);
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, byteArrayOutputStream);
            byte[] byteArray = byteArrayOutputStream.toByteArray();
            String encodedImage = Base64.encodeToString(byteArray, Base64.DEFAULT);

            // Get current timestamp
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            String timestamp = sdf.format(new Date());

            DetectionRequest request = new DetectionRequest(
                    quaternionX, quaternionY, quaternionZ, quaternionW,
                    latitude, longitude, timestamp, encodedImage
            );

            // Make API call using the context to get the user-configured BASE_URL
            CelestialBodyApiService apiService = ApiClient.getApiService(this);
            Call<DetectionResponse> call = apiService.detectCelestialBodies(request);

            call.enqueue(new Callback<DetectionResponse>() {
                @Override
                public void onResponse(Call<DetectionResponse> call, Response<DetectionResponse> response) {
                    progressBar.setVisibility(View.GONE);
                    detectButton.setEnabled(true);

                    if (response.isSuccessful() && response.body() != null) {
                        DetectionResponse detectionResponse = response.body();

                        if ("success".equals(detectionResponse.getStatus())) {
                            // Display result image
                            String base64Image = detectionResponse.getResultImage();
                            byte[] decodedString = Base64.decode(base64Image, Base64.DEFAULT);
                            Bitmap resultBitmap = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
                            resultImageView.setImageBitmap(resultBitmap);
                            resultImageView.setVisibility(View.VISIBLE);

                            // Save the image to gallery
                            saveImageToGallery(resultBitmap);

                            // Display detected bodies
                            if (detectionResponse.getDetectedBodies() != null && !detectionResponse.getDetectedBodies().isEmpty()) {
                                StringBuilder bodiesText = new StringBuilder("Detected Celestial Bodies:\n");
                                for (CelestialBody body : detectionResponse.getDetectedBodies()) {
                                    bodiesText.append(body.getName())
                                            .append(" (Magnitude: ")
                                            .append(body.getMagnitude())
                                            .append(")\n");
                                }
                                detectedBodiesTextView.setText(bodiesText.toString());
                                detectedBodiesTextView.setVisibility(View.VISIBLE);
                            } else {
                                detectedBodiesTextView.setText("No celestial bodies detected");
                                detectedBodiesTextView.setVisibility(View.VISIBLE);
                            }
                        } else {
                            Toast.makeText(ImageDetailsActivity.this,
                                    "Error: " + detectionResponse.getError(),
                                    Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(ImageDetailsActivity.this,
                                "Error: " + (response.errorBody() != null ? response.errorBody().toString() : "Unknown error"),
                                Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onFailure(Call<DetectionResponse> call, Throwable t) {
                    progressBar.setVisibility(View.GONE);
                    detectButton.setEnabled(true);
                    Log.e(TAG, "API call failed", t);
                    Toast.makeText(ImageDetailsActivity.this,
                            "Network error: " + t.getMessage(),
                            Toast.LENGTH_SHORT).show();
                }
            });

        } catch (Exception e) {
            progressBar.setVisibility(View.GONE);
            detectButton.setEnabled(true);
            Log.e(TAG, "Error preparing API call", e);
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}
