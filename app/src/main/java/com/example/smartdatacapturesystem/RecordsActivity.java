package com.example.smartdatacapturesystem;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.io.BufferedReader;
import java.io.File;
//import java.io.FileInputStream;
import java.io.FileReader;
import java.io.InputStreamReader;
import android.util.Log;

public class RecordsActivity extends AppCompatActivity {
    private TextView textRecords;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_records);

        textRecords = findViewById(R.id.textRecords);
        loadSavedRecords();
    }

    @SuppressLint("SetTextI18n")
    private void loadSavedRecords() {
        StringBuilder builder = new StringBuilder();

        // Read legacy data
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(openFileInput("captured_data.txt")))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append("\n");
            }
            builder.append("\n--------------------\n");
        } catch (Exception e) {
            builder.append("No legacy entries found.\n");
        }

        // Read OCR result text files (from internal 'results' folder)
        try {
            File ocrResultsDir = new File(getExternalFilesDir(null), "OCRResults");
            if (ocrResultsDir.exists() && ocrResultsDir.isDirectory()) {
                File[] ocrFiles = ocrResultsDir.listFiles((dir, name) -> name.endsWith(".txt"));
                if (ocrFiles != null && ocrFiles.length > 0) {
                    for (File file : ocrFiles) {
                        builder.append("📄 File: ").append(file.getName()).append("\n");
                        BufferedReader reader = new BufferedReader(new FileReader(file));
                        String line;
                        while ((line = reader.readLine()) != null) {
                            builder.append(line).append("\n");
                        }
                        reader.close();
                        builder.append("\n--------------------\n");
                    }
                } else {
                    builder.append("No OCRResults found.\n");
                }
            }
            else {
                builder.append("Results folder not found.\n");
            }
        } catch (Exception e) {
            Log.e("RecordsActivity", "Error reading results files", e);
            builder.append("Error reading OCR results.\n");
        }

        textRecords.setText(builder.toString());
    }

}
