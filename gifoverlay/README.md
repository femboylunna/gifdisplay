# GIF Overlay & Cape (Fabric, Minecraft 26.1.2, client-side)

Put animated GIFs on your screen, drag and resize them, and wear one as your cape.
Original code (not based on MCGifs, which is All Rights Reserved).

## Build

Needs **JDK 25**.

```
gradle wrapper --gradle-version 9.4.0     # once, creates gradlew + the wrapper jar
./gradlew build                           # jar ends up in build/libs/gifoverlay-1.0.0.jar
./gradlew runClient                       # test in a dev client
```

Or just open the folder in IntelliJ IDEA (Gradle import) and run `Minecraft Client`.

Install: drop the jar into `mods/` with Fabric Loader 0.19.2+ and Fabric API for 26.1.2.

## Use

Press **G** (rebind in Controls > "GIF Overlay") to open the editor.

| Action | How |
|---|---|
| Add a GIF | **Add GIF** button: paste a direct `.gif` URL or a file path. Or drop a `.gif` file on the window. |
| Move | Drag the GIF |
| Resize | Drag a corner handle (keeps shape; hold **Shift** to stretch freely), or mouse wheel |
| Nudge | Arrow keys (Shift = 10px) |
| Hide / opacity / remove | Buttons on the top bar, or **Delete** |
| Cape | **Cape...** button: turn on, pick a GIF, choose crop/stretch and quality |

Direct links: web pages from Tenor/Giphy won't work. Right-click the GIF > "Open image in new tab" and copy that URL.

Everything is saved in `.minecraft/config/gifoverlay/` (`state.json` + a `gifs/` folder).

## Notes

- The cape is **client-side**: only you see it. It shows in third person (F5) and the inventory, and follows
  the normal "Cape" skin-customisation toggle. It does not change the elytra texture.
- Quality = cape texture size (2x..16x of 64x32). 8x (512x256) is a good default.
- Large GIFs are downscaled to 480px and capped at 500 frames when loaded, to protect RAM.

## If it doesn't compile / crashes on launch

This was written against the 26.1.2 docs and ported-mod sources but **could not be compiled against the real
game jars in the environment it was written in**. The GIF decoder and cape composer are pure Java and were tested.
The Minecraft API calls are where a name could be off. The only spots that touch version-specific API:

- `DynTex.java` - `DynamicTexture(Supplier<String>, NativeImage)`, `NativeImage.setPixel(x, y, argb)`, `TextureManager.release`
- `GifRenderer.java` / `CapeScreen.java` - the 13-argument `GuiGraphicsExtractor.blit(...)` with a colour
- `CapeManager.java` + `mixin/AbstractClientPlayerMixin.java` - `PlayerSkin` constructor, `ClientAsset.ResourceTexture`, `AbstractClientPlayer#getSkin`
- `screen/*.java` - `MouseButtonEvent` / `KeyEvent` method names (`x()`, `y()`, `button()`, `key()`, `hasShiftDown()`)

If the mixin is the problem and you only want the screen GIFs, remove `"AbstractClientPlayerMixin"` from
`src/main/resources/gifoverlay.mixins.json` and delete the cape code paths.
