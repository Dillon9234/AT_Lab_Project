package com.example.project;

import android.content.ContentValues;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

public class CameraHelper {
    private final AppCompatActivity activity;
    private final PreviewView cameraPreview;
    private ImageCapture imageCapture;
    private static final String TAG = "CameraHelper";

    public interface CaptureCallback {
        void onImageCaptured(Uri imageUri);
        void onError(String message);
    }

    public CameraHelper(AppCompatActivity activity, PreviewView cameraPreview) {
        this.activity = activity;
        this.cameraPreview = cameraPreview;
    }

    public void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(activity);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                // Set up the preview use case
                Preview preview = new Preview.Builder().build();

                // Set up the capture use case
                imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build();

                // Choose the camera and bind use cases
                CameraSelector cameraSelector = new CameraSelector.Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                        .build();

                // Unbind any bound use cases before rebinding
                cameraProvider.unbindAll();

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(activity, cameraSelector, preview, imageCapture);

                // Connect the preview to the PreviewView
                preview.setSurfaceProvider(cameraPreview.getSurfaceProvider());

            } catch (ExecutionException | InterruptedException e) {
                Toast.makeText(activity, "Error starting camera: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(activity));
    }

    public void takePicture(CaptureCallback callback) {
        if (imageCapture == null) {
            Toast.makeText(activity, "Camera not initialized", Toast.LENGTH_SHORT).show();
            return;
        }

        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        String fileName = "IMG_" + timestamp + ".jpg";

        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { // Android 10+
            contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/MyApp");
        }

        ImageCapture.OutputFileOptions outputOptions;
        Uri outputUri = null;

        try {
            Uri contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                outputOptions = new ImageCapture.OutputFileOptions.Builder(activity.getContentResolver(), contentUri, contentValues).build();
            } else {
                File outputDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "MyApp");
                if (!outputDir.exists()) {
                    outputDir.mkdirs();
                }
                File photoFile = new File(outputDir, fileName);
                outputOptions = new ImageCapture.OutputFileOptions.Builder(photoFile).build();

                // Pre-create content URI for older Android versions
                outputUri = Uri.fromFile(photoFile);
            }

            final Uri finalOutputUri = outputUri;

            // Take the picture
            imageCapture.takePicture(
                    outputOptions,
                    ContextCompat.getMainExecutor(activity),
                    new ImageCapture.OnImageSavedCallback() {
                        @Override
                        public void onImageSaved(@NonNull ImageCapture.OutputFileResults output) {
                            Uri savedUri = output.getSavedUri();
                            if (savedUri == null && finalOutputUri != null) {
                                savedUri = finalOutputUri; // Use pre-created URI as fallback
                            }

                            if (savedUri == null) {
                                callback.onError("Image saved but URI is null");
                                return;
                            }

                            // Add URI to the media store for visibility in gallery apps
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                                activity.sendBroadcast(activity.getIntent().setAction(android.content.Intent.ACTION_MEDIA_SCANNER_SCAN_FILE).setData(savedUri));
                            }

                            // Log the URI for debugging
                            Log.d(TAG, "Image saved, URI: " + savedUri.toString());

                            callback.onImageCaptured(savedUri);
                        }

                        @Override
                        public void onError(@NonNull ImageCaptureException exception) {
                            Log.e(TAG, "Error capturing image", exception);
                            callback.onError("Error capturing image: " + exception.getMessage());
                        }
                    }
            );
        } catch (Exception e) {
            Log.e(TAG, "Error in photo capture setup", e);
            callback.onError("Error in photo capture setup: " + e.getMessage());
        }
    }

    public void release() {
        imageCapture = null;
    }
}