package org.enzo.autojjs;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayDeque;
import java.util.Locale;

public class JJSAccessibilityService extends AccessibilityService {
    private static JJSAccessibilityService instance;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    public void onServiceConnected() {
        instance = this;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public boolean onUnbind(android.content.Intent intent) {
        instance = null;
        return super.onUnbind(intent);
    }

    public static boolean sendText(Context context, String text, boolean clickSend) {
        if (instance == null || text == null || text.length() == 0) {
            return false;
        }
        boolean inserted = instance.insertIntoFocusedField(text);
        if (inserted && clickSend) {
            instance.handler.postDelayed(() -> {
                if (!instance.clickLikelySendButton()) {
                    instance.pressImeEnter();
                }
            }, 220);
        }
        return inserted;
    }

    private boolean insertIntoFocusedField(String text) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            return false;
        }

        AccessibilityNodeInfo focus = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (focus == null || !focus.isEditable()) {
            focus = findEditableNode(root);
        }
        if (focus == null) {
            root.recycle();
            return false;
        }

        Bundle args = new Bundle();
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
        boolean ok = focus.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
        focus.recycle();
        root.recycle();
        return ok;
    }

    private AccessibilityNodeInfo findEditableNode(AccessibilityNodeInfo root) {
        ArrayDeque<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        queue.add(AccessibilityNodeInfo.obtain(root));

        while (!queue.isEmpty()) {
            AccessibilityNodeInfo node = queue.removeFirst();
            if (node.isEditable()) {
                return node;
            }
            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) {
                    queue.add(child);
                }
            }
            node.recycle();
        }
        return null;
    }

    private boolean clickLikelySendButton() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            return false;
        }

        AccessibilityNodeInfo target = findSendButton(root);
        boolean clicked = target != null && target.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        if (target != null) {
            target.recycle();
        }
        root.recycle();
        return clicked;
    }

    private boolean pressImeEnter() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            return false;
        }

        AccessibilityNodeInfo focus = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (focus == null || !focus.isEditable()) {
            focus = findEditableNode(root);
        }
        if (focus == null) {
            root.recycle();
            return false;
        }

        boolean ok = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            ok = focus.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.getId());
        }
        focus.recycle();
        root.recycle();
        return ok;
    }

    private AccessibilityNodeInfo findSendButton(AccessibilityNodeInfo root) {
        ArrayDeque<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        queue.add(AccessibilityNodeInfo.obtain(root));

        while (!queue.isEmpty()) {
            AccessibilityNodeInfo node = queue.removeFirst();
            if (node.isClickable() && looksLikeSend(node)) {
                return node;
            }
            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) {
                    queue.add(child);
                }
            }
            node.recycle();
        }
        return null;
    }

    private boolean looksLikeSend(AccessibilityNodeInfo node) {
        String text = valueOf(node.getText());
        String desc = valueOf(node.getContentDescription());
        return isSendLabel(text) || isSendLabel(desc);
    }

    private boolean isSendLabel(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return "send".equals(lower)
            || "enviar".equals(lower)
            || "mandar".equals(lower)
            || lower.contains("send message")
            || lower.contains("enviar mensagem")
            || lower.contains("mandar mensagem");
    }

    private String valueOf(CharSequence value) {
        return value == null ? "" : value.toString().trim();
    }
}
