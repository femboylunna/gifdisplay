package dev.gifoverlay;

import dev.gifoverlay.cape.CapeComposer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerSkin;

/** Keeps the animated cape texture up to date and swaps it into the local player's skin. */
public final class CapeManager {
    private CapeManager() {}

    private static DynTex tex;
    private static int[] buffer;
    private static int shown = -1;

    // cache so we don't allocate a new PlayerSkin every call
    private static PlayerSkin cachedBase;
    private static PlayerSkin cachedResult;

    /** Call whenever the cape settings or the chosen GIF change. */
    public static void rebuild() {
        if (tex != null) {
            tex.close();
            tex = null;
        }
        buffer = null;
        shown = -1;
        cachedBase = null;
        cachedResult = null;

        CapeSettings c = GifManager.INSTANCE.cape;
        if (!c.enabled) return;
        GifItem item = GifManager.INSTANCE.byId(c.gifId);
        if (item == null || item.data == null) return;

        int w = CapeComposer.textureWidth(c.scale);
        int h = CapeComposer.textureHeight(c.scale);
        tex = new DynTex("cape/" + item.id + "_" + c.scale, w, h);
        buffer = new int[w * h];
    }

    /** The cape texture currently in use, or null if the GIF cape is off. */
    public static DynTex texture() { return tex; }

    /** Composes + uploads the right frame. Safe to call every frame; does nothing if the frame is unchanged. */
    public static void update() {
        if (tex == null) return;
        if (!Minecraft.getInstance().isSameThread()) return; // GPU uploads only on the render thread
        CapeSettings c = GifManager.INSTANCE.cape;
        GifItem item = GifManager.INSTANCE.byId(c.gifId);
        if (item == null || item.data == null) return;

        int f = item.data.frameAt(System.currentTimeMillis());
        if (f == shown) return;
        CapeComposer.compose(item.data.frames[f], item.data.width, item.data.height, c.scale, c.cover, buffer);
        tex.upload(buffer);
        shown = f;
    }

    /** Called from the mixin: returns the skin with our cape swapped in (or the original if the GIF cape is off). */
    public static PlayerSkin wrap(PlayerSkin base) {
        if (tex == null || base == null) return base;
        update();
        if (base == cachedBase && cachedResult != null) return cachedResult;
        Identifier id = tex.id;
        ClientAsset.ResourceTexture capeAsset = new ClientAsset.ResourceTexture(id, id);
        cachedBase = base;
        cachedResult = new PlayerSkin(base.body(), capeAsset, base.elytra(), base.model(), base.secure());
        return cachedResult;
    }
}
