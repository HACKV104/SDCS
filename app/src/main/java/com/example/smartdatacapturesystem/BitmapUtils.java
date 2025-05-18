package com.example.smartdatacapturesystem;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.Image;
import androidx.camera.core.ImageProxy;
import java.nio.ByteBuffer;


public class BitmapUtils {
    public static Bitmap imageProxyToBitmap(ImageProxy imageProxy) {
        @SuppressLint("UnsafeOptInUsageError")
        Image image = imageProxy.getImage();
        if (image == null) return null;
        Image.Plane[] planes = image.getPlanes();
        ByteBuffer buffer = planes[0].getBuffer();
        byte[] bytes = new byte[buffer.capacity()];
        buffer.get(bytes);
        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        if (bitmap == null) return null;

        // Apply rotation using metadata from ImageProxy
        int rotationDegrees = imageProxy.getImageInfo().getRotationDegrees();
        Matrix matrix = new Matrix();
        matrix.postRotate(rotationDegrees); // Rotate based on orientation
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }
}
//public class BitmapUtils {
//    public static Bitmap imageProxyToBitmap(ImageProxy imageProxy) {
//        @SuppressLint("UnsafeOptInUsageError")
//        Image image = imageProxy.getImage();
//        if (image == null) return null;
//        Image.Plane[] planes = image.getPlanes();
//        ByteBuffer buffer = planes[0].getBuffer();
//        byte[] bytes = new byte[buffer.capacity()];
//        buffer.get(bytes);
//        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
//    }
//}
