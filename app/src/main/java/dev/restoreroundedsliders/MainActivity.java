package dev.restoreroundedsliders;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public final class MainActivity extends Activity {

    private static final String PREFIX = "restoreroundedsliders_";

    private View rootContainer;
    private View rootGate;
    private View settingsContent;
    private TextView rootStatus;
    private Button rootRetry;

    private SeekBar brightnessRoundness;
    private Switch brightnessGrabber;
    private TextView brightnessRoundnessValue;

    private SeekBar volumeRoundness;
    private Switch volumeGrabber;
    private TextView volumeRoundnessValue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(getColor(R.color.bg));
        getWindow().setNavigationBarColor(getColor(R.color.bg));

        setContentView(R.layout.activity_main);
        bindViews();
        applySystemBarInsets();
        configureSeekBars();
        configureSwitches();

        findViewById(R.id.button_apply).setOnClickListener(v -> applySettings());
        findViewById(R.id.button_defaults).setOnClickListener(v -> restoreDefaults());
        findViewById(R.id.button_restart_systemui).setOnClickListener(v -> restartSystemUi());
        rootRetry.setOnClickListener(v -> requestRootAccess());

        requestRootAccess();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // KernelSU commonly grants root from its manager rather than via popup.
        // Re-check when returning to the app so the UI unlocks immediately.
        if (rootGate != null && rootGate.getVisibility() == View.VISIBLE) {
            requestRootAccess();
        }
    }

    private void bindViews() {
        rootContainer = findViewById(R.id.root_container);
        rootGate = findViewById(R.id.root_gate);
        settingsContent = findViewById(R.id.settings_content);
        rootStatus = findViewById(R.id.root_status);
        rootRetry = findViewById(R.id.button_retry_root);

        brightnessRoundness = findViewById(R.id.brightness_roundness);
        brightnessGrabber = findViewById(R.id.brightness_grabber);
        brightnessRoundnessValue = findViewById(R.id.brightness_roundness_value);

        volumeRoundness = findViewById(R.id.volume_roundness);
        volumeGrabber = findViewById(R.id.volume_grabber);
        volumeRoundnessValue = findViewById(R.id.volume_roundness_value);
    }

    private void applySystemBarInsets() {
        rootContainer.setOnApplyWindowInsetsListener((view, insets) -> {
            android.graphics.Insets bars =
                    insets.getInsets(WindowInsets.Type.systemBars());

            view.setPadding(
                    bars.left,
                    bars.top,
                    bars.right,
                    bars.bottom
            );

            return insets;
        });

        rootContainer.requestApplyInsets();
    }

    private void configureSeekBars() {
        setupPercentSeekBar(brightnessRoundness, brightnessRoundnessValue);
        setupPercentSeekBar(volumeRoundness, volumeRoundnessValue);
    }

    private void setupPercentSeekBar(SeekBar seekBar, TextView valueView) {
        seekBar.setMax(100);
        seekBar.setProgressTintList(
                android.content.res.ColorStateList.valueOf(getColor(R.color.accent)));
        seekBar.setThumbTintList(
                android.content.res.ColorStateList.valueOf(getColor(R.color.accent)));

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                valueView.setText(progress + "%");
            }

            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });
    }


    private void configureSwitches() {
        int accent = getColor(R.color.accent);
        int offThumb = getColor(R.color.text_secondary);
        int offTrack = getColor(R.color.divider);

        android.content.res.ColorStateList thumbColors =
                new android.content.res.ColorStateList(
                        new int[][] {
                                new int[] { android.R.attr.state_checked },
                                new int[] {}
                        },
                        new int[] { accent, offThumb }
                );

        android.content.res.ColorStateList trackColors =
                new android.content.res.ColorStateList(
                        new int[][] {
                                new int[] { android.R.attr.state_checked },
                                new int[] {}
                        },
                        new int[] { Color.argb(120, 175, 200, 246), offTrack }
                );

        brightnessGrabber.setThumbTintList(thumbColors);
        brightnessGrabber.setTrackTintList(trackColors);
        volumeGrabber.setThumbTintList(thumbColors);
        volumeGrabber.setTrackTintList(trackColors);
    }

    private void requestRootAccess() {
        rootGate.setVisibility(View.VISIBLE);
        settingsContent.setVisibility(View.GONE);
        rootRetry.setEnabled(false);
        rootStatus.setText("This app needs root access to work");

        new Thread(() -> {
            boolean granted = false;

            try {
                Process process = new ProcessBuilder("su", "-c", "id")
                        .redirectErrorStream(true)
                        .start();

                StringBuilder output = new StringBuilder();
                try (BufferedReader reader =
                             new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append('\n');
                    }
                }

                int exit = process.waitFor();
                granted = exit == 0 && output.toString().contains("uid=0");
            } catch (Throwable ignored) {
                granted = false;
            }

            final boolean rootGranted = granted;

            runOnUiThread(() -> {
                rootRetry.setEnabled(true);

                if (rootGranted) {
                    rootGate.setVisibility(View.GONE);
                    settingsContent.setVisibility(View.VISIBLE);
                    loadSettings();
                } else {
                    settingsContent.setVisibility(View.GONE);
                    rootGate.setVisibility(View.VISIBLE);
                    rootStatus.setText("This app needs root access to work");
                }
            });
        }).start();
    }

    private void setSeekValue(SeekBar seekBar, TextView valueView, int value) {
        int clamped = Math.max(0, Math.min(100, value));
        seekBar.setProgress(clamped);
        valueView.setText(clamped + "%");
    }

    private void loadSettings() {
        setSeekValue(
                brightnessRoundness,
                brightnessRoundnessValue,
                readGlobal(PREFIX + "brightness_roundness", 100)
        );

        brightnessGrabber.setChecked(
                readGlobal(PREFIX + "brightness_grabber", 0) != 0
        );

        setSeekValue(
                volumeRoundness,
                volumeRoundnessValue,
                readGlobal(PREFIX + "volume_roundness", 100)
        );

        volumeGrabber.setChecked(
                readGlobal(PREFIX + "volume_grabber", 0) != 0
        );
    }

    private int readGlobal(String key, int defaultValue) {
        try {
            return Settings.Global.getInt(getContentResolver(), key, defaultValue);
        } catch (Throwable ignored) {
            return defaultValue;
        }
    }

    private void applySettings() {
        int br = brightnessRoundness.getProgress();
        int bg = brightnessGrabber.isChecked() ? 1 : 0;
        int vr = volumeRoundness.getProgress();
        int vg = volumeGrabber.isChecked() ? 1 : 0;

        String command =
                // Explicitly reset legacy thickness values; the hook also hard-locks 100%.
                "settings put global " + PREFIX + "brightness_thickness 100; " +
                "settings put global " + PREFIX + "volume_thickness 100; " +
                "settings put global " + PREFIX + "brightness_roundness " + br + "; " +
                "settings put global " + PREFIX + "brightness_grabber " + bg + "; " +
                "settings put global " + PREFIX + "volume_roundness " + vr + "; " +
                "settings put global " + PREFIX + "volume_grabber " + vg + "; " +
                // A fresh SystemUI process is required to reliably restore a
                // previously suppressed Compose grabber overlay.
                "killall com.android.systemui";

        runRootCommand(command, true);
    }

    private void restoreDefaults() {
        setSeekValue(brightnessRoundness, brightnessRoundnessValue, 100);
        brightnessGrabber.setChecked(false);

        setSeekValue(volumeRoundness, volumeRoundnessValue, 100);
        volumeGrabber.setChecked(false);

        Toast.makeText(
                this,
                "Defaults loaded. Tap Apply to save them.",
                Toast.LENGTH_SHORT
        ).show();
    }

    private void restartSystemUi() {
        runRootCommand("killall com.android.systemui", true);
    }

    private void runRootCommand(String command, boolean restartingSystemUi) {
        new Thread(() -> {
            boolean ok = false;
            String error = "";

            try {
                Process process = new ProcessBuilder("su", "-c", command)
                        .redirectErrorStream(true)
                        .start();

                StringBuilder output = new StringBuilder();
                try (BufferedReader reader =
                             new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append('\n');
                    }
                }

                int exitCode = process.waitFor();
                ok = exitCode == 0;
                if (!ok) error = output.toString().trim();
            } catch (Throwable t) {
                error = t.getMessage() == null ? t.toString() : t.getMessage();
            }

            final boolean success = ok;
            final String message = error;

            runOnUiThread(() -> {
                if (success) {
                    Toast.makeText(
                            MainActivity.this,
                            restartingSystemUi
                                    ? "Restarting SystemUI…"
                                    : "Settings saved",
                            Toast.LENGTH_SHORT
                    ).show();
                } else {
                    Toast.makeText(
                            MainActivity.this,
                            message.isEmpty()
                                    ? "Root command failed"
                                    : "Root command failed: " + message,
                            Toast.LENGTH_LONG
                    ).show();

                    requestRootAccess();
                }
            });
        }).start();
    }
}
