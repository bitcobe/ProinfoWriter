package com.example.proinfowriter;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class MainActivity extends AppCompatActivity {

    private TextView tvOutput;
    private Button btnCopy;
    private String serialNumber = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button btnRead = findViewById(R.id.btnRead);
        btnCopy = findViewById(R.id.btnCopy);
        tvOutput = findViewById(R.id.tvOutput);

        btnRead.setOnClickListener(v -> readProinfoPartition());
        
        btnCopy.setOnClickListener(v -> {
            if (!serialNumber.isEmpty()) {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("Serial Number", serialNumber);
                if (clipboard != null) {
                    clipboard.setPrimaryClip(clip);
                    Toast.makeText(this, "Serial number copied to clipboard!", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void readProinfoPartition() {
        tvOutput.setText("Reading proinfo partition...\n\n");
        btnCopy.setEnabled(false);
        serialNumber = "";

        new Thread(() -> {
            StringBuilder resultText = new StringBuilder();
            boolean isValid = false;

            try {
                Process suProcess = Runtime.getRuntime().exec("su");
                DataOutputStream os = new DataOutputStream(suProcess.getOutputStream());

                // Locate the target proinfo partition block device
                os.writeBytes("TARGET=$(find /dev/block/ -name proinfo 2>/dev/null | head -n 1)\n");
                os.writeBytes("if [ -z \"$TARGET\" ]; then TARGET=\"/dev/block/platform/mtk-msdc.0/by-name/proinfo\"; fi\n");
                
                // Read raw 20 bytes and output as base64 to avoid string corruption in shell stdout
                os.writeBytes("dd if=\"$TARGET\" bs=20 count=1 2>/dev/null | xxd -p | head -n 1\n");
                os.writeBytes("exit\n");
                os.flush();

                BufferedReader reader = new BufferedReader(new InputStreamReader(suProcess.getInputStream()));
                String hexLine = reader.readLine();
                suProcess.waitFor();

                if (hexLine != null && !hexLine.trim().isEmpty()) {
                    byte[] bytes = hexStringToByteArray(hexLine.trim());
                    String rawString = new String(bytes, StandardCharsets.UTF_8);

                    // Right-trim trailing spaces (0x20) and null bytes (0x00)
                    serialNumber = trimRight(rawString);

                    // Validation: Minimum 5 characters and alphanumeric only (A-Z, a-z, 0-9)
                    if (serialNumber.length() >= 5 && serialNumber.matches("^[a-zA-Z0-9]+$")) {
                        isValid = true;
                        resultText.append("Valid serial number found!\n\n")
                                  .append("Serial Number: ").append(serialNumber);
                    } else {
                        resultText.append("Valid serial number not found.");
                    }
                } else {
                    resultText.append("Valid serial number not found.");
                }

            } catch (Exception e) {
                resultText.append("Valid serial number not found.\nError: ").append(e.getMessage());
            }

            final boolean enableCopy = isValid;
            final String finalLog = resultText.toString();

            runOnUiThread(() -> {
                tvOutput.setText(finalLog);
                btnCopy.setEnabled(enableCopy);
            });
        }).start();
    }

    private String trimRight(String input) {
        int i = input.length() - 1;
        while (i >= 0 && (input.charAt(i) == ' ' || input.charAt(i) == '\0')) {
            i--;
        }
        return input.substring(0, i + 1);
    }

    private byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                                 + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }
}
