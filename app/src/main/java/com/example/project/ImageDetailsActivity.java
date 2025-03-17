package com.example.project;

import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;

public class ImageDetailsActivity extends AppCompatActivity {
    private static final String TAG = "ImageDetailsActivity";
    private ImageView detailImageView;
    private TextView detailDataTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main2);

        // Initialize views
        detailImageView = findViewById(R.id.detailImageView);
        detailDataTextView = findViewById(R.id.detailDataTextView);

        try {
            // Get data from intent
            if (getIntent() != null && getIntent().getExtras() != null) {
                // Use getParcelableExtra with type token for Android 13+ compatibility
                Uri imageUri;
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    imageUri = getIntent().getParcelableExtra("image_uri", Uri.class);
                } else {
                    imageUri = getIntent().getParcelableExtra("image_uri");
                }

                double latitude = getIntent().getDoubleExtra("latitude", 0.0);
                double longitude = getIntent().getDoubleExtra("longitude", 0.0);
                double altitude = getIntent().getDoubleExtra("altitude", 0.0);
                float roll = getIntent().getFloatExtra("roll", 0.0f);
                float pitch = getIntent().getFloatExtra("pitch", 0.0f);
                float yaw = getIntent().getFloatExtra("yaw", 0.0f);

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

                // Display sensor data
                String data = String.format(
                        "Orientation:\nRoll: %.2f°\nPitch: %.2f°\nYaw: %.2f°\n\n" +
                                "GPS Location:\nLatitude: %.6f\nLongitude: %.6f\nAltitude: %.2f m",
                        roll, pitch, yaw, latitude, longitude, altitude
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
}