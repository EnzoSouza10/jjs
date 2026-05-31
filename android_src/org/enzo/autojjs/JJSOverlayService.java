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
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class JJSOverlayService extends Service {
    private static final int NOTIFICATION_ID = 410;
    private static final String CHANNEL_ID = "auto_jjs_overlay";
    private static final long MIN_INTERVAL_MS = 1800L;
    private static final long SEND_CLICK_DELAY_MS = 450L;
    private static final long ADVANCE_DELAY_MS = 800L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private WindowManager windowManager;
    private WindowManager.LayoutParams params;
    private LinearLayout root;
    private TextView title;
    private TextView preview;
    private TextView credits;
    private Button playButton;
    private Button miniButton;
    private EditText endInput;
    private final List<View> expandedViews = new ArrayList<>();
    private boolean running;
    private boolean sending;
    private boolean minimized;
    private boolean focusable;
    private int start = 1;
    private int end = 100;
    private int current = 1;
    private float interval = 2.2f;
    private boolean autoClickSend = true;

    private final Runnable autoStep = new Runnable() {
        @Override
        public void run() {
            if (!running || sending) {
                return;
            }
            sendCurrent(true);
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
        handler.removeCallbacksAndMessages(null);
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
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_SECURE,
            PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 24;
        params.y = 180;

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(18, 16, 18, 16);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.argb(244, 12, 9, 10));
        bg.setCornerRadius(20);
        bg.setStroke(3, Color.rgb(210, 12, 24));
        root.setBackground(bg);

        miniButton = button("JJS");
        miniButton.setMinWidth(86);
        miniButton.setTextSize(13);
        miniButton.setVisibility(View.GONE);
        miniButton.setOnClickListener(v -> setMinimized(false));
        root.addView(miniButton);

        title = new TextView(this);
        title.setTextColor(Color.WHITE);
        title.setTextSize(14);
        title.setGravity(Gravity.CENTER);
        addExpandedView(title);

        preview = new TextView(this);
        preview.setTextColor(Color.rgb(255, 210, 210));
        preview.setTextSize(12);
        preview.setGravity(Gravity.CENTER);
        preview.setSingleLine(true);
        addExpandedView(preview);

        credits = new TextView(this);
        credits.setTextColor(Color.rgb(224, 20, 34));
        credits.setTextSize(11);
        credits.setGravity(Gravity.CENTER);
        credits.setText("Criador: Xx0iluminati0xX");
        addExpandedView(credits);

        TextView help = new TextView(this);
        help.setTextColor(Color.rgb(255, 180, 180));
        help.setTextSize(10);
        help.setGravity(Gravity.CENTER);
        help.setText("AUTO inicia/parar | ESCREVER envia | MIN esconde | FECHAR remove");
        addExpandedView(help);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        addExpandedView(row);

        playButton = button("AUTO");
        Button sendButton = button("ENVIAR");
        Button prevButton = button("< JJS");
        Button nextButton = button("JJS >");
        Button minButton = button("MIN");
        row.addView(playButton);
        row.addView(sendButton);

        LinearLayout navRow = new LinearLayout(this);
        navRow.setOrientation(LinearLayout.HORIZONTAL);
        addExpandedView(navRow);
        navRow.addView(prevButton);
        navRow.addView(nextButton);
        navRow.addView(minButton);

        LinearLayout limitRow = new LinearLayout(this);
        limitRow.setOrientation(LinearLayout.HORIZONTAL);
        addExpandedView(limitRow);

        Button minusEndButton = button("-10");
        endInput = new EditText(this);
        endInput.setTextColor(Color.WHITE);
        endInput.setTextSize(12);
        endInput.setGravity(Gravity.CENTER);
        endInput.setSingleLine(true);
        endInput.setSelectAllOnFocus(true);
        endInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        endInput.setMinWidth(96);
        endInput.setMinHeight(48);
        GradientDrawable endBg = new GradientDrawable();
        endBg.setColor(Color.rgb(48, 8, 12));
        endBg.setCornerRadius(10);
        endBg.setStroke(1, Color.rgb(255, 70, 80));
        endInput.setBackground(endBg);
        endInput.setOnFocusChangeListener((view, hasFocus) -> setOverlayFocusable(hasFocus));
        Button plusEndButton = button("+10");
        Button applyEndButton = button("OK");
        limitRow.addView(minusEndButton);
        limitRow.addView(endInput);
        limitRow.addView(plusEndButton);
        limitRow.addView(applyEndButton);

        LinearLayout closeRow = new LinearLayout(this);
        closeRow.setOrientation(LinearLayout.HORIZONTAL);
        addExpandedView(closeRow);
        Button disableButton = button("DESATIVAR");
        Button closeButton = button("FECHAR");
        closeRow.addView(disableButton);
        closeRow.addView(closeButton);

        playButton.setOnClickListener(v -> toggleRunning());
        sendButton.setOnClickListener(v -> sendCurrent(false));
        prevButton.setOnClickListener(v -> {
            previous();
            saveCurrent();
            updateTitle();
        });
        nextButton.setOnClickListener(v -> {
            advance();
            saveCurrent();
            updateTitle();
        });
        minusEndButton.setOnClickListener(v -> adjustEndBy(-10));
        plusEndButton.setOnClickListener(v -> adjustEndBy(10));
        applyEndButton.setOnClickListener(v -> applyEndInput());
        minButton.setOnClickListener(v -> setMinimized(true));
        disableButton.setOnClickListener(v -> deactivateOverlay());
        closeButton.setOnClickListener(v -> closeOverlay());
        root.setOnTouchListener(new DragTouchListener());

        updateTitle();
        windowManager.addView(root, params);
    }

    private void addExpandedView(View view) {
        expandedViews.add(view);
        root.addView(view);
    }

    private Button button(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setTextSize(11);
        button.setAllCaps(false);
        button.setMinWidth(64);
        button.setMinHeight(48);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(164, 10, 20));
        bg.setCornerRadius(10);
        bg.setStroke(1, Color.rgb(255, 70, 80));
        button.setBackground(bg);
        return button;
    }

    private void setMinimized(boolean shouldMinimize) {
        minimized = shouldMinimize;
        for (View view : expandedViews) {
            view.setVisibility(minimized ? View.GONE : View.VISIBLE);
        }
        miniButton.setVisibility(minimized ? View.VISIBLE : View.GONE);
        root.setPadding(minimized ? 8 : 18, minimized ? 8 : 16, minimized ? 8 : 18, minimized ? 8 : 16);
        updateTitle();
    }

    private void closeOverlay() {
        running = false;
        sending = false;
        handler.removeCallbacksAndMessages(null);
        stopSelf();
    }

    private void deactivateOverlay() {
        running = false;
        sending = false;
        handler.removeCallbacksAndMessages(null);
        Toast.makeText(this, "Menu flutuante desativado", Toast.LENGTH_SHORT).show();
        stopSelf();
    }

    private void toggleRunning() {
        if (sending) {
            return;
        }
        running = !running;
        playButton.setText(running ? "PARAR" : "AUTO");
        if (running) {
            handler.removeCallbacks(autoStep);
            handler.post(autoStep);
        } else {
            handler.removeCallbacks(autoStep);
        }
    }

    private boolean sendCurrent(boolean fromAuto) {
        if (sending) {
            return false;
        }
        String text = JJSText.gerar(current);
        boolean ok = JJSAccessibilityService.insertText(this, text);
        if (!ok) {
            Toast.makeText(this, "Toque no campo de texto e deixe o botao Enviar visivel", Toast.LENGTH_SHORT).show();
            if (fromAuto) {
                toggleRunning();
            }
            return false;
        }
        sending = true;
        updateTitle();

        handler.postDelayed(() -> {
            if (autoClickSend) {
                JJSAccessibilityService.clickSend(this);
            }
        }, SEND_CLICK_DELAY_MS);

        handler.postDelayed(() -> finishSend(fromAuto), ADVANCE_DELAY_MS);
        return true;
    }

    private void finishSend(boolean fromAuto) {
        boolean wasLast = current >= end;
        sending = false;
        if (wasLast) {
            running = false;
            playButton.setText("AUTO");
            handler.removeCallbacks(autoStep);
            Toast.makeText(this, "AUTO JJS finalizado", Toast.LENGTH_SHORT).show();
        } else {
            advance();
        }
        saveCurrent();
        updateTitle();
        if (fromAuto && running) {
            handler.postDelayed(autoStep, Math.max(MIN_INTERVAL_MS, (long) (interval * 1000)));
        }
    }

    private void advance() {
        current = current < end ? current + 1 : start;
    }

    private void previous() {
        current = current > start ? current - 1 : end;
    }

    private void adjustEndBy(int delta) {
        int max = JJSText.MAX_NUMBER;
        end = AutoJJSBridge.clamp(end + delta, start, max);
        if (current > end) {
            current = end;
        }
        AutoJJSBridge.prefs(this).edit()
            .putInt("end", end)
            .putInt("current", current)
            .apply();
        updateTitle();
    }

    private void applyEndInput() {
        try {
            int typedEnd = Integer.parseInt(endInput.getText().toString().trim());
            end = AutoJJSBridge.clamp(typedEnd, start, JJSText.MAX_NUMBER);
            if (current > end) {
                current = end;
            }
            AutoJJSBridge.prefs(this).edit()
                .putInt("end", end)
                .putInt("current", current)
                .apply();
            updateTitle();
            Toast.makeText(this, "Limite atualizado: " + end, Toast.LENGTH_SHORT).show();
            endInput.clearFocus();
        } catch (NumberFormatException ex) {
            Toast.makeText(this, "Digite um limite valido", Toast.LENGTH_SHORT).show();
            updateTitle();
        }
    }

    private void setOverlayFocusable(boolean shouldFocus) {
        if (params == null || windowManager == null || root == null || focusable == shouldFocus) {
            return;
        }
        focusable = shouldFocus;
        if (focusable) {
            params.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        } else {
            params.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        }
        try {
            windowManager.updateViewLayout(root, params);
        } catch (IllegalArgumentException ignored) {
        }
    }

    private void loadConfig() {
        SharedPreferences prefs = AutoJJSBridge.prefs(this);
        start = prefs.getInt("start", 1);
        end = prefs.getInt("end", 100);
        current = AutoJJSBridge.clamp(prefs.getInt("current", start), start, end);
        interval = Math.max(AutoJJSBridge.MIN_INTERVAL_SECONDS, prefs.getFloat("interval", 2.2f));
        autoClickSend = prefs.getBoolean("autoClickSend", true);
    }

    private void saveCurrent() {
        AutoJJSBridge.prefs(this).edit().putInt("current", current).apply();
    }

    private void updateTitle() {
        if (title != null) {
            title.setText((running ? "AUTO LIGADO  " : "AUTO JJS  ") + current + " / " + end);
        }
        if (miniButton != null) {
            miniButton.setText(running ? "AUTO" : "JJS");
        }
        if (preview != null) {
            preview.setText(JJSText.gerar(current));
        }
        if (endInput != null && !endInput.hasFocus()) {
            endInput.setText(String.valueOf(end));
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
