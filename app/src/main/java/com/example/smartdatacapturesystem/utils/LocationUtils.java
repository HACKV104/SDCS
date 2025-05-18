package com.example.smartdatacapturesystem.utils;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import androidx.core.app.ActivityCompat;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;

public class LocationUtils {
    public static void requestLocationPermission(Activity activity, int requestCode) {
        if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, requestCode);
        }
    }

    public static FusedLocationProviderClient getLocationClient(Activity activity) {
        return LocationServices.getFusedLocationProviderClient(activity);
    }

}
