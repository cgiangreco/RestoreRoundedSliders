package io.github.cgiangreco.restoreroundedsliders;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.provider.Settings;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/** Optional gesture, isolated from the slider appearance hooks. */
final class StatusBarBrightness {
    static final String SETTING = "restoreroundedsliders_status_bar_brightness";
    private static final String STATE = SETTING + "_state";
    private static boolean failureLogged;

    private static boolean installed;

    static synchronized void install(ClassLoader loader) {
        if (installed) return;
        // Android 17 routes the gesture at the window, before PhoneStatusBarView.
        // StatusBarWindowView inherits dispatchTouchEvent, so hook its declaring
        // framework class and filter strictly to this one SystemUI window type.
        XposedHelpers.findAndHookMethod(
                android.view.ViewGroup.class,
                "dispatchTouchEvent", MotionEvent.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        View view = (View) param.thisObject;
                        String name = view.getClass().getName();
                        if (!name.equals("com.android.systemui.statusbar.window.StatusBarWindowView")) return;
                        MotionEvent event = (MotionEvent) param.args[0];
                        Gesture gesture = (Gesture) XposedHelpers.getAdditionalInstanceField(view, STATE);
                        try {
                            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                                if (gesture != null) gesture.clearPreview();
                                gesture = null;
                                if (Settings.Global.getInt(view.getContext().getContentResolver(),
                                        SETTING, 0) != 0 && event.getPointerCount() == 1
                                        && event.getX() >= 0 && event.getX() < view.getWidth()
                                        && event.getY() >= 0 && event.getY() < view.getHeight()) {
                                    gesture = new Gesture(view, event);
                                }
                                XposedHelpers.setAdditionalInstanceField(view, STATE, gesture);
                                return; // Let stock SystemUI receive DOWN until direction is known.
                            }
                            if (gesture == null) return;
                            int action = event.getActionMasked();
                            if (!gesture.active && (event.getPointerCount() != 1
                                    || action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP)) {
                                XposedHelpers.removeAdditionalInstanceField(view, STATE);
                                return;
                            }
                            if (action == MotionEvent.ACTION_MOVE && !gesture.active) {
                                float dx = Math.abs(event.getRawX() - gesture.startX);
                                float dy = Math.abs(event.getRawY() - gesture.startY);
                                if (dy > gesture.slop) {
                                    XposedHelpers.removeAdditionalInstanceField(view, STATE);
                                    return; // Vertical intent belongs to the shade for this entire stream.
                                }
                                if (dx <= gesture.slop * 2 || dx <= dy * 2) return;
                                gesture.prepare(); // Validate brightness access before taking over.
                                MotionEvent cancel = MotionEvent.obtain(event);
                                cancel.setAction(MotionEvent.ACTION_CANCEL);
                                try {
                                    XposedBridge.invokeOriginalMethod(param.method, view, new Object[]{cancel});
                                } finally {
                                    cancel.recycle();
                                }
                                gesture.active = true;
                            }
                            if (!gesture.active) return;
                            param.setResult(true);
                            if (event.getPointerCount() != 1 || action == MotionEvent.ACTION_CANCEL) {
                                gesture.clearPreview();
                                gesture.cancelled = true;
                            } else if (!gesture.cancelled && action == MotionEvent.ACTION_MOVE) {
                                gesture.update(event.getX());
                            } else if (action == MotionEvent.ACTION_UP) {
                                if (!gesture.cancelled) {
                                    gesture.update(event.getX());
                                    XposedHelpers.callMethod(gesture.manager, "setBrightness",
                                            gesture.displayId, gesture.value);
                                }
                                gesture.clearPreview();
                            }
                            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                                XposedHelpers.removeAdditionalInstanceField(view, STATE);
                            }
                        } catch (Throwable error) {
                            // Once stock receives CANCEL, consume the rest of this stream even on failure.
                            if (gesture != null && gesture.active) {
                                param.setResult(true);
                                gesture.cancelled = true;
                                try { gesture.clearPreview(); } catch (Throwable ignored) { }
                            } else {
                                XposedHelpers.removeAdditionalInstanceField(view, STATE);
                            }
                            if (!failureLogged) {
                                failureLogged = true;
                                XposedBridge.log("RestoreRoundedSliders: status bar brightness unavailable: " + error);
                            }
                        }
                    }
                });
        installed = true;
        XposedBridge.log("RestoreRoundedSliders: status bar window brightness hook installed");
    }

    private static final class Gesture {
        final View view;
        final float startX, startY, slop;
        boolean active, cancelled, preview;
        DisplayManager manager;
        Class<?> brightnessUtils;
        int displayId;
        float min, max, value;

        Gesture(View view, MotionEvent event) {
            this.view = view;
            startX = event.getRawX();
            startY = event.getRawY();
            slop = ViewConfiguration.get(view.getContext()).getScaledTouchSlop();
        }

        void prepare() {
            brightnessUtils = XposedHelpers.findClass(
                    "com.android.internal.display.BrightnessUtils", null);
            manager = (DisplayManager) view.getContext().getSystemService(Context.DISPLAY_SERVICE);
            displayId = view.getDisplay().getDisplayId();
            Object info = XposedHelpers.callMethod(view.getDisplay(), "getBrightnessInfo");
            min = XposedHelpers.getFloatField(info, "brightnessMinimum");
            max = Math.min(1f, XposedHelpers.getFloatField(info, "brightnessMaximum"));
            value = XposedHelpers.getFloatField(info, "brightness");
            if (!Float.isFinite(min) || !Float.isFinite(max) || !Float.isFinite(value)
                    || min < 0 || max <= min) throw new IllegalStateException("Invalid brightness range");
        }

        void update(float x) {
            // Match Android's perceptual slider scale: center = 50%, with
            // no endpoint margins shifting the other positions along the bar.
            float fraction = Math.max(0f, Math.min(1f,
                    x / Math.max(1, view.getWidth())));
            float linear = (Float) XposedHelpers.callStaticMethod(
                    brightnessUtils, "convertGammaToLinear", fraction);
            value = min + linear * (max - min);
            preview = true;
            XposedHelpers.callMethod(manager, "setTemporaryBrightness", displayId, value);
        }

        void clearPreview() {
            if (preview) {
                XposedHelpers.callMethod(manager, "setTemporaryBrightness", displayId, Float.NaN);
                preview = false;
            }
        }
    }
}
