package com.example.proinfowriter;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class MainActivity extends AppCompatActivity {

    private TextView tvOutput;
    private Button btnCopy, btnWrite;
    private EditText etSerialInput;
    private String serialNumber = "";
    // Fiksirana primarna putanja koju terminal garantovano koristi
    private final String targetPartitionPath = "/dev/block/platform/mtk-msdc.0/by-name/proinfo";
    private String activePartitionPath = "";
    private boolean isPartitionLocated = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button btnRead = findViewById(R.id.btnRead);
        btnCopy = findViewById(R.id.btnCopy);
        btnWrite = findViewById(R.id.btnWrite);
        etSerialInput = findViewById(R.id.etSerialInput);
        tvOutput = findViewById(R.id.tvOutput);

        btnWrite.setEnabled(false);

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

        etSerialInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String input = s.toString();
                String filtered = input.replaceAll("[^a-zA-Z0-9]", "");
                if (!filtered.equals(input)) {
                    etSerialInput.setText(filtered);
                    etSerialInput.setSelection(filtered.length());
                    return;
                }

                boolean canWrite = isPartitionLocated && filtered.length() >= 5;
                btnWrite.setEnabled(canWrite);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        btnWrite.setOnClickListener(v -> confirmAndWriteSerial());
    }

    private void readProinfoPartition() {
        tvOutput.setText("Reading proinfo partition...\n\n");
        btnCopy.setEnabled(false);
        btnWrite.setEnabled(false);
        isPartitionLocated = false;
        serialNumber = "";

        new Thread(() -> {
            StringBuilder resultText = new StringBuilder();
            boolean isValid = false;
            String detectedPath = "";

            try {
                Process suProcess = Runtime.getRuntime().exec("su");
                DataOutputStream os = new DataOutputStream(suProcess.getOutputStream());

                // Proveravamo prvo tačnu mtk-msdc.0 putanju, a ako ne postoji onda tražimo fallback
                os.writeBytes("if [ -e \"" + targetPartitionPath + "\" ]; then\n");
                os.writeBytes("  echo \"PATH:" + targetPartitionPath + "\"\n");
                os.writeBytes("  dd if=\"" + targetPartitionPath + "\" bs=20 count=1 2>/dev/null | xxd -p | head -n 1\n");
                os.writeBytes("else\n");
                os.writeBytes("  TARGET=$(find /dev/block/ -name proinfo 2>/dev/null | head -n 1)\n");
                os.writeBytes("  if [ -n \"$TARGET\" ]; then\n");
                os.writeBytes("    echo \"PATH:$TARGET\"\n");
                os.writeBytes("    dd if=\"$TARGET\" bs=20 count=1 2>/dev/null | xxd -p | head -n 1\n");
                os.writeBytes("  else\n");
                os.writeBytes("    echo \"NOT_FOUND\"\n");
                os.writeBytes("  fi\n");
                os.writeBytes("fi\n");
                os.writeBytes("exit\n");
                os.flush();

                BufferedReader reader = new BufferedReader(new InputStreamReader(suProcess.getInputStream()));
                String line;
                String hexLine = null;

                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("PATH:")) {
                        detectedPath = line.substring(5).trim();
                    } else if (!line.equals("NOT_FOUND") && !line.startsWith("PATH:")) {
                        hexLine = line;
                    }
                }
                suProcess.waitFor();

                if (detectedPath.isEmpty()) {
                    resultText.append("Proinfo partition not found on device.");
                } else {
                    isPartitionLocated = true;
                    activePartitionPath = detectedPath;

                    resultText.append("Partition Path:\n").append(activePartitionPath).append("\n\n");

                    if (hexLine != null && !hexLine.trim().isEmpty()) {
                        byte[] bytes = hexStringToByteArray(hexLine.trim());
                        String rawString = new String(bytes, StandardCharsets.UTF_8);

                        serialNumber = trimRight(rawString);

                        if (serialNumber.length() >= 5 && serialNumber.matches("^[a-zA-Z0-9]+$")) {
                            isValid = true;
                            resultText.append("Valid serial number found!\n")
                                      .append("Serial Number: ").append(serialNumber);
                        } else {
                            resultText.append("Raw Hex: ").append(hexLine.trim()).append("\n")
                                      .append("Valid serial number not found.");
                        }
                    } else {
                        resultText.append("Valid serial number not found (Empty response).");
                    }
                }

            } catch (Exception e) {
                resultText.append("Error reading partition: ").append(e.getMessage());
            }

            final boolean enableCopy = isValid;
            final boolean partitionFound = isPartitionLocated;
            final String finalLog = resultText.toString();

            runOnUiThread(() -> {
                tvOutput.setText(finalLog);
                btnCopy.setEnabled(enableCopy);
                String currentInput = etSerialInput.getText().toString();
                btnWrite.setEnabled(partitionFound && currentInput.length() >= 5);
            });
        }).start();
    }

    private void confirmAndWriteSerial() {
        String inputVal = etSerialInput.getText().toString();

        if (inputVal.length() < 5 || !inputVal.substring(0, 5).matches("^[a-zA-Z0-9]+$")) {
            Toast.makeText(this, "Incorrect input! First 5 characters must be alphanumeric.", Toast.LENGTH_LONG).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Confirm Write")
                .setMessage("Are you sure you want to write to:\n" + activePartitionPath + "?")
                .setPositiveButton("Yes", (dialog, which) -> writeToPartition(inputVal))
                .setNegativeButton("No", null)
                .show();
    }

    private void writeToPartition(String inputVal) {
        StringBuilder sb = new StringBuilder(inputVal);
        while (sb.length() < 20) {
            sb.append(' ');
        }
        String paddedSerial = sb.toString();

        tvOutput.setText("Writing to partition:\n" + activePartitionPath + "...\n\n");

        new Thread(() -> {
            StringBuilder resultText = new StringBuilder();
            try {
                Process suProcess = Runtime.getRuntime().exec("su");
                DataOutputStream os = new DataOutputStream(suProcess.getOutputStream());

                // Tačna komanda iz terminala preko printf %s
                String command = "printf '%s' '" + paddedSerial + "' | dd of='" + activePartitionPath + "' bs=20 count=1 seek=0 conv=notrunc\n";

                os.writeBytes(command);
                os.writeBytes("sync\n");
                os.writeBytes("exit\n");
                os.flush();

                int exitCode = suProcess.waitFor();

                if (exitCode == 0) {
                    resultText.append("Write command executed successfully!\n\n")
                              .append("Target Path: ").append(activePartitionPath).append("\n")
                              .append("Written Value: [").append(paddedSerial).append("]\n\n")
                              .append("Click 'READ Serial' to verify.");
                } else {
                    resultText.append("Execution failed with exit code: ").append(exitCode);
                }

            } catch (Exception e) {
                resultText.append("Error writing to partition: ").append(e.getMessage());
            }

            final String finalLog = resultText.toString();
            runOnUiThread(() -> tvOutput.setText(finalLog));
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
