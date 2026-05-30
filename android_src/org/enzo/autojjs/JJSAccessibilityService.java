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
        return inserted && (!clickSend || instance.clickLikelySendButton());
    }

    public static boolean insertText(Context context, String text) {
        if (instance == null || text == null || text.length() == 0) {
            return false;
        }
        return instance.insertIntoFocusedField(text);
    }

    public static boolean clickSend(Context context) {
        if (instance == null) {
            return false;
        }
        return instance.clickLikelySendButton();
    }

    private boolean insertIntoFocusedField(String text) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            return false;
        }

        AccessibilityNodeInfo focus = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (focus == null || !isUsableEditable(focus)) {
            if (focus != null) {
                focus.recycle();
            }
            focus = findSingleEditableNode(root);
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

    private AccessibilityNodeInfo findSingleEditableNode(AccessibilityNodeInfo root) {
        ArrayDeque<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        queue.add(AccessibilityNodeInfo.obtain(root));
        AccessibilityNodeInfo found = null;

        while (!queue.isEmpty()) {
            AccessibilityNodeInfo node = queue.removeFirst();
            if (isUsableEditable(node)) {
                if (found != null) {
                    found.recycle();
                    node.recycle();
                    recycleQueue(queue);
                    return null;
                }
                found = AccessibilityNodeInfo.obtain(node);
            }
            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) {
                    queue.add(child);
                }
            }
            node.recycle();
        }
        return found;
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
            if (node.isVisibleToUser() && node.isEnabled() && node.isClickable() && looksLikeSend(node)) {
                AccessibilityNodeInfo target = AccessibilityNodeInfo.obtain(node);
                node.recycle();
                recycleQueue(queue);
                return target;
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
        String viewId = valueOf(node.getViewIdResourceName());
        return isSendLabel(text) || isSendLabel(desc) || isSendId(viewId);
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

    private boolean isSendId(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.endsWith("/send")
            || lower.endsWith("/send_button")
            || lower.endsWith("/button_send")
            || lower.contains(":id/send");
    }

    private boolean isUsableEditable(AccessibilityNodeInfo node) {
        return node != null && node.isEditable() && node.isVisibleToUser() && node.isEnabled();
    }

    private void recycleQueue(ArrayDeque<AccessibilityNodeInfo> queue) {
        while (!queue.isEmpty()) {
            queue.removeFirst().recycle();
        }
    }

    private String valueOf(CharSequence value) {
        return value == null ? "" : value.toString().trim();
    }
}
