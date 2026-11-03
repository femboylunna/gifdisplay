package dev.gifoverlay;

import dev.gifoverlay.gif.GifData;

/**
 * One GIF in your library. Position and size are stored as fractions of the GUI-scaled screen,
 * so layouts survive window resizes and GUI-scale changes.
 */
public final class GifItem {
    // ---- saved to JSON ----
    public String id;
    public String name;
    public String file;
    public float x = 0.05f, y = 0.10f, w = 0.20f, h = 0.20f;
    public float opacity = 1f;
    public boolean visible = true;

    // ---- runtime only ----
    public transient GifData data;
    public transient DynTex tex;
    public transient int shown = -1;

    public boolean ready() { return data != null && tex != null; }

    /** Height / width of the GIF itself (1 if not loaded yet). */
    public float aspect() { return data == null ? 1f : (float) data.height / data.width; }

    /** Uploads the right frame for this moment, if it changed. Render thread only. */
    public void tick(long nowMs) {
        if (!ready()) return;
        int f = data.frameAt(nowMs);
        if (f != shown) {
            tex.upload(data.frames[f]);
            shown = f;
        }
    }

    public int px(int guiW) { return Math.round(x * guiW); }
    public int py(int guiH) { return Math.round(y * guiH); }
    public int pw(int guiW) { return Math.max(1, Math.round(w * guiW)); }
    public int ph(int guiH) { return Math.max(1, Math.round(h * guiH)); }

    public boolean contains(double mx, double my, int guiW, int guiH) {
        return mx >= px(guiW) && mx < px(guiW) + pw(guiW) && my >= py(guiH) && my < py(guiH) + ph(guiH);
    }

    public void release() {
        if (tex != null) {
            tex.close();
            tex = null;
        }
        data = null;
        shown = -1;
    }
}
