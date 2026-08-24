package com.example.proinfowriter;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;

public class MainActivity extends AppCompatActivity {

    private TextView tvOutput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button btnRead = findViewById(R.id.btnRead);
        tvOutput = findViewById(R.id.tvOutput);

        btnRead.setOnClickListener(v -> readProinfoPartition());
    }

    private void readProinfoPartition() {
        tvOutput.setText("Searching and reading proinfo partition...\n\n");

        new Thread(() -> {
            StringBuilder log = new StringBuilder();
            try {
                Process suProcess = Runtime.getRuntime().exec("su");
                DataOutputStream os = new DataOutputStream(suProcess.getOutputStream());

                os.writeBytes("TARGET=$(find /dev/block/ -name proinfo 2>/dev/null | head -n 1)\n");
                os.writeBytes("if [ -z \"$TARGET\" ]; then TARGET=\"/dev/block/platform/mtk-msdc.0/by-name/proinfo\"; fi\n");
                
                os.writeBytes("echo \"[+] Partition Path: $TARGET\"\n");

                os.writeBytes("if [ -e \"$TARGET\" ]; then\n");
                os.writeBytes("  echo \"[+] First 20 bytes:\n\"\n");
                // Read exactly 20 bytes and print printable characters
                os.writeBytes("  dd if=\"$TARGET\" bs=20 count=1 2>/dev/null | strings\n");
                os.writeBytes("else\n");
                os.writeBytes("  echo \"[-] Partition not found.\"\n");
                os.writeBytes("fi\n");

                os.writeBytes("exit\n");
                os.flush();

                BufferedReader reader = new BufferedReader(new InputStreamReader(suProcess.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    log.append(line).append("\n");
                }

                suProcess.waitFor();
            } catch (Exception e) {
                log.append("Error: ").append(e.getMessage());
            }

            runOnUiThread(() -> tvOutput.setText(log.toString()));
        }).start();
    }
}
