package dev.gifoverlay;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.gifoverlay.gif.GifData;
import dev.gifoverlay.gif.GifDecoder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

/** Owns the GIF library: loading, saving, downloading, adding and removing. Main-thread state, worker-thread decoding. */
public final class GifManager {
    public static final GifManager INSTANCE = new GifManager();

    /** GIFs bigger than this are scaled down on load (keeps memory sane). */
    private static final int MAX_DIMENSION = 480;
    private static final int MAX_FRAMES = 500;
    private static final int MAX_DOWNLOAD_BYTES = 40 * 1024 * 1024;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public final List<GifItem> items = new ArrayList<>();
    public CapeSettings cape = new CapeSettings();
    /** Last message for the UI (set on the main thread). */
    public volatile String status = "";

    private Path dir, gifsDir, stateFile;
    private boolean ready;
    private final ExecutorService pool = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "gifoverlay-worker");
        t.setDaemon(true);
        return t;
    });
    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static final class SaveFile {
        List<GifItem> items = new ArrayList<>();
        CapeSettings cape = new CapeSettings();
    }

    private GifManager() {}

    // ------------------------------------------------------------------ lifecycle

    public void init() {
        if (ready) return;
        dir = FabricLoader.getInstance().getConfigDir().resolve("gifoverlay");
        gifsDir = dir.resolve("gifs");
        stateFile = dir.resolve("state.json");
        try {
            Files.createDirectories(gifsDir);
        } catch (IOException e) {
            GifOverlayClient.LOGGER.error("Could not create {}", gifsDir, e);
        }
        ready = true;

        if (Files.exists(stateFile)) {
            try {
                SaveFile s = GSON.fromJson(Files.readString(stateFile), SaveFile.class);
                if (s != null) {
                    if (s.items != null) items.addAll(s.items);
                    if (s.cape != null) cape = s.cape;
                }
            } catch (Exception e) {
                GifOverlayClient.LOGGER.error("Could not read {}", stateFile, e);
            }
        }
        for (GifItem item : new ArrayList<>(items)) {
            pool.execute(() -> {
                try {
                    GifData d = GifDecoder.decode(Files.readAllBytes(gifsDir.resolve(item.file)), MAX_DIMENSION, MAX_FRAMES);
                    onMain(() -> attach(item, d));
                } catch (Exception e) {
                    GifOverlayClient.LOGGER.warn("Could not load GIF {} ({})", item.name, e.toString());
                }
            });
        }
    }

    public void save() {
        if (!ready) return;
        try {
            SaveFile s = new SaveFile();
            s.items = items;
            s.cape = cape;
            Files.writeString(stateFile, GSON.toJson(s));
        } catch (IOException e) {
            GifOverlayClient.LOGGER.error("Could not save {}", stateFile, e);
        }
    }

    // ------------------------------------------------------------------ adding / removing

    /** Adds a GIF from a direct URL or a local file path. Safe to call from the main thread. */
    public void addAsync(String source, Consumer<String> done) {
        final String src = source == null ? "" : source.trim().replaceAll("^\"|\"$", "");
        if (src.isEmpty()) {
            done.accept("Paste a direct .gif link or a file path first.");
            return;
        }
        setStatus("Loading...");
        pool.execute(() -> {
            try {
                byte[] bytes;
                String name;
                if (src.regionMatches(true, 0, "http://", 0, 7) || src.regionMatches(true, 0, "https://", 0, 8)) {
                    HttpRequest req = HttpRequest.newBuilder(URI.create(src))
                            .timeout(Duration.ofSeconds(30))
                            .header("User-Agent", "Mozilla/5.0 (compatible; GifOverlayMod)")
                            .GET().build();
                    HttpResponse<byte[]> resp = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
                    if (resp.statusCode() / 100 != 2) throw new IOException("Server answered HTTP " + resp.statusCode());
                    bytes = resp.body();
                    if (bytes.length > MAX_DOWNLOAD_BYTES) throw new IOException("File is larger than 40 MB");
                    name = nameFromPath(URI.create(src).getPath());
                } else {
                    Path p = Path.of(src);
                    bytes = Files.readAllBytes(p);
                    name = nameFromPath(p.getFileName().toString());
                }
                GifData data = GifDecoder.decode(bytes, MAX_DIMENSION, MAX_FRAMES); // also validates it

                String id = "g" + Long.toHexString(ThreadLocalRandom.current().nextLong() & 0xFFFFFFFFFFFFL);
                String file = id + ".gif";
                Files.write(gifsDir.resolve(file), bytes);

                GifItem item = new GifItem();
                item.id = id;
                item.name = name;
                item.file = file;
                onMain(() -> {
                    placeNew(item, data);
                    items.add(item);
                    attach(item, data);
                    save();
                    setStatus("Added \"" + item.name + "\" (" + data.frames.length + " frames)");
                    done.accept(status);
                });
            } catch (Exception e) {
                String msg = e.getMessage() == null ? e.toString() : e.getMessage();
                onMain(() -> {
                    setStatus("Failed: " + msg);
                    done.accept(status);
                });
            }
        });
    }

    public void remove(GifItem item) {
        if (item == null) return;
        item.release();
        items.remove(item);
        try {
            Files.deleteIfExists(gifsDir.resolve(item.file));
        } catch (IOException ignored) { }
        if (item.id.equals(cape.gifId)) {
            cape.gifId = null;
            cape.enabled = false;
        }
        CapeManager.rebuild();
        save();
    }

    public GifItem byId(String id) {
        if (id == null) return null;
        for (GifItem i : items) if (id.equals(i.id)) return i;
        return null;
    }

    // ------------------------------------------------------------------ helpers

    private void attach(GifItem item, GifData data) {
        item.release();
        item.data = data;
        item.tex = new DynTex("gif/" + item.id, data.width, data.height);
        item.shown = -1;
        if (item.id.equals(cape.gifId)) CapeManager.rebuild();
    }

    /** Gives a freshly added GIF a sensible starting rectangle, keeping its real aspect ratio. */
    private void placeNew(GifItem item, GifData data) {
        Minecraft mc = Minecraft.getInstance();
        int gw = mc.getWindow().getGuiScaledWidth();
        int gh = mc.getWindow().getGuiScaledHeight();
        float wpx = gw * 0.22f;
        float hpx = wpx * data.height / data.width;
        if (hpx > gh * 0.5f) {
            hpx = gh * 0.5f;
            wpx = hpx * data.width / data.height;
        }
        int n = items.size() % 6;
        item.w = wpx / gw;
        item.h = hpx / gh;
        item.x = 0.04f + n * 0.03f;
        item.y = 0.12f + n * 0.03f;
    }

    private static String nameFromPath(String path) {
        if (path == null || path.isBlank()) return "GIF";
        String n = path.substring(path.lastIndexOf('/') + 1);
        n = n.substring(n.lastIndexOf('\\') + 1);
        if (n.toLowerCase(Locale.ROOT).endsWith(".gif")) n = n.substring(0, n.length() - 4);
        return n.isBlank() ? "GIF" : (n.length() > 24 ? n.substring(0, 24) : n);
    }

    private void setStatus(String s) { status = s; }

    private static void onMain(Runnable r) { Minecraft.getInstance().execute(r); }
}
