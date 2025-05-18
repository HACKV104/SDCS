package com.example.smartdatacapturesystem;

import android.Manifest;
import android.content.pm.PackageManager;
//import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.core.resolutionselector.AspectRatioStrategy;
import androidx.camera.core.resolutionselector.ResolutionSelector;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.graphics.Bitmap;
import android.media.Image;
import android.location.Location;
import android.os.Looper;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.content.Context;
import android.util.Log;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
//import java.io.OutputStream;
//import java.net.HttpURLConnection;
//import java.net.URL;
//import java.net.URLEncoder;
//import java.nio.charset.StandardCharsets;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class CameraScanActivity extends AppCompatActivity {

    private PreviewView previewView;
    private TextView textOutput;
    private ImageCapture imageCapture;
    private ExecutorService cameraExecutor;

    private FusedLocationProviderClient fusedLocationClient;
    private String currentLocation = "Unavailable";
    private String currentTimestamp = "";
    private ScrollView  scrollResult;
    private Spinner typeSpinner;
    private String selectedType = "Docs";

//    private static final int REQUEST_LOCATION_PERMISSION = 100;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera_scan);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this,
                    "Camera permission not granted. Please restart the app and allow permissions.",
                    Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        scrollResult = findViewById(R.id.scrollResult); // fixed: assign to field
        previewView = findViewById(R.id.previewView);
        textOutput = findViewById(R.id.textOutput);
        Button btnCapture = findViewById(R.id.btnCapture);

        cameraExecutor = Executors.newSingleThreadExecutor();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        startCamera();
        fetchLocation();

        btnCapture.setOnClickListener(v -> captureAndRecognizeText());

        typeSpinner = findViewById(R.id.typeSpinner); // Add Spinner in your layout

        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                this,
                R.array.image_types,
                R.layout.spinner_item // Use custom layout
        );
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item); // customize drop-down too

        typeSpinner.setAdapter(adapter);
        typeSpinner.setAdapter(adapter);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        typeSpinner.setAdapter(adapter);
        typeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedType = parent.getItemAtPosition(position).toString();
                Log.d("CameraScanActivity", "Selected Type: " + selectedType);
// Or for visual confirmation:
                Toast.makeText(CameraScanActivity.this, "Selected: " + selectedType, Toast.LENGTH_SHORT).show();
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                selectedType = "Docs";
            }
        });
        }

