package dev.gifoverlay.cape;

/**
 * Turns one GIF frame into a vanilla-layout cape texture (64x32 units, scaled up by an integer
 * factor so HD capes work - the cape model uses normalised UVs, so any 2:1 texture size is fine).
 *
 * Vanilla cape layout (in 1/64 units):
 *   outer (visible) face : x=1,  y=1, 10x16
 *   inner (lining) face  : x=12, y=1, 10x16
 *   top edge             : x=1,  y=0, 10x1
 *   bottom edge          : x=11, y=0, 10x1
 *   right edge           : x=0,  y=1, 1x16
 *   left edge            : x=11, y=1, 1x16
 *
 * No Minecraft dependencies, so it can be tested on its own.
 */
public final class CapeComposer {
    private CapeComposer() {}

    public static final int BASE_W = 64;
    public static final int BASE_H = 32;
    private static final double CAPE_ASPECT = 10.0 / 16.0;

    public static int textureWidth(int scale) { return BASE_W * scale; }
    public static int textureHeight(int scale) { return BASE_H * scale; }

    /**
     * @param src   source frame ARGB
     * @param cover true = crop the GIF to the cape's aspect ratio, false = squash the whole GIF onto the cape
     * @param out   destination, length textureWidth(scale)*textureHeight(scale); fully overwritten
     */
    public static void compose(int[] src, int sw, int sh, int scale, boolean cover, int[] out) {
        final int tw = textureWidth(scale);
        java.util.Arrays.fill(out, 0);

        // crop window in source pixels
        double cx = 0, cy = 0, cw = sw, ch = sh;
        if (cover) {
            if ((double) sw / sh > CAPE_ASPECT) {
                cw = sh * CAPE_ASPECT;
                cx = (sw - cw) / 2.0;
            } else {
                ch = sw / CAPE_ASPECT;
                cy = (sh - ch) / 2.0;
            }
        }

        final int ox = 1 * scale, oy = 1 * scale, rw = 10 * scale, rh = 16 * scale;

        // outer face
        double fx = cw / rw, fy = ch / rh;
        int nx = clamp((int) Math.ceil(fx), 1, 4), ny = clamp((int) Math.ceil(fy), 1, 4);
        for (int py = 0; py < rh; py++) {
            for (int px = 0; px < rw; px++) {
                double sx0 = cx + px * fx, sy0 = cy + py * fy;
                out[(oy + py) * tw + (ox + px)] = boxSample(src, sw, sh, sx0, sy0, fx, fy, nx, ny);
            }
        }

        // inner face: horizontally mirrored copy of the outer face
        final int ix = 12 * scale;
        for (int py = 0; py < rh; py++) {
            for (int px = 0; px < rw; px++) {
                out[(oy + py) * tw + (ix + px)] = out[(oy + py) * tw + (ox + (rw - 1 - px))];
            }
        }

        // edges: extend the outer face's border pixels so the cape's thin sides match
        for (int px = 0; px < rw; px++) {
            int top = out[oy * tw + ox + px];
            int bottom = out[(oy + rh - 1) * tw + ox + px];
            for (int k = 0; k < scale; k++) {
                out[k * tw + ox + px] = top;                    // top edge   (x=1,y=0)
                out[k * tw + (11 * scale) + px] = bottom;       // bottom edge (x=11,y=0)
            }
        }
        for (int py = 0; py < rh; py++) {
            int left = out[(oy + py) * tw + ox];
            int right = out[(oy + py) * tw + ox + rw - 1];
            for (int k = 0; k < scale; k++) {
                out[(oy + py) * tw + k] = left;                       // right edge (x=0)
                out[(oy + py) * tw + (11 * scale) + k] = right;       // left edge  (x=11)
            }
        }
    }

    private static int boxSample(int[] src, int sw, int sh, double x0, double y0, double w, double h, int nx, int ny) {
        double a = 0, r = 0, g = 0, b = 0;
        for (int j = 0; j < ny; j++) {
            for (int i = 0; i < nx; i++) {
                int sx = clamp((int) Math.floor(x0 + (i + 0.5) * w / nx), 0, sw - 1);
                int sy = clamp((int) Math.floor(y0 + (j + 0.5) * h / ny), 0, sh - 1);
                int p = src[sy * sw + sx];
                double pa = (p >>> 24) & 0xFF;
                a += pa;
                r += ((p >> 16) & 0xFF) * pa;
                g += ((p >> 8) & 0xFF) * pa;
                b += (p & 0xFF) * pa;
            }
        }
        int n = nx * ny;
        if (a <= 0) return 0;
        int oa = (int) Math.round(a / n);
        int or = (int) Math.round(r / a), og = (int) Math.round(g / a), ob = (int) Math.round(b / a);
        return (oa << 24) | (or << 16) | (og << 8) | ob;
    }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
}
