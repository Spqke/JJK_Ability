package me.pezzo.abilityPlugin.config;

import me.pezzo.abilityPlugin.AbilityPlugin;
import me.pezzo.abilityPlugin.config.data.AbilityData;
import me.pezzo.abilityPlugin.config.data.ability.BlackholeData;
import me.pezzo.abilityPlugin.config.data.ability.DashData;
import me.pezzo.abilityPlugin.config.data.ability.LeechFieldData;
import me.pezzo.abilityPlugin.config.data.ability.BluHollowData;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.*;

public class AbilityConfig {
    private final AbilityPlugin plugin;
    private final File abilitiesFolder;
    private final Map<String, AbilityData> abilities = new HashMap<>();
    private final Map<String, YamlConfiguration> rawConfigs = new HashMap<>();
    private final Map<String, String> fileHashes = new HashMap<>();
    private static final List<String> EXPECTED_DEFAULTS = List.of("dash", "blackhole", "leechfield", "bluhollow");

    public AbilityConfig(AbilityPlugin plugin) {
        this.plugin = plugin;
        this.abilitiesFolder = new File(plugin.getDataFolder(), "abilities");
        if (!abilitiesFolder.exists()) abilitiesFolder.mkdirs();
        migrateFromPluginsFolder();
        loadAbilities();
    }

    private void migrateFromPluginsFolder() {
        File pluginsDir = plugin.getDataFolder().getParentFile();
        if (pluginsDir == null || !pluginsDir.exists()) return;
        for (String fname : EXPECTED_DEFAULTS) {
            String fnameYml = fname + ".yml";
            File src = new File(pluginsDir, fnameYml);
            File dest = new File(abilitiesFolder, fnameYml);
            if (src.exists() && !dest.exists()) {
                try {
                    Files.move(src.toPath(), dest.toPath());
                    plugin.getLogger().info("Migrato " + fnameYml + " da " + pluginsDir.getAbsolutePath() + " a " + abilitiesFolder.getAbsolutePath());
                } catch (IOException e) {
                    plugin.getLogger().warning("Impossibile migrare " + fnameYml + ": " + e.getMessage());
                }
            }
        }
    }

