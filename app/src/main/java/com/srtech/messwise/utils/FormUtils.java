package com.srtech.messwise.utils;

import android.content.Context;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewParent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ScrollView;
import androidx.core.widget.NestedScrollView;

public class FormUtils {

    /**
     * Registers a list of EditTexts for automatic smooth scrolling when they gain focus.
     * Also handles hiding the keyboard on "Done" action if specified.
     * @param views The EditText views to register.
     */
    public static void setupAutoScroll(View... views) {
        View.OnFocusChangeListener focusListener = (v, hasFocus) -> {
            if (hasFocus) {
                scrollToView(v);
            }
        };

        for (View view : views) {
            if (view != null) {
                view.setOnFocusChangeListener(focusListener);
                
                if (view instanceof EditText) {
                    EditText editText = (EditText) view;
                    // Handle "Done" action to hide keyboard
                    editText.setOnEditorActionListener((v, actionId, event) -> {
                        if (actionId == EditorInfo.IME_ACTION_DONE) {
                            hideKeyboard(v);
                            v.clearFocus();
                            return true;
                        }
                        return false;
                    });
                }
            }
        }
    }

    /**
     * Smoothly scrolls the parent ScrollView or NestedScrollView to make the view visible.
     * @param view The view to scroll to.
     */
    public static void scrollToView(View view) {
        if (view == null) return;

        view.postDelayed(() -> {
            View parentScroll = findParentScrollView(view);
            if (parentScroll != null) {
                // Calculate position relative to the ScrollView
                Rect rect = new Rect();
                view.getDrawingRect(rect);
                
                // Get absolute coordinates to calculate the offset within the scroll view
                int[] location = new int[2];
                view.getLocationInWindow(location);
                int viewY = location[1];
                
                int[] scrollLocation = new int[2];
                parentScroll.getLocationInWindow(scrollLocation);
                int scrollY = scrollLocation[1];

                // Target scroll position: current scroll + (view position - scroll view top) - a small margin
                int margin = view.getHeight(); // One height worth of margin for better context
                
                if (parentScroll instanceof NestedScrollView) {
                    NestedScrollView nsv = (NestedScrollView) parentScroll;
                    int targetY = nsv.getScrollY() + (viewY - scrollY) - margin;
                    nsv.smoothScrollTo(0, Math.max(0, targetY));
                } else if (parentScroll instanceof ScrollView) {
                    ScrollView sv = (ScrollView) parentScroll;
                    int targetY = sv.getScrollY() + (viewY - scrollY) - margin;
                    sv.smoothScrollTo(0, Math.max(0, targetY));
                }
            }
        }, 300); // 300ms to allow layout to settle after keyboard resize
    }

    private static View findParentScrollView(View view) {
        ViewParent parent = view.getParent();
        while (parent != null) {
            if (parent instanceof NestedScrollView || parent instanceof ScrollView) {
                return (View) parent;
            }
            parent = parent.getParent();
        }
        return null;
    }

    public static void hideKeyboard(View view) {
        if (view == null) return;
        InputMethodManager imm = (InputMethodManager) view.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }
}
