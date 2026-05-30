package org.enzo.autojjs;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class JJSOverlayService extends Service {
    private static final int NOTIFICATION_ID = 410;
    private static final String CHANNEL_ID = "auto_jjs_overlay";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private WindowManager windowManager;
    private WindowManager.LayoutParams params;
    private LinearLayout root;
    private TextView title;
    private TextView preview;
    private Button playButton;
    private boolean running;
    private int start = 1;
    private int end = 100;
    private int current = 1;
    private float interval = 1.0f;
    private boolean autoClickSend = true;

    private final Runnable autoStep = new Runnable() {
        @Override
        public void run() {
            if (!running) {
                return;
            }
            sendCurrent(true);
            handler.postDelayed(this, Math.max(1000, (long) (interval * 1000)));
        }
    };

    private final BroadcastReceiver configReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            loadConfig();
            updateTitle();
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        loadConfig();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(configReceiver, new IntentFilter(AutoJJSBridge.ACTION_CONFIG_CHANGED), Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(configReceiver, new IntentFilter(AutoJJSBridge.ACTION_CONFIG_CHANGED));
        }
        if (!AutoJJSBridge.canDrawOverlays(this)) {
            Toast.makeText(this, "Ative a permissao de sobrepor apps", Toast.LENGTH_LONG).show();
            stopSelf();
            return;
        }
        createOverlay();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;
        handler.removeCallbacks(autoStep);
        try {
            unregisterReceiver(configReceiver);
        } catch (IllegalArgumentException ignored) {
        }
        if (windowManager != null && root != null) {
            windowManager.removeView(root);
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createOverlay() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        params = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 24;
        params.y = 180;

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(16, 14, 16, 14);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.argb(238, 22, 28, 31));
        bg.setCornerRadius(18);
        bg.setStroke(2, Color.rgb(30, 150, 200));
        root.setBackground(bg);

        title = new TextView(this);
        title.setTextColor(Color.WHITE);
        title.setTextSize(13);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        preview = new TextView(this);
        preview.setTextColor(Color.rgb(190, 235, 255));
        preview.setTextSize(12);
        preview.setGravity(Gravity.CENTER);
        preview.setSingleLine(true);
        root.addView(preview);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(row);

        playButton = button("AUTO");
        Button sendButton = button("1x");
        Button nextButton = button("PROX");
        Button closeButton = button("X");
        row.addView(playButton);
        row.addView(sendButton);
        row.addView(nextButton);
        row.addView(closeButton);

        playButton.setOnClickListener(v -> toggleRunning());
        sendButton.setOnClickListener(v -> sendCurrent(false));
        nextButton.setOnClickListener(v -> {
            advance();
            updateTitle();
        });
        closeButton.setOnClickListener(v -> stopSelf());
        root.setOnTouchListener(new DragTouchListener());

        updateTitle();
        windowManager.addView(root, params);
    }

    private Button button(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setTextSize(12);
        button.setAllCaps(false);
        button.setMinWidth(66);
        button.setMinHeight(48);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(18, 112, 156));
        bg.setCornerRadius(10);
        button.setBackground(bg);
        return button;
    }

    private void toggleRunning() {
        running = !running;
        playButton.setText(running ? "PARAR" : "AUTO");
        if (running) {
            handler.removeCallbacks(autoStep);
            handler.post(autoStep);
        } else {
            handler.removeCallbacks(autoStep);
        }
    }

    private void sendCurrent(boolean fromAuto) {
        String text = JJSText.gerar(current);
        boolean ok = JJSAccessibilityService.sendText(this, text, autoClickSend);
        if (!ok) {
            Toast.makeText(this, "Toque em um campo de texto e ative a acessibilidade", Toast.LENGTH_SHORT).show();
            if (fromAuto) {
                toggleRunning();
            }
            return;
        }
        advance();
        saveCurrent();
        updateTitle();
    }

    private void advance() {
        current = current < end ? current + 1 : start;
    }

    private void loadConfig() {
        SharedPreferences prefs = AutoJJSBridge.prefs(this);
        start = prefs.getInt("start", 1);
        end = prefs.getInt("end", 100);
        current = AutoJJSBridge.clamp(prefs.getInt("current", start), start, end);
        interval = Math.max(1.0f, prefs.getFloat("interval", 1.0f));
        autoClickSend = prefs.getBoolean("autoClickSend", true);
    }

    private void saveCurrent() {
        AutoJJSBridge.prefs(this).edit().putInt("current", current).apply();
    }

    private void updateTitle() {
        if (title != null) {
            title.setText((running ? "AUTO LIGADO  " : "AUTO JJS  ") + current + " / " + end);
        }
        if (preview != null) {
            preview.setText(JJSText.gerar(current));
        }
    }

    private class DragTouchListener implements View.OnTouchListener {
        private int initialX;
        private int initialY;
        private float initialTouchX;
        private float initialTouchY;

        @Override
        public boolean onTouch(View view, MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    initialX = params.x;
                    initialY = params.y;
                    initialTouchX = event.getRawX();
                    initialTouchY = event.getRawY();
                    return false;
                case MotionEvent.ACTION_MOVE:
                    params.x = initialX + (int) (event.getRawX() - initialTouchX);
                    params.y = initialY + (int) (event.getRawY() - initialTouchY);
                    windowManager.updateViewLayout(root, params);
                    return true;
                default:
                    return false;
            }
        }
    }
}
