package dev.gifoverlay.mixin;

import dev.gifoverlay.CapeManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Swaps the animated GIF cape into the LOCAL player's skin. Vanilla's cape layer then renders it
 * exactly like a normal cape (physics, elytra-less, "Cape" skin-customisation toggle, etc.).
 */
@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin {
    @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
    private void gifoverlay$gifCape(CallbackInfoReturnable<PlayerSkin> cir) {
        if (Minecraft.getInstance().player != (Object) this) return;
        PlayerSkin wrapped = CapeManager.wrap(cir.getReturnValue());
        if (wrapped != cir.getReturnValue()) cir.setReturnValue(wrapped);
    }
}
