package dev.gifoverlay.screen;

import com.mojang.blaze3d.platform.InputConstants;
import dev.gifoverlay.GifItem;
import dev.gifoverlay.GifManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Layout editor. The GIFs themselves are drawn by the normal HUD layer underneath, so this screen
 * deliberately draws no dimmed background - what you see is exactly what you get.
 *
 *   drag a GIF            move it
 *   drag a corner handle  resize (keeps shape; hold SHIFT to stretch freely)
 *   mouse wheel           resize the selected GIF
 *   arrow keys            nudge (hold SHIFT for 10px)
 *   DELETE                remove the selected GIF
 *   drop a .gif file      add it
 */
public class EditorScreen extends Screen {
    private enum Mode { NONE, MOVE, RESIZE }

    private static final int HANDLE = 6;

    private final List<Button> buttons = new ArrayList<>();
    private GifItem selected;
    private Mode mode = Mode.NONE;
    private double grabDx, grabDy;
    private float anchorX, anchorY; // fixed corner while resizing, in GUI pixels
    private boolean dirty;

    public EditorScreen() {
        super(Component.literal("GIF Overlay"));
    }

    @Override
    protected void init() {
        buttons.clear();
        int bw = 76, gap = 4, y = 6;
        int total = 7 * bw + 6 * gap;
        int x = Math.max(4, (this.width - total) / 2);

        x = add(x, bw, gap, y, "Add GIF", () -> this.minecraft.setScreen(new AddGifScreen(this)));
        x = add(x, bw, gap, y, "Cape...", () -> this.minecraft.setScreen(new CapeScreen(this)));
        x = add(x, bw, gap, y, "Show/Hide", () -> {
            if (selected != null) { selected.visible = !selected.visible; dirty = true; }
        });
        x = add(x, bw, gap, y, "Remove", () -> {
            if (selected != null) { GifManager.INSTANCE.remove(selected); selected = null; }
        });
        x = add(x, bw, gap, y, "Opacity -", () -> {
            if (selected != null) { selected.opacity = Math.max(0.1f, selected.opacity - 0.1f); dirty = true; }
        });
        x = add(x, bw, gap, y, "Opacity +", () -> {
            if (selected != null) { selected.opacity = Math.min(1f, selected.opacity + 0.1f); dirty = true; }
        });
        add(x, bw, gap, y, "Done", this::onClose);
    }

    private int add(int x, int w, int gap, int y, String label, Runnable action) {
        Button b = Button.builder(Component.literal(label), btn -> action.run()).bounds(x, y, w, 20).build();
        buttons.add(b);
        this.addRenderableWidget(b);
        return x + w + gap;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        // No super call on purpose: it would dim the world and hide the GIFs we're editing.
        g.fill(0, 0, this.width, 30, 0x99000000);
        for (Button b : buttons) b.extractRenderState(g, mouseX, mouseY, delta);

        for (GifItem it : GifManager.INSTANCE.items) {
            int x = it.px(width), y = it.py(height), w = it.pw(width), h = it.ph(height);
            boolean sel = it == selected;
            int c = sel ? 0xFFFFFFFF : (it.visible ? 0x88FFFFFF : 0x88FF5555);
            outline(g, x, y, w, h, c, sel ? 2 : 1);
            String label = it.name + (it.visible ? "" : " (hidden)") + (it.ready() ? "" : " (loading...)");
            g.text(this.font, label, x + 2, Math.max(32, y - 10), c, true);
            if (sel) {
                handle(g, x, y);
                handle(g, x + w, y);
                handle(g, x, y + h);
                handle(g, x + w, y + h);
            }
        }

        String hint = GifManager.INSTANCE.items.isEmpty()
                ? "No GIFs yet - click \"Add GIF\" (or drop a .gif file onto this window)."
                : "Drag to move  |  corners to resize (SHIFT = free)  |  wheel = scale  |  DEL = remove";
        g.text(this.font, hint, 6, 34, 0xFFDDDDDD, true);
        String status = GifManager.INSTANCE.status;
        if (status != null && !status.isEmpty()) g.text(this.font, status, 6, 46, 0xFFFFDD66, true);
    }

    private static void outline(GuiGraphicsExtractor g, int x, int y, int w, int h, int color, int t) {
        g.fill(x, y, x + w, y + t, color);
        g.fill(x, y + h - t, x + w, y + h, color);
        g.fill(x, y, x + t, y + h, color);
        g.fill(x + w - t, y, x + w, y + h, color);
    }

    private static void handle(GuiGraphicsExtractor g, int cx, int cy) {
        int r = HANDLE / 2;
        g.fill(cx - r, cy - r, cx + r, cy + r, 0xFFFFFFFF);
        g.fill(cx - r + 1, cy - r + 1, cx + r - 1, cy + r - 1, 0xFF3A7BFF);
    }

