package com.smartkeyboard.ai

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager

class SettingsActivity : AppCompatActivity() {

    private lateinit var etApiKey: EditText
    private lateinit var switchNumberRow: Switch
    private lateinit var tvSelectedSound: TextView
    private var selectedAudioUri: Uri? = null

    companion object {
        private const val REQUEST_PICK_AUDIO = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        etApiKey = findViewById(R.id.et_api_key)
        switchNumberRow = findViewById(R.id.switch_number_row)
        tvSelectedSound = findViewById(R.id.tv_selected_sound)

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        etApiKey.setText(prefs.getString("deepseek_api_key", ""))
        switchNumberRow.isChecked = prefs.getBoolean("pref_show_number_row", true)

        val savedUri = prefs.getString("custom_sound_uri", null)
        if (!savedUri.isNullOrBlank()) {
            tvSelectedSound.text = "Выбран свой звук: $savedUri"
        }

        findViewById<Button>(R.id.btn_pick_sound).setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "audio/*"
            }
            startActivityForResult(intent, REQUEST_PICK_AUDIO)
        }

        findViewById<Button>(R.id.btn_save).setOnClickListener {
            prefs.edit()
                .putString("deepseek_api_key", etApiKey.text.toString().trim())
                .putBoolean("pref_show_number_row", switchNumberRow.isChecked)
                .apply()

            Toast.makeText(this, "Настройки сохранены!", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_PICK_AUDIO && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri ->
                try {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) {}

                selectedAudioUri = uri
                val prefs = PreferenceManager.getDefaultSharedPreferences(this)
                prefs.edit().putString("custom_sound_uri", uri.toString()).apply()
                tvSelectedSound.text = "Выбран свой звук: ${uri.lastPathSegment ?: uri.toString()}"
                Toast.makeText(this, "Кастомный звук сохранен!", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
