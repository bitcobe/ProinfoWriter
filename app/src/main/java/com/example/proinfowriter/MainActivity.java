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
                
                // Pronalaženje putanje proinfo particije i čitanje prvih 512 bajtova (HEX i ASCII prikaz)
                os.writeBytes("PROINFO_PATH=$(ls -l /dev/block/platform/*/by-name/proinfo /dev/block/by-name/proinfo 2>/dev/null | awk '{print $NF}')\n");
                os.writeBytes("if [ -z \"$PROINFO_PATH\" ]; then PROINFO_PATH=\"/dev/block/bootdevice/by-name/proinfo\"; fi\n");
                os.writeBytes("echo \"Putanja particije: $PROINFO_PATH\"\n");
                os.writeBytes("dd if=\"$PROINFO_PATH\" bs=512 count=1 2>/dev/null | xxd\n");
                os.writeBytes("exit\n");
                os.flush();

                BufferedReader reader = new BufferedReader(new InputStreamReader(suProcess.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    log.append(line).append("\n");
                }
                
                suProcess.waitFor();
            } catch (Exception e) {
                log.append("Greška prilikom izvršavanja root komande: ").append(e.getMessage());
            }

            runOnUiThread(() -> tvOutput.setText(log.toString()));
        }).start();
    }
}
