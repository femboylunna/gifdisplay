package dev.gifoverlay;

import com.mojang.blaze3d.platform.InputConstants;
import dev.gifoverlay.screen.EditorScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GifOverlayClient implements ClientModInitializer {
    public static final String MOD_ID = "gifoverlay";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static KeyMapping openKey;

    @Override
    public void onInitializeClient() {
        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
        openKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.gifoverlay.open",
                InputConstants.Type.KEYSYM,
                InputConstants.KEY_G,
                category));

        ClientLifecycleEvents.CLIENT_STARTED.register(client -> GifManager.INSTANCE.init());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> GifManager.INSTANCE.save());

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openKey.consumeClick()) {
                if (client.screen == null) client.setScreen(new EditorScreen());
            }
        });

        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath(MOD_ID, "gifs"),
                (graphics, deltaTracker) -> GifRenderer.render(graphics));
    }
}
