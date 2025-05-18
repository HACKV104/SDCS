package com.example.smartdatacapturesystem;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkRequest;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import android.util.Log;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity {
    Button btnScanCamera, btnVoiceInput, btnUploadForm, btnViewRecords;
    TextView txtNetworkStatus;

    private static final int REQUEST_ALL_PERMISSIONS = 1;
    private ConnectivityManager.NetworkCallback networkCallback;

    private FusedLocationProviderClient fusedLocationClient;
    private String currentLocation = "unknown";
    private ActivityResultLauncher<String> galleryLauncher;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnScanCamera = findViewById(R.id.btnScanCamera);
        btnVoiceInput = findViewById(R.id.btnVoiceInput);
        btnUploadForm = findViewById(R.id.btnUploadForm);
        btnViewRecords = findViewById(R.id.btnViewRecords);
        txtNetworkStatus = findViewById(R.id.txtNetworkStatus);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        initGalleryLauncher();
        requestAllPermissions();
        registerNetworkCallback();

        btnScanCamera.setOnClickListener(v -> startActivity(new Intent(this, CameraScanActivity.class)));
        btnVoiceInput.setOnClickListener(v -> startActivity(new Intent(this, VoiceInputActivity.class)));
        btnUploadForm.setOnClickListener(v -> pickImageFromGallery());
        btnViewRecords.setOnClickListener(v -> startActivity(new Intent(this, RecordsActivity.class)));

        fetchLocation();
    }

    private void initGalleryLauncher() {
        galleryLauncher = registerForActivityResult(           // ← assign to the field
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        showTypeDialogAndUpload(uri);          // your existing helper
                    }
                });
    }

    private void pickImageFromGallery() {
        if (galleryLauncher != null) {
            galleryLauncher.launch("image/*");   // open gallery for any image
        } else {
            Toast.makeText(this, "Gallery not ready", Toast.LENGTH_SHORT).show();
        }
    }

    private void showTypeDialogAndUpload(Uri uri) {
        String[] types = getResources().getStringArray(R.array.image_types);
        new AlertDialog.Builder(this)
                .setTitle("Select Type")
                .setItems(types, (dialog, which) -> sendImageToBackend(uri, types[which]))
                .show();
    }

    private void sendImageToBackend(Uri uri, String file_type) {

        try (InputStream is = getContentResolver().openInputStream(uri);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            byte[] buf = new byte[4096];
            int len;
            while (true) {
                assert is != null;
                if ((len = is.read(buf)) == -1) break;
                baos.write(buf, 0, len);
            }

            byte[] imageData = baos.toByteArray();
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                    .format(new Date());

            String location = (currentLocation == null) ? "unknown" : currentLocation;

            RequestBody body = RequestBody.create(imageData,
                    MediaType.get("application/octet-stream"));
            BackendApi api = RetrofitClient.getInstance();

            api.uploadImage(body, timestamp, location, file_type)
                    .enqueue(new Callback<>() {

                        @Override
                        public void onResponse(@NonNull Call<ResponseBody> call,
                                               @NonNull Response<ResponseBody> response) {

                            if (response.isSuccessful() && response.body() != null) {

                                new Thread(() -> {
                                    try (InputStream in = response.body().byteStream()) {

                                        File dir = new File(getExternalFilesDir(null), "OCRResults");
                                        if (!dir.exists() && !dir.mkdirs()) {
                                            runOnUiThread(() -> Toast.makeText(MainActivity.this,
                                                    "❌ Failed to create output directory",
                                                    Toast.LENGTH_SHORT).show());
                                            return;
                                        }

                                        File outFile = new File(dir, "OCR_" + timestamp + ".txt");
                                        try (FileOutputStream fos = new FileOutputStream(outFile)) {
                                            byte[] buffer = new byte[4096];
                                            int n;
                                            while ((n = in.read(buffer)) != -1)
                                                fos.write(buffer, 0, n);
                                        }

                                        runOnUiThread(() -> Toast.makeText(MainActivity.this,
                                                "✅ File saved: " + outFile.getAbsolutePath(),
                                                Toast.LENGTH_LONG).show());

                                    } catch (Exception ex) {
                                        Log.e("MainActivity", "save error", ex);
                                        runOnUiThread(() -> Toast.makeText(MainActivity.this,
                                                "❌ Error saving file", Toast.LENGTH_SHORT).show());
                                    }
                                }).start();

                            } else {
                                Toast.makeText(MainActivity.this,
                                        "Upload failed: " + response.code(), Toast.LENGTH_SHORT).show();
                            }
                        }

                        @Override
                        public void onFailure(@NonNull Call<ResponseBody> call,
                                              @NonNull Throwable t) {
                            Log.e("MainActivity", "upload error", t);
                            Toast.makeText(MainActivity.this,
                                    "Network error", Toast.LENGTH_SHORT).show();
                        }
                    });

        } catch (Exception e) {
            Log.e("MainActivity", "send error", e);
            Toast.makeText(this, "Failed to read image", Toast.LENGTH_SHORT).show();
        }
    }

