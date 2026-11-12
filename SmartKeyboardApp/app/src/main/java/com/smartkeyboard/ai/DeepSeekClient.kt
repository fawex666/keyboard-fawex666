package com.smartkeyboard.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object DeepSeekClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private const val DEEPSEEK_API_URL = "https://api.deepseek.com/chat/completions"

    /**
     * 1 РЕЖИМ: Автоматическая расстановка знаков препинания через DeepSeek AI
     */
    suspend fun punctuateText(rawText: String, apiKey: String?): String = withContext(Dispatchers.IO) {
        if (apiKey.isNullOrBlank()) {
            return@withContext offlineSmartPunctuate(rawText)
        }

        val systemPrompt = "Ты — специализированный модуль клавиатуры для восстановления пунктуации. " +
                "Пользователь отправляет сырой текст без знаков препинания или с опечатками. " +
                "Твоя задача — расставить точки, запятые, вопросительные/восклицательные знаки, тире, двоеточия " +
                "и исправить заглавные буквы в начале предложений. " +
                "Отвечай ТОЛЬКО готовым исправленным текстом. Никаких приветствий, кавычек или пояснений."

        try {
            val jsonBody = JSONObject().apply {
                put("model", "deepseek-chat")
                put("temperature", 0.2)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", systemPrompt)
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", rawText)
                    })
                })
            }

            val request = Request.Builder()
                .url(DEEPSEEK_API_URL)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext offlineSmartPunctuate(rawText)
                }
                val respStr = response.body?.string() ?: return@withContext offlineSmartPunctuate(rawText)
                val json = JSONObject(respStr)
                val choices = json.getJSONArray("choices")
                if (choices.length() > 0) {
                    val message = choices.getJSONObject(0).getJSONObject("message")
                    return@withContext message.getString("content").trim()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return@withContext offlineSmartPunctuate(rawText)
    }

    /**
     * Переводчик текста
     */
    suspend fun translateText(rawText: String, targetLang: String, apiKey: String?): String = withContext(Dispatchers.IO) {
        if (apiKey.isNullOrBlank()) {
            return@withContext "[Перевод на $targetLang]: $rawText"
        }

        val prompt = "Переведи следующий текст на язык $targetLang. Верни только сам перевод без пояснений:\n\n$rawText"
        try {
            val jsonBody = JSONObject().apply {
                put("model", "deepseek-chat")
                put("temperature", 0.3)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", prompt)
                    })
                })
            }

            val request = Request.Builder()
                .url(DEEPSEEK_API_URL)
                .addHeader("Authorization", "Bearer $apiKey")
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val respStr = response.body?.string() ?: return@withContext rawText
                    val json = JSONObject(respStr)
                    return@withContext json.getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content").trim()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext rawText
    }

    /**
     * Оффлайн-алгоритм умной расстановки запятых, заглавных букв и знаков конца предложения
     */
    private fun offlineSmartPunctuate(text: String): String {
        if (text.isBlank()) return text
        val trimmed = text.trim()
        val words = trimmed.split(Regex("\\s+")).toMutableList()
        if (words.isEmpty()) return text

        // Заглавная буква первого слова
        words[0] = words[0].replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

        val conjunctions = setOf("что", "чтобы", "как", "потому", "если", "когда", "хотя", "но", "а", "где", "куда")
        for (i in 1 until words.size) {
            val lower = words[i].lowercase()
            if (conjunctions.contains(lower)) {
                val prev = words[i - 1]
                if (!prev.endsWith(",") && !prev.endsWith(".") && !prev.endsWith("!") && !prev.endsWith("?")) {
                    words[i - 1] = "$prev,"
                }
            }
        }

        var result = words.joinToString(" ")
        val questions = setOf("как", "где", "куда", "когда", "почему", "зачем", "кто", "что", "сколько")
        val firstWord = words[0].lowercase()

        if (questions.contains(firstWord) || result.contains("?")) {
            if (!result.endsWith("?")) result += "?"
        } else {
            if (!result.endsWith(".") && !result.endsWith("!")) result += "."
        }

        return result
    }
}
