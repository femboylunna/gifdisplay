package dev.gifoverlay.gif;

/** A fully decoded, fully composited GIF: every frame is a complete ARGB image. */
public final class GifData {
    public final int width;
    public final int height;
    /** frames[i] is width*height ARGB pixels (non-premultiplied). */
    public final int[][] frames;
    public final int[] delaysMs;
    public final int totalMs;

    public GifData(int width, int height, int[][] frames, int[] delaysMs) {
        this.width = width;
        this.height = height;
        this.frames = frames;
        this.delaysMs = delaysMs;
        int sum = 0;
        for (int d : delaysMs) sum += d;
        this.totalMs = sum;
    }

    /** Which frame should be showing at the given time (loops forever). */
    public int frameAt(long timeMs) {
        if (frames.length <= 1 || totalMs <= 0) return 0;
        long t = Math.floorMod(timeMs, (long) totalMs);
        long acc = 0;
        for (int i = 0; i < delaysMs.length; i++) {
            acc += delaysMs[i];
            if (t < acc) return i;
        }
        return frames.length - 1;
    }
}
