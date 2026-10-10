package com.tidyclean.storagecleaner.utils

import android.content.Context
import android.content.pm.PackageManager
import android.os.Debug
import android.os.Process
import android.text.TextUtils
import android.util.Log


import com.pdffox.adv.log.LogUtil
import com.google.android.gms.tasks.Task
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.StandardIntegrityManager
import com.google.gson.Gson

import com.tidyclean.storagecleaner.BuildConfig
import com.tidyclean.storagecleaner.base.MyApp
import com.tidyclean.storagecleaner.base.config.AppConfig
import com.tidyclean.storagecleaner.bean.PlayIntegrityBean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.Dns
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.UnknownHostException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.apply
import kotlin.collections.any
import kotlin.collections.filterNot
import kotlin.collections.forEach
import kotlin.collections.joinToString
import kotlin.compareTo
import kotlin.io.use
import kotlin.jvm.java
import kotlin.run
import kotlin.system.exitProcess
import kotlin.text.format
import kotlin.text.isNotEmpty
import kotlin.text.toByteArray

object SafeChecker {

    private const val TAG = "SafeChecker"

    private const val CLOUD_PROJECT_NUMBER =  873431583316L

    private const val SERVER_KEY = "TianWangGaiDiHu"
    private const val INTEGRITY_TIMEOUT_MS = 8000L
    private const val MAX_RETRY_ATTEMPTS = 2
    private const val RETRY_DELAY_MS = 2000L
    private const val CACHE_VALIDITY_MS = 6 * 60 * 60 * 1000L
    private const val INTEGRITY_CACHE_KEY = "appIntegrity"
    private const val INTEGRITY_CACHE_TIME_KEY = "appIntegrityTime"
    private const val UNRECOGNIZED_VERDICT = "UNRECOGNIZED_VERSION"

    private val ROOT_MANAGER_PACKAGES = arrayOf(
        "com.topjohnwu.magisk",
        "eu.chainfire.supersu",
        "com.noshufou.android.su",
        "com.koushikdutta.superuser",
        "com.kingroot.kinguser"
    )

    private val gson = Gson()



    private val okHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
    }


    // ==================== 辅助检测（Play Integrity） ====================
    suspend fun performAuxiliaryChecks() {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Debug mode, skip essential check.")
            return
        }
        try {
            if (!checkPlayIntegrityWithRetry()) {
                Log.e(TAG, "Play Integrity returned definite failure, terminating process.")
                forceExit()
            } else {
                Log.d(TAG, "Play Integrity check passed or uncertain (safe).")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error in auxiliary checks, passing", e)
        }
    }

    private suspend fun checkPlayIntegrityWithRetry(): Boolean {
        repeat(MAX_RETRY_ATTEMPTS) { attempt ->
            try {
                return withTimeout(INTEGRITY_TIMEOUT_MS) {
                    performPlayIntegrityCheck()
                }
            } catch (e: TimeoutCancellationException) {
                Log.w(TAG, "Play Integrity timeout on attempt ${attempt + 1}, treat as uncertain")
                return true
            } catch (e: IOException) {
                Log.w(TAG, "Network error on attempt ${attempt + 1}", e)
                if (attempt == MAX_RETRY_ATTEMPTS - 1) {
                    Log.w(TAG, "All network retries exhausted, treat as uncertain")
                    return true
                }
                delay(RETRY_DELAY_MS)
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected exception during retry, treat as uncertain", e)
                return true
            }
        }
        return true
    }

    private suspend fun performPlayIntegrityCheck(): Boolean = withContext(Dispatchers.IO) {
        if (hasValidCachedVerdict()) {
            return@withContext true
        }
        try {
            val token = obtainIntegrityToken() ?: run {
                Log.w(TAG, "Token generation failed (non-network), passing with caution")
                return@withContext true
            }
            val verdict = validateTokenOnServer(token)
            if (verdict != UNRECOGNIZED_VERDICT) {
                cacheVerdict(verdict)
                Log.d(TAG, "Play Integrity verdict accepted: $verdict")
                true
            } else {
                Log.w(TAG, "Verdict is UNRECOGNIZED_VERSION, tampering detected")
                false
            }
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Non-network error during integrity check, passing with caution", e)
            true
        }
    }

    private fun hasValidCachedVerdict(): Boolean {
        val cachedVerdict = AppPrefsUtils.getString(INTEGRITY_CACHE_KEY, "")
        val cachedTime = AppPrefsUtils.getLong(INTEGRITY_CACHE_TIME_KEY, 0L)
        return cachedVerdict.isNotEmpty()
                && cachedVerdict != UNRECOGNIZED_VERDICT
                && (System.currentTimeMillis() - cachedTime < CACHE_VALIDITY_MS)
    }

    private fun cacheVerdict(verdict: String) {
        AppPrefsUtils.commitString(INTEGRITY_CACHE_KEY, verdict)
        AppPrefsUtils.commitLong(INTEGRITY_CACHE_TIME_KEY, System.currentTimeMillis())
    }

    private suspend fun obtainIntegrityToken(): String? {
        return try {
            val standardIntegrityManager = IntegrityManagerFactory
                .createStandard(MyApp.instance)

            val prepareRequest = StandardIntegrityManager.PrepareIntegrityTokenRequest.builder()
                .setCloudProjectNumber(CLOUD_PROJECT_NUMBER)
                .build()
            val tokenProvider = standardIntegrityManager.prepareIntegrityToken(prepareRequest).await()

            val requestHash = generateRequestHash("${System.currentTimeMillis()}-${Math.random()}")
                ?: return null

            val tokenRequest = StandardIntegrityManager.StandardIntegrityTokenRequest.builder()
                .setRequestHash(requestHash)
                .build()
            tokenProvider.request(tokenRequest).await().token()
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to obtain integrity token (non-network)", e)
            null
        }
    }

    private suspend fun validateTokenOnServer(token: String): String {
        val jsonBody = JSONObject().apply {
            put("Key", SERVER_KEY)
            put("PackageName", MyApp.instance!!.packageName)
            put("token", token)
        }

        val requestBody = jsonBody.toString()
            .toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
        val request = Request.Builder()
            .url(AppConfig.ParseTokenUrl)
            .post(requestBody)
            .build()

        val response = okHttpClient.newCall(request).execute()
        response.use { resp ->
            if (!resp.isSuccessful) throw IOException("Server returned error: ${resp.code}")
            val bodyString = resp.body?.string() ?: throw IOException("Empty response body")
            Log.d(TAG, "Server response: $bodyString")
            return parseVerdictFromResponse(bodyString)
        }
    }

    private fun parseVerdictFromResponse(responseBody: String): String {
        return try {
            val json = JSONObject(responseBody)
            val dataString = json.getString("data")
            val integrityBean = gson.fromJson(dataString, PlayIntegrityBean::class.java)
                ?: throw kotlin.IllegalStateException("Gson parsing returned null")
            integrityBean.appIntegrity?.appRecognitionVerdict
                ?: throw kotlin.IllegalStateException("Verdict field missing")
        } catch (e: IllegalStateException) {
            throw e
        } catch (e: Exception) {
            throw kotlin.IllegalStateException("Malformed server response", e)
        }
    }

    private fun generateRequestHash(input: String): String? {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
            hashBytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Log.e(TAG, "SHA-256 generation failed", e)
            null
        }
    }

    fun forceExit() {
        Log.e(TAG, "Force exiting process")
        ActivityManagerUtils.finishAll()
        Process.killProcess(Process.myPid())
    }
}
