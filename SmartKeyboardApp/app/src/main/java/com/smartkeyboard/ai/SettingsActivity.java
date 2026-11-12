package com.smartkeyboard.ai;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {

    private EditText etApiKey;
    private Switch switchNumberRow;
    private TextView tvSelectedSound;
    private static final int REQUEST_PICK_AUDIO = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        etApiKey = findViewById(R.id.et_api_key);
        switchNumberRow = findViewById(R.id.switch_number_row);
        tvSelectedSound = findViewById(R.id.tv_selected_sound);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        etApiKey.setText(prefs.getString("deepseek_api_key", ""));
        switchNumberRow.setChecked(prefs.getBoolean("pref_show_number_row", true));

        String savedUri = prefs.getString("custom_sound_uri", null);
        if (savedUri != null && !savedUri.trim().isEmpty()) {
            tvSelectedSound.setText("Выбран свой звук: " + savedUri);
        }

        findViewById(R.id.btn_pick_sound).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("audio/*");
            startActivityForResult(intent, REQUEST_PICK_AUDIO);
        });

        findViewById(R.id.btn_save).setOnClickListener(v -> {
            prefs.edit()
                    .putString("deepseek_api_key", etApiKey.getText().toString().trim())
                    .putBoolean("pref_show_number_row", switchNumberRow.isChecked())
                    .apply();

            Toast.makeText(this, "Настройки сохранены!", Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PICK_AUDIO && resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) {}

            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            prefs.edit().putString("custom_sound_uri", uri.toString()).apply();
            tvSelectedSound.setText("Выбран свой звук: " + uri.getLastPathSegment());
            Toast.makeText(this, "Кастомный звук сохранен!", Toast.LENGTH_SHORT).show();
        }
    }
}
