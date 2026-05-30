package org.enzo.autojjs;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.os.Bundle;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayDeque;
import java.util.Locale;

public class JJSAccessibilityService extends AccessibilityService {
    private static JJSAccessibilityService instance;

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
            instance.clickLikelySendButton();
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

        CharSequence current = focus.getText();
        String next = current == null || current.length() == 0
            ? text
            : current.toString() + text;

        Bundle args = new Bundle();
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, next);
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
            || lower.contains("send message")
            || lower.contains("enviar mensagem");
    }

    private String valueOf(CharSequence value) {
        return value == null ? "" : value.toString().trim();
    }
}
