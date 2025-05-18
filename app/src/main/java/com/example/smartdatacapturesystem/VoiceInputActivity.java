package com.example.smartdatacapturesystem;

import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONObject;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.Scanner;
import android.util.Log;

public class VoiceInputActivity extends AppCompatActivity {
    private TextView voiceOutput;
    private SpeechRecognizer speechRecognizer;
    private String recognizedText = "";

    private boolean isListening = false;
    private Button btnStartVoice;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_voice_input);
        voiceOutput = findViewById(R.id.voiceOutput);
        btnStartVoice = findViewById(R.id.btnStartVoice);
        Button btnSaveVoice = findViewById(R.id.btnSaveVoice);

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);

        btnStartVoice.setOnClickListener(view -> {
            if (!isListening) {
                startListening();
            } else {
                stopListening();
            }
        });

        btnSaveVoice.setOnClickListener(view -> {
            if (!recognizedText.isEmpty()) {
                saveVoiceData(recognizedText);
                sendVoiceToBackend(recognizedText);
                validateVoiceData(recognizedText);
            } else {
                Toast.makeText(this, "Nothing to save.", Toast.LENGTH_SHORT).show();
            }
        });
}

    private void startListening() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US");
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) {}
            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEndOfSpeech() {}
            @Override public void onError(int error) {
                Toast.makeText(VoiceInputActivity.this, "Recognition error", Toast.LENGTH_SHORT).show();
                resetListeningState();
            }

            @Override
            public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    recognizedText = matches.get(0);
                    voiceOutput.setText(recognizedText);
                }
                resetListeningState();
            }

            @Override public void onPartialResults(Bundle partialResults) {}
            @Override public void onEvent(int eventType, Bundle params) {}
        });

        speechRecognizer.startListening(intent);
        isListening = true;
        btnStartVoice.setText("🛑 Stop Listening");
    }

    private void stopListening() {
        speechRecognizer.stopListening();
        isListening = false;
        btnStartVoice.setText("🎤 Start Listening");
    }

    private void resetListeningState() {
        isListening = false;
        runOnUiThread(() -> btnStartVoice.setText("🎤 Start Listening"));
    }

        private void saveVoiceData(String text) {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
        String entry = "Captured At: " + timestamp + "\nSource: Voice\nData: " + text + "\n-------------------------\n";
        try {
            FileOutputStream fos = openFileOutput("captured_data.txt", MODE_APPEND);
            fos.write(entry.getBytes());
            fos.close();
            Toast.makeText(this, "Voice Data saved locally", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e("CameraScanActivity", "Error message here", e);
        }
    }

    private void sendVoiceToBackend(String text) {
        new Thread(() -> {
            try {
                URL url = new URL("http://<your-ip>:5000/upload");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json");
                JSONObject json = new JSONObject();
                json.put("timestamp", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()));
                json.put("location", "Voice input (no GPS)");
                json.put("data", text);
                OutputStream os = conn.getOutputStream();
                os.write(json.toString().getBytes());
                os.close();
                conn.getResponseCode();
            } catch (Exception e) {
                Log.e("CameraScanActivity", "Error message here", e);
            }
        }).start();
    }

    private void validateVoiceData(String text) {
        new Thread(() -> {
            try {
                URL url = new URL("http://<your-ip>:5000/validate");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json");
                JSONObject json = new JSONObject();
                json.put("data", text);
                OutputStream os = conn.getOutputStream();
                os.write(json.toString().getBytes());
                os.close();

                Scanner scanner = new Scanner(conn.getInputStream());
                StringBuilder response = new StringBuilder();
                while (scanner.hasNext()) {
                    response.append(scanner.nextLine());
                }
                scanner.close();

                JSONObject result = new JSONObject(response.toString());
                boolean isValid = result.getBoolean("valid");

                runOnUiThread(() -> {
                    if (isValid) {
                        Toast.makeText(this, "✅ Voice data is valid", Toast.LENGTH_LONG).show();
                    } else {
                        StringBuilder issues = new StringBuilder("⚠️ Issues Detected:\n");
                        try {
                            for (int i = 0; i < result.getJSONArray("anomalies").length(); i++) {
                                issues.append("• ").append(result.getJSONArray("anomalies").getString(i)).append("\n");
                            }
                        } catch (Exception e) {
                            Log.e("CameraScanActivity", "Error message here", e);
                            issues.append("⚠ Error reading validation result");
                        }

                        new android.app.AlertDialog.Builder(this)
                                .setTitle("Validation Result")
                                .setMessage(issues.toString())
                                .setPositiveButton("OK", null)
                                .show();
                    }
                });

            } catch (Exception e) {
                Log.e("CameraScanActivity", "Error message here", e);
            }
        }).start();
    }
}
