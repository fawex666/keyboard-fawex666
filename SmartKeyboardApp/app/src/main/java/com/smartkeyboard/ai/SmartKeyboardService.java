package com.smartkeyboard.ai;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.inputmethodservice.InputMethodService;
import android.preference.PreferenceManager;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.util.Arrays;
import java.util.List;

public class SmartKeyboardService extends InputMethodService {

    private SoundManager soundManager;
    private boolean isCaps = false;
    private boolean isSymbols = false;
    private String currentLang = "ru";
    private boolean showNumberRow = true;

    private View keyboardView;
    private LinearLayout rowNumbers;
    private LinearLayout rowLetters1;
    private LinearLayout rowLetters2;
    private LinearLayout rowLetters3;
    private LinearLayout rowBottom;

    @Override
    public void onCreate() {
        super.onCreate();
        soundManager = new SoundManager(this);
    }

    @Override
    public View onCreateInputView() {
        LayoutInflater inflater = LayoutInflater.from(this);
        View view = inflater.inflate(R.layout.keyboard_view, null);
        keyboardView = view;

        initViews(view);
        setupToolbar(view);
        buildKeyboardLayout();

        return view;
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        soundManager.reloadSoundPreferences();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        showNumberRow = prefs.getBoolean("pref_show_number_row", true);
        updateNumberRowVisibility();
    }

    private void initViews(View root) {
        rowNumbers = root.findViewById(R.id.row_numbers);
        rowLetters1 = root.findViewById(R.id.row_letters_1);
        rowLetters2 = root.findViewById(R.id.row_letters_2);
        rowLetters3 = root.findViewById(R.id.row_letters_3);
        rowBottom = root.findViewById(R.id.row_bottom);
    }

