package org.enzo.autojjs;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;

public final class AutoJJSBridge {
    public static final String PREFS = "auto_jjs";
    public static final String ACTION_CONFIG_CHANGED = "org.enzo.autojjs.CONFIG_CHANGED";
    public static final float MIN_INTERVAL_SECONDS = 1.8f;

    private AutoJJSBridge() {
    }

    public static void saveConfig(Context context, int start, int end, int current, float intervalSeconds, boolean autoClickSend) {
        int safeStart = clamp(start, 1, JJSText.MAX_NUMBER);
        int safeEnd = clamp(end, safeStart, JJSText.MAX_NUMBER);
        int safeCurrent = clamp(current, safeStart, safeEnd);
        float safeInterval = Math.max(MIN_INTERVAL_SECONDS, intervalSeconds);

        prefs(context).edit()
            .putInt("start", safeStart)
            .putInt("end", safeEnd)
            .putInt("current", safeCurrent)
            .putFloat("interval", safeInterval)
            .putBoolean("autoClickSend", autoClickSend)
            .apply();
        context.sendBroadcast(new Intent(ACTION_CONFIG_CHANGED));
    }

    public static void startOverlay(Context context) {
        Intent intent = new Intent(context, JJSOverlayService.class);
        context.startService(intent);
    }

    public static void stopOverlay(Context context) {
        context.stopService(new Intent(context, JJSOverlayService.class));
    }

    public static void openOverlaySettings(Context context) {
        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
        intent.setData(Uri.parse("package:" + context.getPackageName()));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    public static void openAccessibilitySettings(Context context) {
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    public static boolean canDrawOverlays(Context context) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context);
    }

    public static boolean isAccessibilityEnabled(Context context) {
        String expected = context.getPackageName() + "/" + JJSAccessibilityService.class.getName();
        String enabled = Settings.Secure.getString(
            context.getContentResolver(),
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        );
        if (enabled == null) {
            return false;
        }
        TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
        splitter.setString(enabled);
        while (splitter.hasNext()) {
            if (expected.equalsIgnoreCase(splitter.next())) {
                return true;
            }
        }
        return false;
    }

    public static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
