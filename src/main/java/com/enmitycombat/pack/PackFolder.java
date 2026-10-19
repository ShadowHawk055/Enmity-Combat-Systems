package com.enmitycombat.pack;

import com.enmitycombat.EnmityCombat;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.FilePackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * TACZ-style content packs.
 *
 *   <minecraft folder>/enmitycombat/
 *       my_pack.zip              <- a zip with assets/ and/or data/ at its root
 *       another_pack/            <- or a plain folder with assets/ and/or data/
 *
 * Each pack is mounted as BOTH a resource pack (assets: player_animations, fx, lang, textures)
 * and a data pack (data: characters, skills, cutscenes, tags), always enabled, on client and dedicated server.
 * Re-scanned on /reload (data) and F3+T (assets). Names starting with "_" or "." are ignored (handy for disabling a pack).
 */
@Mod.EventBusSubscriber(modid = EnmityCombat.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class PackFolder {
    public static final String FOLDER = "enmitycombat";

    public static Path dir() { return FMLPaths.GAMEDIR.get().resolve(FOLDER); }

    /** Called once from the mod constructor: creates the folder and a README the first time. */
    public static void ensureCreated() {
        try {
            Path d = dir();
            Path legacy = FMLPaths.GAMEDIR.get().resolve("actioncombat");      // folder name before the rename to Enmity Combat Systems
            if (Files.isDirectory(legacy) && !Files.exists(d)) {
                Files.move(legacy, d);
                EnmityCombat.LOGGER.info("[Enmity] Moved old pack folder '{}' to '{}'", legacy, d);
            }
            Files.createDirectories(d);
            Path readme = d.resolve("README.txt");
            if (!Files.exists(readme)) {
                Files.write(readme, List.of(
                    "Enmity Combat Systems - content packs",
                    "=====================================",
                    "Drop packs in this folder. A pack is either a .zip or a folder that has 'assets' and/or 'data' at its top level:",
                    "",
                    "  my_pack.zip",
                    "    data/<namespace>/characters/<id>.json",
                    "    data/<namespace>/skills/<path>.json",
                    "    data/<namespace>/cutscenes/<id>.json",
                    "    assets/<namespace>/player_animations/<name>.json",
                    "    assets/<namespace>/fx/<name>.fx",
                    "    assets/<namespace>/lang/en_us.json",
                    "",
                    "Use your own <namespace> (lowercase, no spaces) so packs don't collide.",
                    "Reload data with /reload, reload assets with F3+T (or restart).",
                    "Prefix a pack's name with '_' to disable it.",
                    "Multiplayer: the SERVER needs the pack (data); every PLAYER needs it too (animations / effects).",
                    "Full guide: see CONTENT_GUIDE.md that came with the mod."));
            }
        } catch (IOException e) {
            EnmityCombat.LOGGER.warn("[Enmity] Could not create pack folder: {}", e.toString());
        }
    }

    @SubscribeEvent
    public static void onAddPackFinders(AddPackFindersEvent e) {
        PackType type = e.getPackType();
        if (type != PackType.CLIENT_RESOURCES && type != PackType.SERVER_DATA) return;
        e.addRepositorySource(out -> scan(type, out));
    }

    private static void scan(PackType type, Consumer<Pack> out) {
        Path dir = dir();
        if (!Files.isDirectory(dir)) return;
        int count = 0;
        try (Stream<Path> files = Files.list(dir)) {
            for (Path p : (Iterable<Path>) files.sorted()::iterator) {
                String name = p.getFileName().toString();
                if (name.startsWith("_") || name.startsWith(".")) continue;
                boolean isDir = Files.isDirectory(p);
                boolean isZip = Files.isRegularFile(p) && name.toLowerCase(Locale.ROOT).endsWith(".zip");
                if (isDir && !Files.isDirectory(p.resolve("assets")) && !Files.isDirectory(p.resolve("data"))) {
                    EnmityCombat.LOGGER.warn("[Enmity] Folder '{}' has no assets/ or data/ at its top level - skipped. (Did you unzip into an extra sub-folder?)", name);
                    continue;
                }
                if (!isDir && !isZip) continue;
                try {
                    Pack.ResourcesSupplier supplier = isDir
                            ? id -> new PathPackResources(id, p, false)
                            : id -> new FilePackResources(id, p.toFile(), false);
                    Pack pack = Pack.create("enmitycombat/" + name, Component.literal("[Enmity Combat] " + name), true, supplier,
                            new Pack.Info(Component.literal("Enmity Combat Systems content pack"),
                                    SharedConstants.getCurrentVersion().getPackVersion(type), FeatureFlagSet.of()),
                            type, Pack.Position.TOP, false, PackSource.BUILT_IN);
                    out.accept(pack);
                    count++;
                } catch (Exception ex) {
                    EnmityCombat.LOGGER.error("[Enmity] Failed to load pack '{}': {}", name, ex.toString());
                }
            }
        } catch (IOException ex) {
            EnmityCombat.LOGGER.error("[Enmity] Could not scan pack folder: {}", ex.toString());
        }
        EnmityCombat.LOGGER.info("[Enmity] Mounted {} content pack(s) as {} from {}", count, type, dir);
    }
    private PackFolder() {}
}
