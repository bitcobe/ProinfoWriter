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
        tvOutput.setText("Pokrećem root čitanje proinfo particije...\n\n");

        new Thread(() -> {
            StringBuilder log = new StringBuilder();
            try {
                Process suProcess = Runtime.getRuntime().exec("su");
                DataOutputStream os = new DataOutputStream(suProcess.getOutputStream());

                // 1. Pronalaženje tačne putanje
                os.writeBytes("PROINFO_PATH=$(ls -l /dev/block/platform/*/by-name/proinfo /dev/block/by-name/proinfo /dev/block/bootdevice/by-name/proinfo 2>/dev/null | awk '{print $NF}' | head -n 1)\n");
                os.writeBytes("echo \"[+] Putanja: $PROINFO_PATH\"\n");

                // 2. Čitanje prvih 512 bajtova i konverzija u HEX + ASCII čitljiv tekst
                os.writeBytes("if [ -n \"$PROINFO_PATH\" ]; then\n");
                os.writeBytes("  echo \"[+] Prvih 64 bajta (HEX):\"\n");
                os.writeBytes("  dd if=\"$PROINFO_PATH\" bs=64 count=1 2>/dev/null | hexdump -C || dd if=\"$PROINFO_PATH\" bs=64 count=1 2>/dev/null | od -tx1 -tc\n");
                os.writeBytes("  echo \"\n[+] Čitljivi tekstualni nizovi (prvih 20+ karaktera):\"\n");
                os.writeBytes("  dd if=\"$PROINFO_PATH\" bs=512 count=1 2>/dev/null | strings -n 3\n");
                os.writeBytes("else\n");
                os.writeBytes("  echo \"[-] Particija nije pronađena!\"\n");
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
                log.append("Greška: ").append(e.getMessage());
            }

            runOnUiThread(() -> tvOutput.setText(log.toString()));
        }).start();
    }
}