    private void setupToolbar(View root) {
        Button btnPunct = root.findViewById(R.id.btn_auto_punctuate);
        if (btnPunct != null) {
            btnPunct.setOnClickListener(v -> {
                soundManager.playKeyClick();
                performAutoPunctuation();
            });
        }

        Button btnAi = root.findViewById(R.id.btn_ai_menu);
        if (btnAi != null) {
            btnAi.setOnClickListener(v -> {
                soundManager.playKeyClick();
                performAutoPunctuation();
            });
        }

        ImageButton btnTranslate = root.findViewById(R.id.btn_translate);
        if (btnTranslate != null) {
            btnTranslate.setOnClickListener(v -> {
                soundManager.playKeyClick();
                performQuickTranslate("en");
            });
        }

        ImageButton btnCamera = root.findViewById(R.id.btn_camera);
        if (btnCamera != null) {
            btnCamera.setOnClickListener(v -> {
                soundManager.playKeyClick();
                Intent intent = new Intent(this, CameraOcrActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            });
        }

        Button btnToggleNum = root.findViewById(R.id.btn_toggle_numbers);
        if (btnToggleNum != null) {
            btnToggleNum.setOnClickListener(v -> {
                soundManager.playKeyClick();
                showNumberRow = !showNumberRow;
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
                prefs.edit().putBoolean("pref_show_number_row", showNumberRow).apply();
                updateNumberRowVisibility();
            });
        }

        ImageButton btnSettings = root.findViewById(R.id.btn_settings);
        if (btnSettings != null) {
            btnSettings.setOnClickListener(v -> {
                soundManager.playKeyClick();
                Intent intent = new Intent(this, SettingsActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            });
        }
    }

    private void updateNumberRowVisibility() {
        if (rowNumbers != null) {
            rowNumbers.setVisibility(showNumberRow ? View.VISIBLE : View.GONE);
        }
    }

    private void performAutoPunctuation() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        CharSequence before = ic.getTextBeforeCursor(500, 0);
        String beforeText = before != null ? before.toString() : "";
        if (beforeText.trim().isEmpty()) {
            Toast.makeText(this, "Сначала наберите текст", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "DeepSeek расставляет знаки...", Toast.LENGTH_SHORT).show();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String apiKey = prefs.getString("deepseek_api_key", "");

        new Thread(() -> {
            String punctuated = DeepSeekClient.punctuateText(beforeText, apiKey);
            if (keyboardView != null) {
                keyboardView.post(() -> {
                    ic.deleteSurroundingText(beforeText.length(), 0);
                    ic.commitText(punctuated, 1);
                });
            }
        }).start();
    }

    private void performQuickTranslate(String targetLang) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        CharSequence before = ic.getTextBeforeCursor(300, 0);
        String beforeText = before != null ? before.toString() : "";
        if (beforeText.trim().isEmpty()) return;

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String apiKey = prefs.getString("deepseek_api_key", "");

        new Thread(() -> {
            String translated = DeepSeekClient.translateText(beforeText, targetLang, apiKey);
            if (keyboardView != null) {
                keyboardView.post(() -> {
                    ic.deleteSurroundingText(beforeText.length(), 0);
                    ic.commitText(translated, 1);
                });
            }
        }).start();
    }

    private void buildKeyboardLayout() {
        buildNumberRow();
        buildLetterRows();
        buildBottomRow();
    }

    private void buildNumberRow() {
        if (rowNumbers == null) return;
        rowNumbers.removeAllViews();
        List<String> numbers = Arrays.asList("1", "2", "3", "4", "5", "6", "7", "8", "9", "0");
        for (String num : numbers) {
            rowNumbers.addView(createKeyButton(num, 1.0f, () -> commitCharacter(num)));
        }
    }

    private void buildLetterRows() {
        if (rowLetters1 == null || rowLetters2 == null || rowLetters3 == null) return;
        rowLetters1.removeAllViews();
        rowLetters2.removeAllViews();
        rowLetters3.removeAllViews();

        List<String> r1;
        List<String> r2;
        List<String> r3;

        if (isSymbols) {
            r1 = Arrays.asList("[", "]", "{", "}", "#", "%", "^", "*", "+", "=");
            r2 = Arrays.asList("_", "\\", "|", "~", "<", ">", "$", "€", "₽", "•");
            r3 = Arrays.asList(".", ",", "?", "!", "'", "\"", "-", ":", ";");
        } else if ("ru".equals(currentLang)) {
            r1 = Arrays.asList("й", "ц", "у", "к", "е", "н", "г", "ш", "щ", "з", "х", "ъ");
            r2 = Arrays.asList("ф", "ы", "в", "а", "п", "р", "о", "л", "д", "ж", "э");
            r3 = Arrays.asList("я", "ч", "с", "м", "и", "т", "ь", "б", "ю");
        } else {
            r1 = Arrays.asList("q", "w", "e", "r", "t", "y", "u", "i", "o", "p");
            r2 = Arrays.asList("a", "s", "d", "f", "g", "h", "j", "k", "l");
            r3 = Arrays.asList("z", "x", "c", "v", "b", "n", "m");
        }

        for (String c : r1) {
            String ch = (isCaps && !isSymbols) ? c.toUpperCase() : c;
            rowLetters1.addView(createKeyButton(ch, 1.0f, () -> commitCharacter(ch)));
        }

        for (String c : r2) {
            String ch = (isCaps && !isSymbols) ? c.toUpperCase() : c;
            rowLetters2.addView(createKeyButton(ch, 1.0f, () -> commitCharacter(ch)));
        }

        // Shift key
        rowLetters3.addView(createKeyButton(isCaps ? "⇧" : "⇪", 1.4f, () -> {
            isCaps = !isCaps;
            buildLetterRows();
        }));

        for (String c : r3) {
            String ch = (isCaps && !isSymbols) ? c.toUpperCase() : c;
            rowLetters3.addView(createKeyButton(ch, 1.0f, () -> commitCharacter(ch)));
        }

        // Backspace key labeled "Даша"
        rowLetters3.addView(createKeyButton("⌫\nДаша", 1.4f, () -> {
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) {
                ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL));
            }
        }));
    }

    private void buildBottomRow() {
        if (rowBottom == null) return;
        rowBottom.removeAllViews();

        // 1. Sym
        rowBottom.addView(createKeyButton(isSymbols ? "АБВ" : "?123", 1.3f, () -> {
            isSymbols = !isSymbols;
            buildLetterRows();
        }));

        // 2. Lang switch
        rowBottom.addView(createKeyButton("ru".equals(currentLang) ? "EN" : "RU", 1.2f, () -> {
            currentLang = "ru".equals(currentLang) ? "en" : "ru";
            buildLetterRows();
        }));

        // 3. Comma
        rowBottom.addView(createKeyButton(",", 1.0f, () -> commitCharacter(",")));

        // 4. Space labeled "Султан, чёрт"
        rowBottom.addView(createKeyButton("Султан, чёрт", 5.0f, () -> commitCharacter(" ")));

        // 5. Period
        rowBottom.addView(createKeyButton(".", 1.0f, () -> commitCharacter(".")));

        // 6. Enter labeled "Прости"
        rowBottom.addView(createKeyButton("Прости", 1.5f, () -> {
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) {
                ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
            }
        }));
    }

    private void commitCharacter(String text) {
        soundManager.playKeyClick();
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            ic.commitText(text, 1);
        }
    }

    private Button createKeyButton(String label, float weight, Runnable onClick) {
        Button btn = new Button(this);
        btn.setText(label);
        btn.setTextSize(14f);
        btn.setAllCaps(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, weight);
        lp.setMargins(2, 2, 2, 2);
        btn.setLayoutParams(lp);
        btn.setBackgroundResource(android.R.drawable.btn_default);
        btn.setOnClickListener(v -> {
            soundManager.playKeyClick();
            onClick.run();
        });
        return btn;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (soundManager != null) {
            soundManager.release();
        }
    }
}
