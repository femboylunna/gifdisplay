package dev.gifoverlay.screen;

import dev.gifoverlay.GifManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.util.List;

/** Paste a direct .gif link or a file path (or drop a .gif file on the window). */
public class AddGifScreen extends Screen {
    private final Screen parent;
    private EditBox input;

    public AddGifScreen(Screen parent) {
        super(Component.literal("Add GIF"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cx = this.width / 2, cy = this.height / 2;
        input = new EditBox(this.font, cx - 150, cy - 10, 300, 20, Component.literal("GIF link or path"));
        input.setMaxLength(2048);
        input.setHint(Component.literal("https://.../cat.gif   or   C:\\Users\\you\\cat.gif"));
        this.addRenderableWidget(input);
        this.setInitialFocus(input);

        this.addRenderableWidget(Button.builder(Component.literal("Add"), b -> submit())
                .bounds(cx - 150, cy + 16, 146, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose())
                .bounds(cx + 4, cy + 16, 146, 20).build());
    }

    private void submit() {
        GifManager.INSTANCE.addAsync(input.getValue(), msg -> {
            if (msg.startsWith("Added")) this.minecraft.setScreen(parent);
        });
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        int cx = this.width / 2, cy = this.height / 2;
        centered(g, "Add a GIF", cx, cy - 56, 0xFFFFFFFF);
        centered(g, "Use a direct link ending in .gif (right-click the GIF > Open image in new tab > copy that URL),", cx, cy - 40, 0xFFAAAAAA);
        centered(g, "or paste a file path. You can also drop a .gif file onto the window.", cx, cy - 29, 0xFFAAAAAA);
        String status = GifManager.INSTANCE.status;
        if (status != null && !status.isEmpty()) centered(g, status, cx, cy + 46, 0xFFFFDD66);
    }

    private void centered(GuiGraphicsExtractor g, String s, int cx, int y, int color) {
        g.text(this.font, s, cx - this.font.width(s) / 2, y, color, true);
    }

    @Override
    public void onFilesDrop(List<Path> paths) {
        for (Path p : paths) {
            if (p.getFileName().toString().toLowerCase().endsWith(".gif")) {
                GifManager.INSTANCE.addAsync(p.toString(), msg -> {
                    if (msg.startsWith("Added")) this.minecraft.setScreen(parent);
                });
                return;
            }
        }
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }
}