private void fetchLocation() {
    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
        currentLocation = "Permission not granted";
        return;
    }

    // Try to get last known location first
    fusedLocationClient.getLastLocation()
            .addOnSuccessListener(location -> {
                if (location != null) {
                    currentLocation = location.getLatitude() + ", " + location.getLongitude();
                } else {
                    // If null, request a fresh location
                    requestFreshLocation();
                }
            })
            .addOnFailureListener(e -> currentLocation = "Location fetch error");
}

    private void requestFreshLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            currentLocation = "Permission not granted";
            return;
        }

        LocationRequest locationRequest = new LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY, 1000L)
                .setWaitForAccurateLocation(true)
                .setMinUpdateIntervalMillis(500)
                .setMaxUpdateAgeMillis(5000)
                .setMaxUpdates(1)
                .build();

        fusedLocationClient.requestLocationUpdates(locationRequest, new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                Location location = locationResult.getLastLocation();
                if (location != null) {
                    currentLocation = location.getLatitude() + ", " + location.getLongitude();
                } else {
                    currentLocation = "Fresh location not available";
                }
            }
        }, Looper.getMainLooper());
    }

    private boolean isOnline() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            NetworkCapabilities capabilities = cm.getNetworkCapabilities(cm.getActiveNetwork());
            return capabilities != null && (
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            );
        }
        return false;
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                // Define ResolutionSelector with 4:3 fallback
                ResolutionSelector resolutionSelector = new ResolutionSelector.Builder()
                        .setAspectRatioStrategy(
                                new AspectRatioStrategy(
                                        AspectRatio.RATIO_16_9,
                                        AspectRatioStrategy.FALLBACK_RULE_AUTO
                                )
                        )
                        .build();

                Preview preview = new Preview.Builder()
                        .setResolutionSelector(resolutionSelector)
                        .build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                imageCapture = new ImageCapture.Builder()
                        .setResolutionSelector(resolutionSelector)
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build();

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageCapture);

            } catch (ExecutionException | InterruptedException e) {
                Log.e("CameraScanActivity", "Error starting camera.", e);
                Toast.makeText(this, "Error starting camera.", Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

//    private void startCamera() {
//        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
//                ProcessCameraProvider.getInstance(this);
//
//        cameraProviderFuture.addListener(() -> {
//            try {
//                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
//
//                Preview preview = new Preview.Builder().build();
//                preview.setSurfaceProvider(previewView.getSurfaceProvider());
//
//                imageCapture = new ImageCapture.Builder().build();
//
//                cameraProvider.unbindAll();
//                cameraProvider.bindToLifecycle(this,
//                        CameraSelector.DEFAULT_BACK_CAMERA,
//                        preview,
//                        imageCapture);
//
//            } catch (ExecutionException | InterruptedException e) {
//                Log.e("CameraScanActivity", "Error message here", e);
//                Toast.makeText(this, "Error starting camera.", Toast.LENGTH_SHORT).show();
//            }
//        }, ContextCompat.getMainExecutor(this));
//    }

    private void captureAndRecognizeText() {
        if (imageCapture == null) {
            Toast.makeText(this, "Camera not ready", Toast.LENGTH_SHORT).show();
            return;
        }
        imageCapture.takePicture(ContextCompat.getMainExecutor(this), new ImageCapture.OnImageCapturedCallback() {
            @OptIn(markerClass = ExperimentalGetImage.class)
            @Override
            public void onCaptureSuccess(@NonNull ImageProxy imageProxy) {
                currentTimestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());

                if (isOnline()) {
                    textOutput.setVisibility(View.GONE);
                    scrollResult.setVisibility(View.GONE);
                    findViewById(R.id.scrollResult).setVisibility(View.GONE); // <-- hide the box
                    sendImageToBackend(imageProxy);
                    imageProxy.close();
                } else {
                    textOutput.setVisibility(View.VISIBLE);
                    scrollResult.setVisibility(View.VISIBLE);
                    findViewById(R.id.scrollResult).setVisibility(View.VISIBLE); // <-- show the box

                    try {
                        InputImage image = InputImage.fromMediaImage(
                                Objects.requireNonNull(imageProxy.getImage()),
                                imageProxy.getImageInfo().getRotationDegrees()
                        );

                        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                                .process(image)
                                .addOnSuccessListener(visionText -> {
                                    String recognizedText = visionText.getText();
                                    textOutput.setText(recognizedText);
                                    saveDataLocally(recognizedText);
                                    imageProxy.close();
                                })
                                .addOnFailureListener(e -> {
                                    Toast.makeText(CameraScanActivity.this, "Text recognition failed", Toast.LENGTH_SHORT).show();
                                    imageProxy.close();
                                });
                    } catch (Exception e) {
                        Log.e("CameraScanActivity", "Error processing image", e);
                        imageProxy.close();
                    }
                }
            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                Toast.makeText(CameraScanActivity.this, "Image capture failed: " + exception.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void sendImageToBackend(ImageProxy imageProxy) {
        @OptIn(markerClass = ExperimentalGetImage.class)
        Image image = imageProxy.getImage();
        if (image == null) return;
        Bitmap bitmap = BitmapUtils.imageProxyToBitmap(imageProxy);
        if (bitmap == null) {
            runOnUiThread(() -> Toast.makeText(this, "Failed to convert image", Toast.LENGTH_SHORT).show());
            return;
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, baos);
        byte[] imageData = baos.toByteArray();

        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String location = currentLocation;
        String file_type = selectedType; // Assuming selectedType is declared in your activity

        RequestBody requestBody = RequestBody.create(imageData, MediaType.get("application/octet-stream"));

        BackendApi api = RetrofitClient.getInstance();

        api.uploadImage(requestBody, timestamp, location, file_type).enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if (response.isSuccessful() && response.body() != null) {
                    new Thread(() -> {
                        try {
                            InputStream inputStream = response.body().byteStream();
                            File outputDir = new File(getExternalFilesDir(null), "OCRResults");
                            if (!outputDir.exists()) {
                                boolean dirCreated = outputDir.mkdirs();
                                if (!dirCreated) {
                                    Log.e("CameraScanActivity", "Failed to create directory: " + outputDir.getAbsolutePath());
                                    runOnUiThread(() -> Toast.makeText(CameraScanActivity.this, "❌ Failed to create output directory", Toast.LENGTH_SHORT).show());
                                    return;
                                }
                            }
                            String filename = "OCR_" + timestamp + ".txt";
                            File outFile = new File(outputDir, filename);
                            FileOutputStream fos = new FileOutputStream(outFile);

                            byte[] buffer = new byte[4096];
                            int len;
                            while ((len = inputStream.read(buffer)) != -1) {
                                fos.write(buffer, 0, len);
                            }
                            fos.close();
                            inputStream.close();

                            runOnUiThread(() ->
                                    Toast.makeText(CameraScanActivity.this, "✅ File saved: " + outFile.getAbsolutePath(), Toast.LENGTH_LONG).show()
                            );

                        } catch (Exception e) {
                            Log.e("CameraScanActivity", "Error saving file", e);
                            runOnUiThread(() -> Toast.makeText(CameraScanActivity.this, "❌ Error saving file", Toast.LENGTH_SHORT).show());
                        }
                    }).start();
                } else {
                    runOnUiThread(() ->
                            Toast.makeText(CameraScanActivity.this, "⚠️ Failed to receive file: " + response.code(), Toast.LENGTH_SHORT).show()
                    );
                }
            }


            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                Log.e("CameraScanActivity", "Error sending image", t);
                runOnUiThread(() -> Toast.makeText(CameraScanActivity.this, "❌ Network error", Toast.LENGTH_SHORT).show());
            }
        });
    }
//    private void sendImageToBackend(ImageProxy imageProxy) {
//        @OptIn(markerClass = ExperimentalGetImage.class)
//        Image image = imageProxy.getImage();
//        if (image == null) return;
//        Bitmap bitmap = BitmapUtils.imageProxyToBitmap(imageProxy);
//        if (bitmap == null) {
//            runOnUiThread(() -> Toast.makeText(this, "Failed to convert image", Toast.LENGTH_SHORT).show());
//            return;
//        }
//
//        ByteArrayOutputStream baos = new ByteArrayOutputStream();
//        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, baos);
//        byte[] imageData = baos.toByteArray();
//
//        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
//        String location = currentLocation;
//
//        RequestBody requestBody = RequestBody.create(imageData, MediaType.get("application/octet-stream"));
//
//        BackendApi api = RetrofitClient.getInstance();
//
//        api.uploadImage(requestBody, timestamp, location).enqueue(new Callback<>() {
//            @Override
//            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
//                runOnUiThread(() -> {
//                    if (response.isSuccessful()) {
//                        Toast.makeText(CameraScanActivity.this, "✅ Image sent to backend", Toast.LENGTH_SHORT).show();
//                    } else {
//                        Toast.makeText(CameraScanActivity.this, "⚠️ Failed to send image: " + response.code(), Toast.LENGTH_SHORT).show();
//                    }
//                });
//            }
//
//            @Override
//            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
//                Log.e("CameraScanActivity", "Error sending image", t);
//                runOnUiThread(() -> Toast.makeText(CameraScanActivity.this, "❌ Network error", Toast.LENGTH_SHORT).show());
//            }
//        });
//    }

//    private void sendImageToBackend(ImageProxy imageProxy) {
//        @OptIn(markerClass = ExperimentalGetImage.class)
//        Image image = imageProxy.getImage();
//        if (image == null) return;
//
//        Bitmap bitmap = BitmapUtils.imageProxyToBitmap(imageProxy);
//        if (bitmap == null) {
//            runOnUiThread(() -> Toast.makeText(this, "Failed to convert image", Toast.LENGTH_SHORT).show());
//            return;
//        }
//
//        ByteArrayOutputStream baos = new ByteArrayOutputStream();
//        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, baos);
//        byte[] imageData = baos.toByteArray();
//
//        new Thread(() -> {
//            try {
//                String serverIp = getString(R.string.server_ip);
//                String backendUrl = "http://" + serverIp + ":5000";
//
//                // 1. Ping check
//                URL testUrl = new URL(backendUrl + "/ping");
//                HttpURLConnection testConn = (HttpURLConnection) testUrl.openConnection();
//                testConn.setConnectTimeout(2000);
//                testConn.connect();
//
//                if (testConn.getResponseCode() != 200) {
//                    runOnUiThread(() -> Toast.makeText(this, "Backend not responding", Toast.LENGTH_SHORT).show());
//                    return;
//                }
//
//                // 2. Add timestamp and location to URL
//                String ts = null;
//                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
//                    ts = URLEncoder.encode(currentTimestamp, StandardCharsets.UTF_8);
//                }
//                String loc = null;
//                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
//                    loc = URLEncoder.encode(currentLocation, StandardCharsets.UTF_8);
//                }
//                URL url = new URL(backendUrl + "/uploadImage?ts=" + ts + "&loc=" + loc);
//
//                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
//                conn.setRequestMethod("POST");
//                conn.setDoOutput(true);
//                conn.setConnectTimeout(5000);
//                conn.setReadTimeout(5000);
//                conn.setRequestProperty("Content-Type", "application/octet-stream");
//
//                try (OutputStream os = conn.getOutputStream()) {
//                    os.write(imageData);
//                    os.flush();
//                }
//
//                int responseCode = conn.getResponseCode();
//                runOnUiThread(() -> {
//                    if (responseCode == 200) {
//                        Toast.makeText(this, "✅ Image sent to backend", Toast.LENGTH_SHORT).show();
//                    } else {
//                        Toast.makeText(this, "⚠️ Failed to send image: " + responseCode, Toast.LENGTH_SHORT).show();
//                    }
//                });
//
//            } catch (Exception e) {
//                Log.e("CameraScanActivity", "Image send error", e);
//                runOnUiThread(() -> Toast.makeText(this, "❌ Error connecting to backend", Toast.LENGTH_SHORT).show());
//            }
//        }).start();
//    }


    private void saveDataLocally(String text) {
        try {
            String data = "Captured At: " + currentTimestamp +
                    "\nLocation: " + currentLocation +
                    "\n\nData:\n" + text +
                    "\n------------------------\n";

            FileOutputStream fos = openFileOutput("captured_data.txt", MODE_APPEND);
            fos.write(data.getBytes());
            fos.close();
            Toast.makeText(this, "Data saved locally", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e("CameraScanActivity", "Error message here", e);
            Toast.makeText(this, "Failed to save data locally", Toast.LENGTH_SHORT).show();
        }
    }
//    private void sendDataToBackend(Bitmap bitmap) {
//        new Thread(() -> {
//            try {
//// Convert Bitmap to Base64
//                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
//                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream);
//                byte[] imageBytes = outputStream.toByteArray();
//                String base64Image = Base64.encodeToString(imageBytes, Base64.DEFAULT);
//                URL url = new URL("http://<your-ip>:5000/uploadImage"); // Replace <your-ip> and endpoint
//                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
//                conn.setRequestMethod("POST");
//                conn.setDoOutput(true);
//                conn.setRequestProperty("Content-Type", "application/json");
//
//                JSONObject json = new JSONObject();
//                json.put("timestamp", currentTimestamp);
//                json.put("location", currentLocation);
//                json.put("image_data", base64Image);
//
//                OutputStream os = conn.getOutputStream();
//                os.write(json.toString().getBytes());
//                os.flush();
//                os.close();
//
//                int responseCode = conn.getResponseCode();
//                runOnUiThread(() -> {
//                    if (responseCode == 200) {
//                        Toast.makeText(CameraScanActivity.this, "✅ Image sent to backend", Toast.LENGTH_SHORT).show();
//                    } else {
//                        Toast.makeText(CameraScanActivity.this, "⚠️ Backend error: " + responseCode, Toast.LENGTH_SHORT).show();
//                    }
//                });
//
//                conn.disconnect();
//            } catch (Exception e) {
//                Log.e("CameraScanActivity", "Image send failed", e);
//                runOnUiThread(() -> Toast.makeText(CameraScanActivity.this, "Failed to send image", Toast.LENGTH_SHORT).show());
//            }
//        }).start();
//    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
    }
}
