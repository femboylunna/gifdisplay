package dev.gifoverlay.screen;

import dev.gifoverlay.CapeManager;
import dev.gifoverlay.CapeSettings;
import dev.gifoverlay.DynTex;
import dev.gifoverlay.GifItem;
import dev.gifoverlay.GifManager;
import dev.gifoverlay.cape.CapeComposer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Pick which GIF is your cape and how it is fitted. Only you see it (it's client-side). */
public class CapeScreen extends Screen {
    private final Screen parent;

    public CapeScreen(Screen parent) {
        super(Component.literal("GIF Cape"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        CapeSettings c = GifManager.INSTANCE.cape;
        int cx = this.width / 2, y = 52, w = 200, gap = 24;

        this.addRenderableWidget(Button.builder(Component.literal("GIF cape: " + (c.enabled ? "ON" : "OFF")), b -> {
            if (!c.enabled && GifManager.INSTANCE.byId(c.gifId) == null) pickNext(c);
            c.enabled = !c.enabled && GifManager.INSTANCE.byId(c.gifId) != null;
            changed();
        }).bounds(cx - w / 2, y, w, 20).build());

        GifItem cur = GifManager.INSTANCE.byId(c.gifId);
        String gifLabel = GifManager.INSTANCE.items.isEmpty() ? "No GIFs yet - add one first"
                : "GIF: " + (cur == null ? "(click to choose)" : cur.name);
        this.addRenderableWidget(Button.builder(Component.literal(gifLabel), b -> {
            pickNext(c);
            changed();
        }).bounds(cx - w / 2, y + gap, w, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Fit: " + (c.cover ? "Crop to fit" : "Stretch")), b -> {
            c.cover = !c.cover;
            changed();
        }).bounds(cx - w / 2, y + gap * 2, w, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal(
                "Quality: " + c.scale + "x  (" + CapeComposer.textureWidth(c.scale) + "x" + CapeComposer.textureHeight(c.scale) + ")"), b -> {
            int idx = 0;
            for (int i = 0; i < CapeSettings.SCALES.length; i++) if (CapeSettings.SCALES[i] == c.scale) idx = i;
            c.scale = CapeSettings.SCALES[(idx + 1) % CapeSettings.SCALES.length];
            changed();
        }).bounds(cx - w / 2, y + gap * 3, w, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose())
                .bounds(cx - w / 2, this.height - 28, w, 20).build());
    }

    private void pickNext(CapeSettings c) {
        List<GifItem> ready = new ArrayList<>();
        for (GifItem i : GifManager.INSTANCE.items) if (i.data != null) ready.add(i);
        if (ready.isEmpty()) return;
        int idx = -1;
        for (int i = 0; i < ready.size(); i++) if (ready.get(i).id.equals(c.gifId)) idx = i;
        c.gifId = ready.get((idx + 1) % ready.size()).id;
    }

    private void changed() {
        CapeManager.rebuild();
        GifManager.INSTANCE.save();
        this.rebuildWidgets();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        String title = "GIF Cape";
        g.text(this.font, title, this.width / 2 - this.font.width(title) / 2, 30, 0xFFFFFFFF, true);

        // live preview of the cape's outer face
        CapeManager.update();
        DynTex t = CapeManager.texture();
        int cx = this.width / 2;
        int top = 52 + 24 * 4 + 6;
        int availH = this.height - 28 - 8 - top;
        if (t != null && availH > 40) {
            int s = GifManager.INSTANCE.cape.scale;
            int ph = Math.min(availH, 128);
            int pw = Math.round(ph * 10f / 16f);
            int px = cx - pw / 2;
            g.fill(px - 2, top - 2, px + pw + 2, top + ph + 2, 0xFF000000);
            g.blit(RenderPipelines.GUI_TEXTURED, t.id,
                    px, top,
                    1f * s, 1f * s,
                    pw, ph,
                    10 * s, 16 * s,
                    t.width, t.height,
                    0xFFFFFFFF);
            String hint = "Preview. See it on yourself in F5 / the inventory.";
            g.text(this.font, hint, cx - this.font.width(hint) / 2, top + ph + 6, 0xFFAAAAAA, true);
        } else if (t == null) {
            String hint = "Turn the cape ON and pick a GIF to see a preview.";
            g.text(this.font, hint, cx - this.font.width(hint) / 2, top + 10, 0xFFAAAAAA, true);
        }
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }
}