//    private void sendImageToBackend(Uri uri, String file_type) {
//        try {
//            InputStream is = getContentResolver().openInputStream(uri);
//            ByteArrayOutputStream baos = new ByteArrayOutputStream();
//            byte[] buf = new byte[4096]; int len;
//            while (true) {
//                assert is != null;
//                if ((len = is.read(buf)) == -1) break;
//                baos.write(buf,0,len);
//            }
//            is.close();
//            byte[] imageData = baos.toByteArray();
//
//            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
//                    .format(new Date());
//
//            String location = currentLocation;
//
//            RequestBody body = RequestBody.create(imageData, MediaType.get("application/octet-stream"));
//            BackendApi api = RetrofitClient.getInstance();
//            api.uploadImage(body, timestamp, location, file_type)
//                    .enqueue(new Callback<>() {          // <-- Retrofit callback
//                        @Override
//                        public void onResponse(@NonNull Call<ResponseBody> call,
//                                               @NonNull Response<ResponseBody> response) {
//                            if (response.isSuccessful()) {
//                                Toast.makeText(MainActivity.this,
//                                        "Uploaded ✅", Toast.LENGTH_SHORT).show();
//                            } else {
//                                Toast.makeText(MainActivity.this,
//                                        "Upload failed: " + response.code(),
//                                        Toast.LENGTH_SHORT).show();
//                            }
//                        }
//
//                        @Override
//                        public void onFailure(@NonNull Call<ResponseBody> call,
//                                              @NonNull Throwable t) {
//                            Log.e("MainActivity", "upload error", t);
//                            Toast.makeText(MainActivity.this,
//                                    "Network error", Toast.LENGTH_SHORT).show();
//                        }
//                    });
//        } catch (Exception e) {
//            Log.e("MainActivity","send error",e);
//            Toast.makeText(this, "Failed to read image", Toast.LENGTH_SHORT).show();
//        }
//    }

    private void fetchLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.getLastLocation().addOnSuccessListener(loc -> {
                if (loc != null) currentLocation = loc.getLatitude()+","+loc.getLongitude();
            });
        }
    }
    private void requestAllPermissions() {
        String[] permissions = {
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.ACCESS_FINE_LOCATION
        };

        boolean allGranted = true;
        for (String permission : permissions) {
            if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
                break;
            }
        }

        if (!allGranted) {
            requestPermissions(permissions, REQUEST_ALL_PERMISSIONS);
        } else {
            checkStoragePermission();
        }
    }

    private void checkStoragePermission() {
        if (!Environment.isExternalStorageManager()) {
            new AlertDialog.Builder(this)
                    .setTitle("Storage Access Required")
                    .setMessage("This app requires access to all files. Please enable it in settings.")
                    .setPositiveButton("Open Settings", (dialog, which) -> {
                        Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                        startActivity(intent);
                    })
                    .setNegativeButton("Cancel", (dialog, which) -> Toast.makeText(this, "All permissions are required for the app to work properly.", Toast.LENGTH_LONG).show())
                    .setCancelable(false)
                    .show();
        }
    }

    private void registerNetworkCallback() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkRequest request = new NetworkRequest.Builder().build();
        networkCallback = new ConnectivityManager.NetworkCallback() {
            @SuppressLint("SetTextI18n")
            @Override
            public void onAvailable(@NonNull Network network) {
                runOnUiThread(() -> {
                    txtNetworkStatus.setText("Status: Online");
                    txtNetworkStatus.setTextColor(ContextCompat.getColor(MainActivity.this, android.R.color.holo_green_dark));
                });
            }

            @SuppressLint("SetTextI18n")
            @Override
            public void onLost(@NonNull Network network) {
                runOnUiThread(() -> {
                    txtNetworkStatus.setText("Status: Offline");
                    txtNetworkStatus.setTextColor(ContextCompat.getColor(MainActivity.this, android.R.color.holo_red_dark));
                });
            }
        };

        connectivityManager.registerNetworkCallback(request, networkCallback);
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (networkCallback != null) {
            ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            connectivityManager.unregisterNetworkCallback(networkCallback);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_ALL_PERMISSIONS) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (!allGranted) {
                Toast.makeText(this, "All permissions are required for the app to work properly.", Toast.LENGTH_LONG).show();
            } else {
                checkStoragePermission();  // proceed to check storage access if other permissions are granted
            }
        }
    }
}