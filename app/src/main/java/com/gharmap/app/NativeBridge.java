package com.gharmap.app;

import android.app.Activity;

/** Lifecycle and GL methods are serialized on the rendering thread, except resume/pause
 * while that thread is suspended. C++ owns capture, geometry and SVG export. */
public final class NativeBridge {
    static { System.loadLibrary("gharmap"); }
    public static native long create();
    public static native void destroy(long handle);
    public static native String resume(long handle, Activity activity);
    public static native void pause(long handle);
    public static native void surfaceCreated(long handle);
    public static native void resize(long handle, int rotation, int width, int height);
    public static native String draw(long handle);
    public static native String markCorner(long handle, float x, float y);
    public static native void undo(long handle);
    public static native void clearCorners(long handle);
    public static native String autoOutline(long handle);
    public static native String polygon(long handle);
    public static native String startRecording(long handle, String path);
    public static native String stopRecording(long handle);
    public static native String validate(double[] xy);
    public static native String exportSvg(double[] xy, String label);
    private NativeBridge() {}
}
