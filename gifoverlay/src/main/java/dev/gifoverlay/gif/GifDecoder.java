package dev.gifoverlay.gif;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;

/**
 * Pure-Java GIF decoder (ImageIO based, no Minecraft or third-party dependencies).
 * Handles frame offsets, transparency and all three disposal methods, and
 * downscales / thins out huge GIFs so they don't eat all your RAM.
 */
public final class GifDecoder {
    private GifDecoder() {}

    public static boolean looksLikeGif(byte[] b) {
        return b != null && b.length > 6 && b[0] == 'G' && b[1] == 'I' && b[2] == 'F' && b[3] == '8';
    }

    public static GifData decode(byte[] bytes, int maxDimension, int maxFrames) throws IOException {
        if (!looksLikeGif(bytes)) throw new IOException("That is not a GIF file (it may be a web page - use a direct .gif link)");

        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
        if (!readers.hasNext()) throw new IOException("No GIF reader available in this Java runtime");
        ImageReader reader = readers.next();

        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            reader.setInput(in, false, false);
            int count = reader.getNumImages(true);
            if (count <= 0) throw new IOException("GIF has no frames");

            int[] logical = logicalScreenSize(reader);
            int sw = logical[0], sh = logical[1];
            if (sw <= 0 || sh <= 0) {
                BufferedImage first = reader.read(0);
                sw = first.getWidth();
                sh = first.getHeight();
            }

            double scale = Math.min(1.0, (double) maxDimension / Math.max(sw, sh));
            int ow = Math.max(1, (int) Math.round(sw * scale));
            int oh = Math.max(1, (int) Math.round(sh * scale));

            int step = Math.max(1, (int) Math.ceil(count / (double) Math.max(1, maxFrames)));
            int keptMax = (count + step - 1) / step;
            int[][] frames = new int[keptMax][];
            int[] delays = new int[keptMax];
            int kept = 0;

            BufferedImage canvas = new BufferedImage(sw, sh, BufferedImage.TYPE_INT_ARGB);
            BufferedImage scaled = scale < 1.0 ? new BufferedImage(ow, oh, BufferedImage.TYPE_INT_ARGB) : null;

            for (int i = 0; i < count; i++) {
                BufferedImage img = reader.read(i);
                FrameMeta m = frameMeta(reader.getImageMetadata(i));

                int[] backup = null;
                if (m.disposal.equals("restoreToPrevious")) {
                    backup = ((DataBufferInt) canvas.getRaster().getDataBuffer()).getData().clone();
                }

                Graphics2D g = canvas.createGraphics();
                g.setComposite(AlphaComposite.SrcOver);
                g.drawImage(img, m.left, m.top, null);
                g.dispose();

                if (i % step == 0) {
                    frames[kept] = snapshot(canvas, scaled, ow, oh);
                    delays[kept] = m.delayMs;
                    kept++;
                } else if (kept > 0) {
                    delays[kept - 1] += m.delayMs; // thinned-out frame: keep total duration
                }

                switch (m.disposal) {
                    case "restoreToBackgroundColor" -> {
                        Graphics2D c = canvas.createGraphics();
                        c.setComposite(AlphaComposite.Clear);
                        c.fillRect(m.left, m.top, img.getWidth(), img.getHeight());
                        c.dispose();
                    }
                    case "restoreToPrevious" -> {
                        if (backup != null) {
                            int[] data = ((DataBufferInt) canvas.getRaster().getDataBuffer()).getData();
                            System.arraycopy(backup, 0, data, 0, data.length);
                        }
                    }
                    default -> { }
                }
            }

            if (kept < frames.length) {
                frames = java.util.Arrays.copyOf(frames, kept);
                delays = java.util.Arrays.copyOf(delays, kept);
            }
            return new GifData(ow, oh, frames, delays);
        } finally {
            reader.dispose();
        }
    }

    private static int[] snapshot(BufferedImage canvas, BufferedImage scaled, int ow, int oh) {
        if (scaled == null) {
            return ((DataBufferInt) canvas.getRaster().getDataBuffer()).getData().clone();
        }
        Graphics2D g = scaled.createGraphics();
        g.setComposite(AlphaComposite.Src);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(canvas, 0, 0, ow, oh, null);
        g.dispose();
        return ((DataBufferInt) scaled.getRaster().getDataBuffer()).getData().clone();
    }

    private record FrameMeta(int left, int top, String disposal, int delayMs) {}

    private static FrameMeta frameMeta(IIOMetadata md) {
        int left = 0, top = 0, delayCs = 10;
        String disposal = "none";
        Node root = md.getAsTree("javax_imageio_gif_image_1.0");
        for (Node n = root.getFirstChild(); n != null; n = n.getNextSibling()) {
            NamedNodeMap a = n.getAttributes();
            if (a == null) continue;
            switch (n.getNodeName()) {
                case "ImageDescriptor" -> {
                    left = intAttr(a, "imageLeftPosition", 0);
                    top = intAttr(a, "imageTopPosition", 0);
                }
                case "GraphicControlExtension" -> {
                    delayCs = intAttr(a, "delayTime", 10);
                    Node d = a.getNamedItem("disposalMethod");
                    if (d != null) disposal = d.getNodeValue();
                }
                default -> { }
            }
        }
        int delayMs = delayCs * 10;
        if (delayMs <= 10) delayMs = 100; // browsers treat 0/1cs delays as 100ms
        return new FrameMeta(left, top, disposal, delayMs);
    }

    private static int[] logicalScreenSize(ImageReader reader) {
        try {
            IIOMetadata sm = reader.getStreamMetadata();
            if (sm == null) return new int[]{0, 0};
            Node root = sm.getAsTree("javax_imageio_gif_stream_1.0");
            for (Node n = root.getFirstChild(); n != null; n = n.getNextSibling()) {
                if ("LogicalScreenDescriptor".equals(n.getNodeName())) {
                    NamedNodeMap a = n.getAttributes();
                    return new int[]{intAttr(a, "logicalScreenWidth", 0), intAttr(a, "logicalScreenHeight", 0)};
                }
            }
        } catch (Exception ignored) { }
        return new int[]{0, 0};
    }

    private static int intAttr(NamedNodeMap a, String name, int def) {
        Node n = a.getNamedItem(name);
        if (n == null) return def;
        try {
            return Integer.parseInt(n.getNodeValue());
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
