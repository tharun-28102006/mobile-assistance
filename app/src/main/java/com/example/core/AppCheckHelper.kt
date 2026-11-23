package com.example.core

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.recaptcha.RecaptchaAppCheckProviderFactory

private const val TAG = "AppCheckInit"

private const val DEBUG_STORE_PREFIX = "com.google.firebase.appcheck.debug.store."
private const val DEBUG_SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"
private const val APP_CHECK_MIGRATION_STORE = "app_check_migrations"
private const val DEBUG_TOKEN_MIGRATION_KEY = "debug_token_migrated_v21"
private const val RECAPTCHA_SITE_KEY_RES = "recaptcha_site_key"

/**
 * Configures Firebase App Check before Compose UI, ViewModels, or any Firebase operations.
 * Must be invoked in MainActivity.onCreate(savedInstanceState).
 */
fun configureAppCheck(context: Context) {
    try {
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }

        if (BuildConfig.DEBUG) {
            val migrationPrefs = context.getSharedPreferences(
                APP_CHECK_MIGRATION_STORE,
                Context.MODE_PRIVATE
            )
            if (!migrationPrefs.getBoolean(DEBUG_TOKEN_MIGRATION_KEY, false)) {
                val persistenceKey = FirebaseApp.getInstance().persistenceKey
                val debugPrefs = context.getSharedPreferences(
                    "$DEBUG_STORE_PREFIX$persistenceKey",
                    Context.MODE_PRIVATE
                )
                if (!debugPrefs.edit().remove(DEBUG_SECRET_KEY).commit()) {
                    throw IllegalStateException(
                        "Could not clear the previously configured App Check debug token."
                    )
                }
                if (!migrationPrefs.edit().putBoolean(DEBUG_TOKEN_MIGRATION_KEY, true).commit()) {
                    throw IllegalStateException("Could not save the App Check token migration state.")
                }
            }

            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
            )
            Log.d(TAG, "DebugAppCheckProviderFactory installed; the SDK will generate a local debug token.")
        } else {
            val resId = context.resources.getIdentifier(
                RECAPTCHA_SITE_KEY_RES,
                "string",
                context.packageName
            )
            if (resId != 0) {
                val siteKey = context.getString(resId)
                if (siteKey.isNotBlank()) {
                    FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                        RecaptchaAppCheckProviderFactory.getInstance(siteKey)
                    )
                    Log.d(TAG, "RecaptchaAppCheckProviderFactory installed.")
                } else {
                    Log.e(TAG, "recaptcha_site_key resource string is blank.")
                }
            } else {
                Log.e(TAG, "recaptcha_site_key string resource not found in package.")
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to initialize Firebase App Check", e)
    }
}
