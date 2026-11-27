package com.example.data

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

private const val AI_TAG = "AiBillScannerRepo"

class AiBillScannerRepository {

    private val billSchema = Schema.obj(
        properties = mapOf(
            "merchantName" to Schema.string(description = "Name of the merchant, utility, vendor, or supplier on the bill"),
            "billDate" to Schema.string(description = "Bill date in YYYY-MM-DD format if visible, otherwise empty string"),
            "totalAmountInr" to Schema.string(description = "Final payable total amount in Indian Rupees as plain digits with optional 2 decimals, e.g. 30000 or 1450.50. Empty string if unreadable."),
            "taxAmountInr" to Schema.string(description = "GST or tax amount in INR if visible, otherwise empty string"),
            "category" to Schema.enumeration(
                values = BILL_CATEGORIES,
                description = "Best matching bill expense category"
            ),
            "lineItems" to Schema.array(
                items = Schema.string(),
                description = "Readable line items formatted as 'Item - ₹Amount'"
            ),
            "confidenceNote" to Schema.string(description = "Short note describing image clarity and whether any fields need manual verification")
        ),
        optionalProperties = listOf("billDate", "taxAmountInr", "lineItems")
    )

    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = billSchema
            }
        )
    }

    /**
     * Scans a bill photo using Gemini 3.8 Flash multimodal structured output.
     * Results are strictly returned for human review and never auto-published.
     */
    suspend fun scanBillImage(bitmap: Bitmap): Result<ScannedBillResult> {
        return try {
            val response = withTimeout(60_000L) {
                model.generateContent(
                    content {
                        image(bitmap)
                        text(
                            "Extract the bill/receipt details carefully from this image. " +
                                "Identify merchant/supplier name, bill date (YYYY-MM-DD), total payable amount in Indian Rupees (numbers only, no rupee symbol or commas), " +
                                "tax/GST amount if present, best category, and readable line items. " +
                                "If the image is blurry or a field is unclear, leave that field empty and mention it in confidenceNote. Do not fabricate amounts."
                        )
                    }
                )
            }
            val rawJson = response.text
                ?: return Result.failure(IllegalStateException("AI scanner returned an empty response. Please enter details manually."))

            val json = JSONObject(rawJson)
            val itemsJson = json.optJSONArray("lineItems")
            val items = mutableListOf<String>()
            if (itemsJson != null) {
                for (i in 0 until itemsJson.length()) {
                    val v = itemsJson.optString(i).trim()
                    if (v.isNotEmpty()) items.add(v)
                }
            }

            Result.success(
                ScannedBillResult(
                    merchantName = json.optString("merchantName", "").trim(),
                    billDate = json.optString("billDate", "").trim(),
                    totalAmountInr = json.optString("totalAmountInr", "").trim(),
                    taxAmountInr = json.optString("taxAmountInr", "").trim(),
                    category = json.optString("category", "Utilities").ifBlank { "Utilities" },
                    lineItems = items,
                    confidenceNote = json.optString("confidenceNote", "Please verify all extracted values before saving.")
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(AI_TAG, "Bill scan timed out", e)
            Result.failure(Exception("Bill scan timed out after 60 seconds. Please retry with a clearer photo or enter details manually."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(AI_TAG, "Bill scan failed", e)
            Result.failure(Exception(e.localizedMessage ?: "Unable to scan bill image. Please enter details manually."))
        }
    }
}
