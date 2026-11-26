package com.example.data.remote

import android.graphics.Bitmap
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

data class RegisterOcrExtraction(
    val detectedDate: String?,
    val dateUnclear: Boolean,
    val dateRawText: String,
    val fullMasoorQty: Int?,
    val fullMasoorUnclear: Boolean,
    val fullMasoorRawText: String,
    val halfMasoorQty: Int?,
    val halfMasoorUnclear: Boolean,
    val halfMasoorRawText: String,
    val rotiQty: Int?,
    val rotiUnclear: Boolean,
    val rotiRawText: String,
    val rawTranscript: String,
    val overallWarning: String
) {
    val hasAnyUnclearOrMissing: Boolean
        get() = dateUnclear || detectedDate.isNullOrBlank() ||
            fullMasoorUnclear || fullMasoorQty == null ||
            halfMasoorUnclear || halfMasoorQty == null ||
            rotiUnclear || rotiQty == null
}

class RegisterOcrRepository {
    companion object {
        private const val TAG = "RegisterOcrRepository"

        fun parseExtractionJson(jsonString: String): RegisterOcrExtraction {
            val json = JSONObject(jsonString)

            fun optNullableInt(key: String): Int? {
                if (!json.has(key) || json.isNull(key)) return null
                val value = json.optInt(key, -1)
                return if (value >= 0) value else null
            }

            fun optNullableDate(key: String): String? {
                if (!json.has(key) || json.isNull(key)) return null
                val raw = json.optString(key, "").trim()
                return raw.takeIf { Regex("""^\d{4}-\d{2}-\d{2}$""").matches(it) }
            }

            val detectedDate = optNullableDate("detectedDate")
            val fullQty = optNullableInt("fullMasoorQty")
            val halfQty = optNullableInt("halfMasoorQty")
            val rotiQty = optNullableInt("rotiQty")

            return RegisterOcrExtraction(
                detectedDate = detectedDate,
                dateUnclear = json.optBoolean("dateUnclear", detectedDate == null) || detectedDate == null,
                dateRawText = json.optString("dateRawText", "").trim(),
                fullMasoorQty = fullQty,
                fullMasoorUnclear = json.optBoolean("fullMasoorUnclear", fullQty == null),
                fullMasoorRawText = json.optString("fullMasoorRawText", "").trim(),
                halfMasoorQty = halfQty,
                halfMasoorUnclear = json.optBoolean("halfMasoorUnclear", halfQty == null),
                halfMasoorRawText = json.optString("halfMasoorRawText", "").trim(),
                rotiQty = rotiQty,
                rotiUnclear = json.optBoolean("rotiUnclear", rotiQty == null),
                rotiRawText = json.optString("rotiRawText", "").trim(),
                rawTranscript = json.optString("rawTranscript", "").trim(),
                overallWarning = json.optString("overallWarning", "").trim()
            )
        }
    }