    // ------------------------------------------------------------------ mouse

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true; // a button took it
        if (event.button() != 0) return false;
        double mx = event.x(), my = event.y();

        if (selected != null) {
            int x = selected.px(width), y = selected.py(height), w = selected.pw(width), h = selected.ph(height);
            int corner = cornerAt(mx, my, x, y, w, h);
            if (corner >= 0) {
                // anchor = the corner opposite the one grabbed
                anchorX = (corner == 0 || corner == 2) ? x + w : x;
                anchorY = (corner == 0 || corner == 1) ? y + h : y;
                mode = Mode.RESIZE;
                return true;
            }
        }

        List<GifItem> items = GifManager.INSTANCE.items;
        for (int i = items.size() - 1; i >= 0; i--) {
            GifItem it = items.get(i);
            if (it.contains(mx, my, width, height)) {
                selected = it;
                items.remove(i);          // bring to front
                items.add(it);
                grabDx = mx - it.px(width);
                grabDy = my - it.py(height);
                mode = Mode.MOVE;
                return true;
            }
        }
        selected = null;
        mode = Mode.NONE;
        return true;
    }

    /** 0=top-left, 1=top-right, 2=bottom-left, 3=bottom-right, -1=none */
    private static int cornerAt(double mx, double my, int x, int y, int w, int h) {
        int r = HANDLE + 2;
        if (Math.abs(mx - x) <= r && Math.abs(my - y) <= r) return 0;
        if (Math.abs(mx - (x + w)) <= r && Math.abs(my - y) <= r) return 1;
        if (Math.abs(mx - x) <= r && Math.abs(my - (y + h)) <= r) return 2;
        if (Math.abs(mx - (x + w)) <= r && Math.abs(my - (y + h)) <= r) return 3;
        return -1;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (selected == null || mode == Mode.NONE) return super.mouseDragged(event, dx, dy);
        double mx = event.x(), my = event.y();

        if (mode == Mode.MOVE) {
            float w = selected.w, h = selected.h;
            selected.x = clamp((float) ((mx - grabDx) / width), -w + 0.02f, 1f - 0.02f);
            selected.y = clamp((float) ((my - grabDy) / height), -h + 0.02f, 1f - 0.02f);
            dirty = true;
            return true;
        }

        // RESIZE
        float nw = (float) Math.abs(mx - anchorX);
        float nh = (float) Math.abs(my - anchorY);
        if (!event.hasShiftDown()) {
            float asp = selected.aspect(); // height / width in pixels
            nw = Math.max(nw, nh / asp);
            nh = nw * asp;
        }
        nw = Math.max(nw, 16f);
        nh = Math.max(nh, 16f);
        float left = mx < anchorX ? anchorX - nw : anchorX;
        float top = my < anchorY ? anchorY - nh : anchorY;
        selected.x = left / width;
        selected.y = top / height;
        selected.w = nw / width;
        selected.h = nh / height;
        dirty = true;
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (mode != Mode.NONE) {
            mode = Mode.NONE;
            flush();
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (selected == null || scrollY == 0) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        float f = scrollY > 0 ? 1.06f : 1f / 1.06f;
        float cx = selected.x + selected.w / 2f, cy = selected.y + selected.h / 2f;
        float nw = selected.w * f, nh = selected.h * f;
        if (nw * width < 16 || nh * height < 16) return true;
        selected.w = nw;
        selected.h = nh;
        selected.x = cx - nw / 2f;
        selected.y = cy - nh / 2f;
        dirty = true;
        return true;
    }

    // ------------------------------------------------------------------ keyboard / files

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (selected != null) {
            int step = event.hasShiftDown() ? 10 : 1;
            int key = event.key();
            if (key == InputConstants.KEY_DELETE) {
                GifManager.INSTANCE.remove(selected);
                selected = null;
                return true;
            } else if (key == InputConstants.KEY_LEFT) { selected.x -= step / (float) width; dirty = true; return true; }
            else if (key == InputConstants.KEY_RIGHT) { selected.x += step / (float) width; dirty = true; return true; }
            else if (key == InputConstants.KEY_UP) { selected.y -= step / (float) height; dirty = true; return true; }
            else if (key == InputConstants.KEY_DOWN) { selected.y += step / (float) height; dirty = true; return true; }
        }
        return super.keyPressed(event);
    }

    @Override
    public void onFilesDrop(List<Path> paths) {
        for (Path p : paths) {
            String n = p.getFileName().toString().toLowerCase();
            if (n.endsWith(".gif")) GifManager.INSTANCE.addAsync(p.toString(), msg -> { });
        }
    }

    // ------------------------------------------------------------------ misc

    private void flush() {
        if (dirty) {
            GifManager.INSTANCE.save();
            dirty = false;
        }
    }

    @Override
    public void onClose() {
        flush();
        GifManager.INSTANCE.save();
        super.onClose();
    }

    private static float clamp(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }
}
