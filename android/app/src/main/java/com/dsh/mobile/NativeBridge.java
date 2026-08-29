package com.dsh.mobile;

import android.content.SharedPreferences;
import android.os.Build;
import android.webkit.JavascriptInterface;

import androidx.annotation.Keep;

import java.nio.charset.StandardCharsets;

@Keep
public final class NativeBridge {
    private static final String PREFS = "dsh-mobile-secure";
    private static final String ENDPOINT = "endpoint";
    private static final String TOKEN = "token";
    private static final String CACHE_ID = "snapshot-v1";
    private static final int MAX_CACHE_BYTES = 16 * 1024 * 1024;

    private final SharedPreferences preferences;
    private final SecureStore secureStore = new SecureStore();
    private final CacheDao cache;
    private final MainActivity activity;

    NativeBridge(MainActivity context) {
        activity = context;
        android.content.Context app = context.getApplicationContext();
        preferences = app.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE);
        cache = MobileDatabase.get(app).cache();
    }

    @JavascriptInterface
    public String getEndpoint() {
        return decryptPreference(ENDPOINT);
    }

    @JavascriptInterface
    public String getToken() {
        return decryptPreference(TOKEN);
    }

    @JavascriptInterface
    public void saveConnection(String endpoint, String token) {
        if (endpoint == null || token == null || endpoint.length() > 2048 || token.length() > 4096) return;
        try {
            preferences.edit()
                .putString(ENDPOINT, secureStore.encrypt(endpoint))
                .putString(TOKEN, secureStore.encrypt(token))
                .commit();
        } catch (Exception ignored) {
            preferences.edit().clear().commit();
        }
    }

    @JavascriptInterface
    public void clearConnection() {
        preferences.edit().clear().commit();
    }

    @JavascriptInterface
    public String getDeviceName() {
        String manufacturer = Build.MANUFACTURER == null ? "Android" : Build.MANUFACTURER.trim();
        String model = Build.MODEL == null ? "device" : Build.MODEL.trim();
        return manufacturer.equalsIgnoreCase(model) ? model : manufacturer + " " + model;
    }

    @JavascriptInterface
    public String readCache() {
        try {
            CacheRecord record = cache.read(CACHE_ID);
            return record == null ? "" : secureStore.decrypt(record.encryptedPayload);
        } catch (Exception ignored) {
            return "";
        }
    }

    @JavascriptInterface
    public void writeCache(String payload) {
        if (payload == null || payload.getBytes(StandardCharsets.UTF_8).length > MAX_CACHE_BYTES) return;
        try {
            cache.write(new CacheRecord(CACHE_ID, secureStore.encrypt(payload), System.currentTimeMillis()));
        } catch (Exception ignored) {
            // A cache write is best effort; server session history remains authoritative.
        }
    }

    @JavascriptInterface
    public void clearCache() {
        cache.clear();
    }

    @JavascriptInterface
    public void setSystemBars(boolean light) {
        activity.runOnUiThread(() -> activity.applySystemBars(light));
    }

    private String decryptPreference(String key) {
        try {
            return secureStore.decrypt(preferences.getString(key, ""));
        } catch (Exception ignored) {
            preferences.edit().remove(key).commit();
            return "";
        }
    }
}