    private val ocrSchema = Schema.obj(
        properties = mapOf(
            "detectedDate" to Schema.string(
                description = "Date written on the notebook page formatted strictly as YYYY-MM-DD, or null if missing/unclear.",
                nullable = true
            ),
            "dateUnclear" to Schema.boolean(
                description = "True if the date is missing, smudged, partially cut off, or ambiguous."
            ),
            "dateRawText" to Schema.string(
                description = "Exact handwritten date text seen on the page (e.g. '२५/११/२६' or 'not written')."
            ),
            "fullMasoorQty" to Schema.integer(
                description = "Total plate count for Full Akha Masoor (फुल आखा मसूर / फुल मसूर / Full). MUST be null if missing, illegible, or uncertain.",
                nullable = true
            ),
            "fullMasoorUnclear" to Schema.boolean(
                description = "True if Full Akha Masoor quantity is unclear, overwritten, smudged, or ambiguous."
            ),
            "fullMasoorRawText" to Schema.string(
                description = "Exact handwritten snippet matched for Full Akha Masoor."
            ),
            "halfMasoorQty" to Schema.integer(
                description = "Total plate count for Half Pakka Masoor (हाफ पक्का मसूर / हाफ मसूर / Half). MUST be null if missing, illegible, or uncertain.",
                nullable = true
            ),
            "halfMasoorUnclear" to Schema.boolean(
                description = "True if Half Pakka Masoor quantity is unclear, overwritten, smudged, or ambiguous."
            ),
            "halfMasoorRawText" to Schema.string(
                description = "Exact handwritten snippet matched for Half Pakka Masoor."
            ),
            "rotiQty" to Schema.integer(
                description = "Total piece count for Roti (रोटी / पोळी / चपाती / Roti). MUST be null if missing, illegible, or uncertain.",
                nullable = true
            ),
            "rotiUnclear" to Schema.boolean(
                description = "True if Roti quantity is unclear, overwritten, smudged, or ambiguous."
            ),
            "rotiRawText" to Schema.string(
                description = "Exact handwritten snippet matched for Roti."
            ),
            "rawTranscript" to Schema.string(
                description = "Line-by-line transcription of all readable Marathi, Hindi, and English text and numbers on the register page."
            ),
            "overallWarning" to Schema.string(
                description = "Helpful note in Marathi if image is blurry, cropped, dark, or has uncertain numbers; empty string if clear."
            )
        )
    )

    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = ocrSchema
                temperature = 0.1f
            }
        )
    }

    suspend fun extractFromNotebookImage(bitmap: Bitmap): Result<RegisterOcrExtraction> {
        return try {
            val promptText = """
                You are an accurate handwriting OCR reader for an Indian Maharashtrian hotel daily sales register (दैनिक विक्री रजिस्टर).
                Read the uploaded handwritten notebook page carefully. The handwriting may contain Marathi, Hindi (Devanagari script and Devanagari digits ०१२३४५६७८९), and English text/digits (0-9).

                Extract ONLY these three menu items and the page date:
                1. Full Akha Masoor (फुल आखा मसूर / फुल मसूर / आखा मसूर / Full Masoor / Full / FM) - rate ₹100
                2. Half Pakka Masoor (हाफ पक्का मसूर / हाफ मसूर / पक्का मसूर / Half Masoor / Half / HM) - rate ₹50
                3. Roti (रोटी / रोटी नग / Roti / Chapati / चपाती) - rate ₹15
                4. Page Date (तारीख / Date / दि.) -> convert to YYYY-MM-DD if clear (if 2-digit year like 26, infer 2026).

                STRICT RELIABILITY RULES:
                - NEVER guess, hallucinate, or silently invent a number when handwriting is smudged, crossed out, illegible, ambiguous, or missing.
                - If a quantity is not clearly readable or is missing from the page, return null for that quantity and set its corresponding *Unclear boolean to true.
                - Only return 0 if the page explicitly writes 0 / ० / nil / '-' for that item; if an item is simply unclear or unreadable, return null and *Unclear = true.
                - If there are multiple entries or rows for the same item on that single daily page (e.g., morning + evening counts like 12 + 8), sum them carefully if both numbers are clearly legible, and document the individual numbers in rawText and rawTranscript; if any part is illegible, mark *Unclear = true.
                - Transcribe all readable lines from the page into rawTranscript so the hotel owner can verify everything visually.
                - Keep overallWarning concise and written in Marathi if anything looks blurry or uncertain.
            """.trimIndent()

            val response = withTimeout(60_000L) {
                model.generateContent(
                    content {
                        image(bitmap)
                        text(promptText)
                    }
                )
            }

            val jsonString = response.text
                ?: throw IllegalStateException("AI कडून कोणताही प्रतिसाद मिळाला नाही")
            Result.success(parseExtractionJson(jsonString))
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Register OCR timed out after 60s", e)
            Result.failure(
                IllegalStateException("AI फोटो वाचनास ६० सेकंदांपेक्षा जास्त वेळ लागला. कृपया पुन्हा प्रयत्न करा.")
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Register OCR failed", e)
            Result.failure(
                IllegalStateException(
                    e.localizedMessage ?: "हस्तलिखित फोटो वाचताना त्रुटी आली. कृपया इंटरनेट व फोटो तपासा."
                )
            )
        }
    }
}
