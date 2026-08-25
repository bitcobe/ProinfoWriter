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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;

public class MainActivity extends AppCompatActivity {

    private TextView tvReadStatus, tvWriteStatus, tvLengthDisplay;
    private Button btnCopy, btnWrite, btnGenerate, btnMinus, btnPlus;
    private EditText etSerialInput;
    private String serialNumber = "";
    private String activePartitionPath = "";
    private boolean isPartitionLocated = false;

    private int genLength = 15; // Podrazumevana dužina za generisanje (5 - 20)
    private static final String ALPHA_NUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private final SecureRandom random = new SecureRandom();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button btnRead = findViewById(R.id.btnRead);
        btnCopy = findViewById(R.id.btnCopy);
        btnWrite = findViewById(R.id.btnWrite);
        btnGenerate = findViewById(R.id.btnGenerate);
        btnMinus = findViewById(R.id.btnMinus);
        btnPlus = findViewById(R.id.btnPlus);

        etSerialInput = findViewById(R.id.etSerialInput);
        tvReadStatus = findViewById(R.id.tvReadStatus);
        tvWriteStatus = findViewById(R.id.tvWriteStatus);
        tvLengthDisplay = findViewById(R.id.tvLengthDisplay);

        btnWrite.setEnabled(false);
        tvLengthDisplay.setText(String.valueOf(genLength));

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

        // Kontrole za izmenu dužine generisanja (5 - 20)
        btnMinus.setOnClickListener(v -> {
            if (genLength > 5) {
                genLength--;
                tvLengthDisplay.setText(String.valueOf(genLength));
            }
        });

        btnPlus.setOnClickListener(v -> {
            if (genLength < 20) {
                genLength++;
                tvLengthDisplay.setText(String.valueOf(genLength));
            }
        });

        // Generisanje nasumičnog serijskog broja
        btnGenerate.setOnClickListener(v -> {
            String randomSerial = generateRandomSerial(genLength);
            etSerialInput.setText(randomSerial);
            etSerialInput.setSelection(randomSerial.length());
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

                // Dinamički prikaz broja karaktera u tvWriteStatus
                tvWriteStatus.setText("Current length: " + filtered.length() + " / 20");

                boolean canWrite = isPartitionLocated && filtered.length() >= 5;
                btnWrite.setEnabled(canWrite);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        btnWrite.setOnClickListener(v -> confirmAndWriteSerial());
    }

    private String generateRandomSerial(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int index = random.nextInt(ALPHA_NUMERIC.length());
            sb.append(ALPHA_NUMERIC.charAt(index));
        }
        return sb.toString();
    }

    private void readProinfoPartition() {
        tvReadStatus.setText("Reading proinfo partition...");
        btnCopy.setEnabled(false);
        btnWrite.setEnabled(false);
        isPartitionLocated = false;
        serialNumber = "";

        new Thread(() -> {
            StringBuilder resultText = new StringBuilder();
            boolean isValid = false;

            try {
                // 1. Pronalaženje prve putanje (kompatibilno sa Android 5.1+)
                Process findProcess = Runtime.getRuntime().exec(new String[]{"su", "-c", "find /dev/block/ -name proinfo 2>/dev/null | head -n 1"});
                BufferedReader pathReader = new BufferedReader(new InputStreamReader(findProcess.getInputStream()));
                String detectedPath = pathReader.readLine();
                findProcess.waitFor();

                if (detectedPath == null || detectedPath.trim().isEmpty()) {
                    resultText.append("Proinfo partition not found.");
                } else {
                    isPartitionLocated = true;
                    activePartitionPath = detectedPath.trim();

                    // 2. Čitanje bajtova direktno u Java stream (ne zavisi od xxd alata)
                    Process ddProcess = Runtime.getRuntime().exec(new String[]{"su", "-c", "dd if='" + activePartitionPath + "' bs=20 count=1"});
                    InputStream is = ddProcess.getInputStream();

                    byte[] buffer = new byte[20];
                    int bytesRead = 0;
                    int read;
                    while (bytesRead < 20 && (read = is.read(buffer, bytesRead, 20 - bytesRead)) != -1) {
                        bytesRead += read;
                    }
                    ddProcess.waitFor();

                    if (bytesRead > 0) {
                        String rawString = new String(buffer, 0, bytesRead, StandardCharsets.UTF_8);
                        serialNumber = trimRight(rawString);

                        if (serialNumber.length() >= 5 && serialNumber.matches("^[a-zA-Z0-9]+$")) {
                            isValid = true;
                            resultText.append("Valid serial number found!\n")
                                      .append("Serial Number: ").append(serialNumber);
                        } else {
                            resultText.append("Valid serial number not found.");
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
                tvReadStatus.setText(finalLog);
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
                .setMessage("Are you sure you want to write new serial number?")
                .setPositiveButton("Yes", (dialog, which) -> writeToPartition(inputVal))
                .setNegativeButton("No", null)
                .show();
    }

    private void writeToPartition(String inputVal) {
        byte[] buffer = new byte[20];
        Arrays.fill(buffer, (byte) 0x20);

        byte[] inputBytes = inputVal.getBytes(StandardCharsets.UTF_8);
        int copyLength = Math.min(inputBytes.length, 20);
        System.arraycopy(inputBytes, 0, buffer, 0, copyLength);

        tvWriteStatus.setText("Writing to partition...");

        new Thread(() -> {
            StringBuilder resultText = new StringBuilder();
            try {
                String ddCommand = "dd of='" + activePartitionPath + "' bs=20 count=1 seek=0\n";

                Process suProcess = Runtime.getRuntime().exec(new String[]{"su", "-c", ddCommand});
                OutputStream os = suProcess.getOutputStream();

                os.write(buffer);
                os.flush();
                os.close();

                int exitCode = suProcess.waitFor();

                Process syncProcess = Runtime.getRuntime().exec(new String[]{"su", "-c", "sync"});
                syncProcess.waitFor();

                if (exitCode == 0) {
                    resultText.append("Write command executed successfully!\n")
                              .append("Written String: [").append(new String(buffer, StandardCharsets.UTF_8)).append("]");
                } else {
                    BufferedReader errReader = new BufferedReader(new InputStreamReader(suProcess.getErrorStream()));
                    StringBuilder errLog = new StringBuilder();
                    String errLine;
                    while ((errLine = errReader.readLine()) != null) {
                        errLog.append(errLine).append("\n");
                    }
                    resultText.append("Execution failed (code ").append(exitCode).append("):\n").append(errLog);
                }

            } catch (Exception e) {
                resultText.append("Error writing to partition: ").append(e.getMessage());
            }

            final String finalLog = resultText.toString();
            runOnUiThread(() -> tvWriteStatus.setText(finalLog));
        }).start();
    }

    private String trimRight(String input) {
        int i = input.length() - 1;
        while (i >= 0 && (input.charAt(i) == ' ' || input.charAt(i) == '\0')) {
            i--;
        }
        return input.substring(0, i + 1);
    }
}
