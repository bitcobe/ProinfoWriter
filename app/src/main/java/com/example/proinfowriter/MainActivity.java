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
        tvOutput.setText("Pretražujem i čitam proinfo particiju...\n\n");

        new Thread(() -> {
            StringBuilder log = new StringBuilder();
            try {
                Process suProcess = Runtime.getRuntime().exec("su");
                DataOutputStream os = new DataOutputStream(suProcess.getOutputStream());

                // Traženje direktne putanje do proinfo fajla u blok uređajima
                os.writeBytes("TARGET=$(find /dev/block/ -name proinfo 2>/dev/null | head -n 1)\n");
                os.writeBytes("if [ -z \"$TARGET\" ]; then TARGET=\"/dev/block/platform/mtk-msdc.0/by-name/proinfo\"; fi\n");
                
                os.writeBytes("echo \"[+] Tražena putanja: $TARGET\"\n");

                os.writeBytes("if [ -e \"$TARGET\" ]; then\n");
                os.writeBytes("  echo \"[+] Particija pronađena! Čitam prvih 512 bajtova...\"\n");
                os.writeBytes("  echo \"------------------------------------\"\n");
                // Čitanje prvih 512 bajtova i prikaz čitljivih karaktera
                os.writeBytes("  dd if=\"$TARGET\" bs=512 count=1 2>/dev/null | strings\n");
                os.writeBytes("  echo \"------------------------------------\"\n");
                os.writeBytes("else\n");
                os.writeBytes("  echo \"[-] Particija nije pronađena na uobičajenim lokacijama.\"\n");
                os.writeBytes("  echo \"Pokušavam izlistavanje svih by-name particija:\"\n");
                os.writeBytes("  find /dev/block/ -name \"*proinfo*\" 2>/dev/null\n");
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
