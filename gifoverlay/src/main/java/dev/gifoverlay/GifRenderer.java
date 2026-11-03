package dev.gifoverlay;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;

/** Draws every visible GIF onto the HUD. */
public final class GifRenderer {
    private GifRenderer() {}

    public static void render(GuiGraphicsExtractor g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        Window win = mc.getWindow();
        int gw = win.getGuiScaledWidth(), gh = win.getGuiScaledHeight();
        long now = System.currentTimeMillis();

        for (GifItem it : GifManager.INSTANCE.items) {
            if (!it.visible || !it.ready()) continue;
            it.tick(now);

            int alpha = Math.max(0, Math.min(255, Math.round(it.opacity * 255f)));
            int color = (alpha << 24) | 0x00FFFFFF;
            g.blit(RenderPipelines.GUI_TEXTURED, it.tex.id,
                    it.px(gw), it.py(gh),
                    0f, 0f,
                    it.pw(gw), it.ph(gh),
                    it.tex.width, it.tex.height,
                    it.tex.width, it.tex.height,
                    color);
        }
    }
}
