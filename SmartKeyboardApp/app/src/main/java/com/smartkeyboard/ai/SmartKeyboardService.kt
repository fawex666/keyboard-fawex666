package com.smartkeyboard.ai

import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import androidx.preference.PreferenceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SmartKeyboardService : InputMethodService() {

    private val serviceScope = CoroutineScope(Dispatchers.Main)
    private lateinit var soundManager: SoundManager

    private var isCaps = false
    private var isSymbols = false
    private var currentLang = "ru" // "ru" or "en"
    private var showNumberRow = true

    private var keyboardView: View? = null
    private var rowNumbers: LinearLayout? = null
    private var rowLetters1: LinearLayout? = null
    private var rowLetters2: LinearLayout? = null
    private var rowLetters3: LinearLayout? = null
    private var rowBottom: LinearLayout? = null

    override fun onCreate() {
        super.onCreate()
        soundManager = SoundManager(this)
    }

    override fun onCreateInputView(): View {
        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.keyboard_view, null)
        keyboardView = view

        initViews(view)
        setupToolbar(view)
        buildKeyboardLayout()

        return view
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        soundManager.reloadSoundPreferences()
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        showNumberRow = prefs.getBoolean("pref_show_number_row", true)
        updateNumberRowVisibility()
    }

    private fun initViews(root: View) {
        rowNumbers = root.findViewById(R.id.row_numbers)
        rowLetters1 = root.findViewById(R.id.row_letters_1)
        rowLetters2 = root.findViewById(R.id.row_letters_2)
        rowLetters3 = root.findViewById(R.id.row_letters_3)
        rowBottom = root.findViewById(R.id.row_bottom)
    }

    private fun setupToolbar(root: View) {
        // AI Auto-Punctuation (1 РЕЖИМ)
        root.findViewById<Button>(R.id.btn_auto_punctuate)?.setOnClickListener {
            soundManager.playKeyClick()
            performAutoPunctuation()
        }

        // DeepSeek AI Menu
        root.findViewById<Button>(R.id.btn_ai_menu)?.setOnClickListener {
            soundManager.playKeyClick()
            performAutoPunctuation()
        }

        // Translator
        root.findViewById<ImageButton>(R.id.btn_translate)?.setOnClickListener {
            soundManager.playKeyClick()
            performQuickTranslate("en")
        }

        // Camera OCR
        root.findViewById<ImageButton>(R.id.btn_camera)?.setOnClickListener {
            soundManager.playKeyClick()
            val intent = Intent(this, CameraOcrActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        }

        // Toggle Number Row (1 2 3 4 5 6 7 8 9 0)
        root.findViewById<Button>(R.id.btn_toggle_numbers)?.setOnClickListener {
            soundManager.playKeyClick()
            showNumberRow = !showNumberRow
            val prefs = PreferenceManager.getDefaultSharedPreferences(this)
            prefs.edit().putBoolean("pref_show_number_row", showNumberRow).apply()
            updateNumberRowVisibility()
        }

        // Settings Activity
        root.findViewById<ImageButton>(R.id.btn_settings)?.setOnClickListener {
            soundManager.playKeyClick()
            val intent = Intent(this, SettingsActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        }
    }

    private fun updateNumberRowVisibility() {
        rowNumbers?.visibility = if (showNumberRow) View.VISIBLE else View.GONE
    }

    /**
     * 1 РЕЖИМ: Автоматическая расстановка знаков препинания через DeepSeek
     */
    private fun performAutoPunctuation() {
        val ic = currentInputConnection ?: return
        val beforeText = ic.getTextBeforeCursor(500, 0)?.toString() ?: ""
        if (beforeText.isBlank()) {
            Toast.makeText(this, "Сначала наберите текст для расстановки знаков", Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(this, "DeepSeek расставляет знаки...", Toast.LENGTH_SHORT).show()
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val apiKey = prefs.getString("deepseek_api_key", "")

        serviceScope.launch {
            val punctuated = DeepSeekClient.punctuateText(beforeText, apiKey)
            withContext(Dispatchers.Main) {
                // Заменяем непунктуированный текст на оформленный
                ic.deleteSurroundingText(beforeText.length, 0)
                ic.commitText(punctuated, 1)
            }
        }
    }

    private fun performQuickTranslate(targetLang: String) {
        val ic = currentInputConnection ?: return
        val beforeText = ic.getTextBeforeCursor(300, 0)?.toString() ?: ""
        if (beforeText.isBlank()) return

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val apiKey = prefs.getString("deepseek_api_key", "")

        serviceScope.launch {
            val translated = DeepSeekClient.translateText(beforeText, targetLang, apiKey)
            withContext(Dispatchers.Main) {
                ic.deleteSurroundingText(beforeText.length, 0)
                ic.commitText(translated, 1)
            }
        }
    }

    private fun buildKeyboardLayout() {
        buildNumberRow()
        buildLetterRows()
        buildBottomRow()
    }

    private fun buildNumberRow() {
        rowNumbers?.removeAllViews()
        val numbers = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
        for (num in numbers) {
            val btn = createKeyButton(num, weight = 1.0f) {
                commitCharacter(num)
            }
            rowNumbers?.addView(btn)
        }
    }

    private fun buildLetterRows() {
        rowLetters1?.removeAllViews()
        rowLetters2?.removeAllViews()
        rowLetters3?.removeAllViews()

        val r1Chars = if (isSymbols) {
            listOf("[", "]", "{", "}", "#", "%", "^", "*", "+", "=")
        } else if (currentLang == "ru") {
            listOf("й", "ц", "у", "к", "е", "н", "г", "ш", "щ", "з", "х", "ъ")
        } else {
            listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p")
        }

        for (c in r1Chars) {
            val displayChar = if (isCaps && !isSymbols) c.uppercase() else c
            rowLetters1?.addView(createKeyButton(displayChar, 1.0f) { commitCharacter(displayChar) })
        }

        val r2Chars = if (isSymbols) {
            listOf("_", "\\", "|", "~", "<", ">", "$", "€", "₽", "•")
        } else if (currentLang == "ru") {
            listOf("ф", "ы", "в", "а", "п", "р", "о", "л", "д", "ж", "э")
        } else {
            listOf("a", "s", "d", "f", "g", "h", "j", "k", "l")
        }

        for (c in r2Chars) {
            val displayChar = if (isCaps && !isSymbols) c.uppercase() else c
            rowLetters2?.addView(createKeyButton(displayChar, 1.0f) { commitCharacter(displayChar) })
        }

        // Row 3: Shift, letters, Backspace
        val shiftBtn = createKeyButton(if (isCaps) "⇧" else "⇪", 1.4f) {
            isCaps = !isCaps
            buildLetterRows()
        }
        rowLetters3?.addView(shiftBtn)

        val r3Chars = if (isSymbols) {
            listOf(".", ",", "?", "!", "\'", "\"", "-", ":", ";")
        } else if (currentLang == "ru") {
            listOf("я", "ч", "с", "м", "и", "т", "ь", "б", "ю")
        } else {
            listOf("z", "x", "c", "v", "b", "n", "m")
        }

        for (c in r3Chars) {
            val displayChar = if (isCaps && !isSymbols) c.uppercase() else c
            rowLetters3?.addView(createKeyButton(displayChar, 1.0f) { commitCharacter(displayChar) })
        }

        val backspaceBtn = createKeyButton("⌫\nДаша", 1.4f) {
            currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
        }
        rowLetters3?.addView(backspaceBtn)
    }

    private fun buildBottomRow() {
        rowBottom?.removeAllViews()

        // 1. Switch symbols
        val symBtn = createKeyButton(if (isSymbols) "АБВ" else "?123", 1.3f) {
            isSymbols = !isSymbols
            buildLetterRows()
        }
        rowBottom?.addView(symBtn)

        // 2. Switch language
        val langBtn = createKeyButton(if (currentLang == "ru") "EN" else "RU", 1.2f) {
            currentLang = if (currentLang == "ru") "en" else "ru"
            buildLetterRows()
        }
        rowBottom?.addView(langBtn)

        // 3. Comma
        rowBottom?.addView(createKeyButton(",", 1.0f) { commitCharacter(",") })

        // 4. Large Spacebar with custom label "Султан, чёрт"
        val spaceBtn = createKeyButton("Султан, чёрт", 5.0f) {
            commitCharacter(" ")
        }
        rowBottom?.addView(spaceBtn)

        // 5. Period
        rowBottom?.addView(createKeyButton(".", 1.0f) { commitCharacter(".") })

        // 6. Enter labeled "Прости"
        val enterBtn = createKeyButton("Прости", 1.5f) {
            val ic = currentInputConnection ?: return@createKeyButton
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
        }
        rowBottom?.addView(enterBtn)
    }

    private fun commitCharacter(text: String) {
        soundManager.playKeyClick()
        currentInputConnection?.commitText(text, 1)
    }

    private fun createKeyButton(label: String, weight: Float, onClick: () -> Unit): Button {
        val btn = Button(this).apply {
            text = label
            textSize = 15f
            isAllCaps = false
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, weight).apply {
                setMargins(2, 2, 2, 2)
            }
            setBackgroundResource(android.R.drawable.btn_default)
            setOnClickListener {
                soundManager.playKeyClick()
                onClick()
            }
        }
        return btn
    }

    override fun onDestroy() {
        super.onDestroy()
        soundManager.release()
    }
}
