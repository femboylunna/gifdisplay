package dev.gifoverlay;

/** Saved cape options. */
public final class CapeSettings {
    public boolean enabled = false;
    /** id of the GifItem used as the cape (null = none). */
    public String gifId = null;
    /** true = crop the GIF to the cape's shape, false = squash the whole GIF onto it. */
    public boolean cover = true;
    /** Texture resolution multiplier: texture is (64*scale) x (32*scale). */
    public int scale = 8;

    public static final int[] SCALES = {2, 4, 8, 12, 16};
}
