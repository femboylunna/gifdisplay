package dev.gifoverlay;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/** A registered, re-uploadable texture. Everything the mod draws goes through one of these. */
public final class DynTex implements AutoCloseable {
    public final Identifier id;
    public final int width;
    public final int height;
    private final DynamicTexture texture;

    public DynTex(String path, int width, int height) {
        this.width = width;
        this.height = height;
        this.id = Identifier.fromNamespaceAndPath(GifOverlayClient.MOD_ID, path);
        NativeImage image = new NativeImage(width, height, true);
        this.texture = new DynamicTexture(() -> "gifoverlay " + path, image);
        Minecraft.getInstance().getTextureManager().register(id, texture);
    }

    /** Must be called on the render thread. */
    public void upload(int[] argb) {
        NativeImage image = texture.getPixels();
        if (image == null) return;
        int i = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setPixel(x, y, argb[i++]);
            }
        }
        texture.upload();
    }

    @Override
    public void close() {
        Minecraft.getInstance().getTextureManager().release(id);
    }
}