    private String normalizeKey(String raw) {
        if (raw == null) return "";
        return raw.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private void ensureDefaultFilesExists() {
        for (String base : EXPECTED_DEFAULTS) {
            File f = new File(abilitiesFolder, base + ".yml");
            if (!f.exists()) {
                switch (base) {
                    case "dash" -> createDefaultDashConfig(f);
                    case "blackhole" -> createDefaultBlackholeConfig(f);
                    case "leechfield" -> createDefaultLeechFieldConfig(f);
                    case "bluhollow" -> createDefaultBluHollowConfig(f);
                    default -> { }
                }
            }
        }
    }

    private synchronized void loadAbilities() {
        abilities.clear();
        rawConfigs.clear();
        fileHashes.clear();
        ensureDefaultFilesExists();
        File[] files = abilitiesFolder.listFiles((d, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null || files.length == 0) return;
        for (File f : files) {
            String base = f.getName().toLowerCase(Locale.ROOT);
            if (base.endsWith(".yml")) base = base.substring(0, base.length() - 4);
            String key = normalizeKey(base);
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(f);
            rawConfigs.put(key, cfg);
            fileHashes.put(key, computeHash(f));
            try {
                switch (key) {
                    case "dash" -> {
                        DashData dashData = new DashData(
                                cfg.getString("name", "&4Dash"),
                                cfg.getString("lore", "&fAbilita per scattare in avanti"),
                                parseMaterial(cfg.getString("item", "FEATHER"), Material.FEATHER),
                                cfg.getDouble("boost", 2.0),
                                cfg.getLong("cooldown", 5000)
                        );
                        abilities.put(key, dashData);
                    }
                    case "blackhole" -> {
                        BlackholeData blackholeData = new BlackholeData(
                                cfg.getString("name", "&4Bagliore Rosso"),
                                cfg.getString("lore", "&cBagliore rosso. Attira e danneggia i nemici."),
                                parseMaterial(cfg.getString("item", "RED_DYE"), Material.RED_DYE),
                                cfg.getDouble("damage", 4.0),
                                cfg.getDouble("range", 20.0),
                                cfg.getLong("cooldown", 180000)
                        );
                        abilities.put(key, blackholeData);
                    }
                    case "leechfield" -> {
                        LeechFieldData leechData = new LeechFieldData(
                                cfg.getString("name", "&2Leech Field"),
                                cfg.getString("lore", "&7Sottrai vita ai nemici e rigenerati"),
                                parseMaterial(cfg.getString("item", "SPIDER_EYE"), Material.SPIDER_EYE),
                                cfg.getDouble("radius", 6.0),
                                cfg.getDouble("damage_per_tick", 1.5),
                                cfg.getInt("tick_interval", 20),
                                cfg.getInt("duration_ticks", 200),
                                cfg.getDouble("knockback_reduce", 0.0),
                                cfg.getInt("slowness_level", 1),
                                cfg.getLong("cooldown", 45000)
                        );
                        abilities.put(key, leechData);
                    }
                    case "bluhollow" -> {
                        BluHollowData blu = new BluHollowData(
                                cfg.getString("name", "&9Bagliore Blu"),
                                cfg.getString("lore", "&bBagliore blu. Una palla distruttiva che avanza dove guardi."),
                                parseMaterial(cfg.getString("item", "BLUE_DYE"), Material.BLUE_DYE),
                                cfg.getDouble("damage", 8.0),
                                cfg.getDouble("radius", 4.0),
                                cfg.getDouble("speed", 0.8),
                                cfg.getInt("duration_ticks", 120),
                                cfg.getBoolean("destroy_blocks", true),
                                cfg.getDouble("anchor_distance", 7.0), // nuovo parametro configurabile
                                cfg.getLong("cooldown", 240000)
                        );
                        abilities.put(key, blu);
                    }
                    default -> plugin.getLogger().info("File abilità non riconosciuto (ignorato): " + f.getName());
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Errore caricando " + f.getName() + ": " + e.getMessage());
            }
        }
    }

    private Material parseMaterial(String name, Material fallback) {
        if (name == null) return fallback;
        try {
            return Material.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            plugin.getLogger().warning("Material non valido in config: '" + name + "'. Uso fallback " + fallback.name());
            return fallback;
        }
    }

    private void createDefaultDashConfig(File file) {
        if (file.exists()) return;
        YamlConfiguration config = new YamlConfiguration();
        config.set("name", "&4Dash");
        config.set("lore", "&fAbilita per scattare in avanti");
        config.set("item", "FEATHER");
        config.set("boost", 4.0);
        config.set("cooldown", 4000);
        try { config.save(file); } catch (IOException e) { plugin.getLogger().severe("Impossibile creare il file dash.yml: " + e.getMessage()); }
    }

    private void createDefaultBlackholeConfig(File file) {
        if (file.exists()) return;
        YamlConfiguration config = new YamlConfiguration();
        config.set("name", "&4Bagliore Rosso");
        config.set("lore", "&cBagliore rosso. Attira e danneggia i nemici.");
        config.set("item", "RED_DYE");
        config.set("damage", 4.0);
        config.set("range", 20.0);
        config.set("cooldown", 180000);
        try { config.save(file); } catch (IOException e) { plugin.getLogger().severe("Impossibile creare il file blackhole.yml: " + e.getMessage()); }
    }

    private void createDefaultLeechFieldConfig(File file) {
        if (file.exists()) return;
        YamlConfiguration config = new YamlConfiguration();
        config.set("name", "&2Leech Field");
        config.set("lore", "&7Sottrai vita ai nemici e rigenerati");
        config.set("item", "SPIDER_EYE");
        config.set("radius", 6.0);
        config.set("damage_per_tick", 1.5);
        config.set("tick_interval", 20);
        config.set("duration_ticks", 200);
        config.set("knockback_reduce", 0.0);
        config.set("slowness_level", 1);
        config.set("cooldown", 45000);
        try { config.save(file); } catch (IOException e) { plugin.getLogger().severe("Impossibile creare il file leechfield.yml: " + e.getMessage()); }
    }

    private void createDefaultBluHollowConfig(File file) {
        if (file.exists()) return;
        YamlConfiguration config = new YamlConfiguration();
        config.set("name", "&9Bagliore Blu");
        config.set("lore", "&bBagliore blu. Una palla distruttiva che avanza dove guardi.");
        config.set("item", "BLUE_DYE");
        config.set("damage", 8.0);
        config.set("radius", 4.0);
        config.set("speed", 0.8);
        config.set("duration_ticks", 120);
        config.set("destroy_blocks", true);
        config.set("anchor_distance", 7.0); // default aggiunto
        config.set("cooldown", 240000);
        try { config.save(file); } catch (IOException e) { plugin.getLogger().severe("Impossibile creare il file bluhollow.yml: " + e.getMessage()); }
    }

    public synchronized ReloadResult reload() {
        plugin.getLogger().info("Ricaricamento configurazioni abilità in corso...");
        migrateFromPluginsFolder();
        File[] files = abilitiesFolder.listFiles((d, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        Map<String, File> currentFiles = new HashMap<>();
        if (files != null) {
            for (File f : files) {
                String base = f.getName().toLowerCase(Locale.ROOT);
                if (base.endsWith(".yml")) base = base.substring(0, base.length() - 4);
                currentFiles.put(normalizeKey(base), f);
            }
        }
        Set<String> prevKeys = new HashSet<>(fileHashes.keySet());
        Set<String> currKeys = new HashSet<>(currentFiles.keySet());
        List<String> added = new ArrayList<>();
        List<String> removed = new ArrayList<>();
        List<String> modified = new ArrayList<>();
        Map<String, List<String>> modifiedDetails = new HashMap<>();
        for (String k : currKeys) {
            if (!prevKeys.contains(k)) added.add(k);
        }
        for (String k : prevKeys) {
            if (!currKeys.contains(k)) removed.add(k);
        }
        for (String k : currKeys) {
            if (prevKeys.contains(k)) {
                try {
                    File f = currentFiles.get(k);
                    String newHash = computeHash(f);
                    String oldHash = fileHashes.get(k);
                    if (oldHash == null || !oldHash.equals(newHash)) {
                        modified.add(k);
                        YamlConfiguration oldCfg = rawConfigs.get(k);
                        YamlConfiguration newCfg = YamlConfiguration.loadConfiguration(f);
                        List<String> diffs = compareTopLevel(oldCfg, newCfg);
                        modifiedDetails.put(k, diffs);
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Impossibile controllare modifiche per " + k + ": " + e.getMessage());
                }
            }
        }
        loadAbilities();
        plugin.getLogger().info("Ricaricamento configurazioni abilità completato. Added=" + added.size() + " Removed=" + removed.size() + " Modified=" + modified.size());
        return new ReloadResult(added, removed, modified, modifiedDetails);
    }

    private List<String> compareTopLevel(YamlConfiguration oldCfg, YamlConfiguration newCfg) {
        List<String> diffs = new ArrayList<>();
        Map<String, Object> oldMap = oldCfg == null ? Collections.emptyMap() : oldCfg.getValues(false);
        Map<String, Object> newMap = newCfg == null ? Collections.emptyMap() : newCfg.getValues(false);
        Set<String> allKeys = new HashSet<>();
        allKeys.addAll(oldMap.keySet());
        allKeys.addAll(newMap.keySet());
        for (String key : allKeys) {
            Object oldVal = oldMap.get(key);
            Object newVal = newMap.get(key);
            if (!Objects.equals(oldVal, newVal)) {
                String oldS = oldVal == null ? "<absent>" : String.valueOf(oldVal);
                String newS = newVal == null ? "<absent>" : String.valueOf(newVal);
                diffs.add(key + ": '" + oldS + "' -> '" + newS + "'");
            }
        }
        return diffs;
    }

    private String computeHash(File f) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] bytes = Files.readAllBytes(f.toPath());
            byte[] digest = md.digest(bytes);
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return Long.toString(f.lastModified());
        }
    }

    public synchronized void reloadAndIgnoreResult() {
        reload();
    }

    public AbilityData getAbility(String name) {
        if (name == null) return null;
        return abilities.get(normalizeKey(name));
    }

    public DashData getDashData() {
        AbilityData data = abilities.get("dash");
        return data instanceof DashData ? (DashData) data : null;
    }

    public BlackholeData getBlackholeData() {
        AbilityData data = abilities.get("blackhole");
        return data instanceof BlackholeData ? (BlackholeData) data : null;
    }

    public LeechFieldData getLeechFieldData() {
        AbilityData data = abilities.get("leechfield");
        return data instanceof LeechFieldData ? (LeechFieldData) data : null;
    }

    public BluHollowData getBluHollowData() {
        AbilityData data = abilities.get("bluhollow");
        return data instanceof BluHollowData ? (BluHollowData) data : null;
    }

    public static class ReloadResult {
        public final List<String> added;
        public final List<String> removed;
        public final List<String> modified;
        public final Map<String, List<String>> modifiedDetails;

        public ReloadResult(List<String> added, List<String> removed, List<String> modified, Map<String, List<String>> modifiedDetails) {
            this.added = Collections.unmodifiableList(new ArrayList<>(added));
            this.removed = Collections.unmodifiableList(new ArrayList<>(removed));
            this.modified = Collections.unmodifiableList(new ArrayList<>(modified));
            this.modifiedDetails = Collections.unmodifiableMap(new HashMap<>(modifiedDetails));
        }

        public boolean isEmpty() {
            return added.isEmpty() && removed.isEmpty() && modified.isEmpty();
        }
    }
}