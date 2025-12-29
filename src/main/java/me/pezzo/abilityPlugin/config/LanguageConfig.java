package me.pezzo.abilityPlugin.config;

import me.pezzo.abilityPlugin.AbilityPlugin;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.*;

/**
 * Gestisce il file lang.yml separato. Fornisce:
 * - caricamento e reload con rilevamento modifiche (added/removed/modified)
 * - formattazione messaggi con placeholder {key}
 * - supporto per '&' come codice colore
 */
public class LanguageConfig {

    private final AbilityPlugin plugin;
    private final File langFile;
    private YamlConfiguration currentConfig;
    // valori "flattened" key->string value, per confronti affidabili
    private Map<String, String> previousFlat = new HashMap<>();
    private String previousHash;

    public LanguageConfig(AbilityPlugin plugin) {
        this.plugin = plugin;
        this.langFile = new File(plugin.getDataFolder(), "lang.yml");
        if (!langFile.getParentFile().exists()) langFile.getParentFile().mkdirs();
        loadInitial();
    }

    private void loadInitial() {
        if (!langFile.exists()) createDefaultLang();
        this.currentConfig = YamlConfiguration.loadConfiguration(langFile);
        this.previousFlat = flatten(this.currentConfig);
        this.previousHash = computeHash(langFile);
    }

    private void createDefaultLang() {
        YamlConfiguration cfg = new YamlConfiguration();
        // Messages general (usare & per colori)
        cfg.set("reload.started", "&aRicaricamento configurazioni in corso...");
        cfg.set("reload.completed", "&aRicaricamento completato.");
        cfg.set("reload.none", "&7Nessuna modifica rilevata.");
        cfg.set("reload.added", "&aFile aggiunti: {files}");
        cfg.set("reload.removed", "&cFile rimossi: {files}");
        cfg.set("reload.modified", "&eFile modificati: {files}");
        cfg.set("reload.detail.header", "&6Dettagli per: {file}");
        cfg.set("reload.detail.none", "&7(modificato ma nessun cambiamento top-level rilevato)");

        // Ability messages
        cfg.set("ability.not_found", "&cConfigurazione per l'abilità {ability} non trovata!");
        cfg.set("ability.given", "&aHai dato {ability} a {player}");
        cfg.set("ability.used_dash", "&bHai usato il Dash!");
        cfg.set("ability.used_blackhole", "&5Hai creato un Blackhole!");
        cfg.set("ability.cooldown", "&c{ability} in cooldown: {seconds}s");
        cfg.set("ability.load_error", "&cErrore caricamento configurazione per {ability}: {error}");

        // Errors
        cfg.set("error.reload", "&cErrore durante il reload: {error}");

        try {
            cfg.save(langFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Impossibile creare lang.yml di default: " + e.getMessage());
        }
    }

    /**
     * Format a message by key with placeholders and color codes.
     * Placeholders format: {name}
     * Example: format("ability.given", Map.of("ability","Dash","player","Foo"))
     */
    public String format(String key, Map<String, String> placeholders) {
        if (key == null) return "";
        // otteniamo raw (non tradotto) per sostituire placeholders, poi traduciamo '&'
        String raw = currentConfig == null ? null : currentConfig.getString(key, null);
        if (raw == null) raw = "<missing:" + key + ">";
        if (placeholders != null) {
            for (Map.Entry<String, String> e : placeholders.entrySet()) {
                raw = raw.replace("{" + e.getKey() + "}", e.getValue() == null ? "" : e.getValue());
            }
        }
        return ChatColor.translateAlternateColorCodes('&', raw);
    }

    /**
     * Ritorna la string tradotta (& -> colori). Utile quando non servono placeholder.
     */
    public String getString(String key, String def) {
        if (currentConfig == null) return ChatColor.translateAlternateColorCodes('&', def == null ? "" : def);
        String raw = currentConfig.getString(key, def);
        return ChatColor.translateAlternateColorCodes('&', raw == null ? "" : raw);
    }

    public YamlConfiguration getConfig() {
        return currentConfig;
    }

    /**
     * Reload del lang.yml. Ritorna un ReloadResult con added/removed/modified (tutte le chiavi flattenate)
     */
    public synchronized ReloadResult reload() {
        if (!langFile.exists()) createDefaultLang();
        YamlConfiguration newCfg = YamlConfiguration.loadConfiguration(langFile);
        Map<String, String> newFlat = flatten(newCfg);
        String newHash = computeHash(langFile);

        List<String> added = new ArrayList<>();
        List<String> removed = new ArrayList<>();
        List<String> modified = new ArrayList<>();
        Map<String, String[]> modifiedDetails = new HashMap<>();

        Set<String> allKeys = new HashSet<>();
        allKeys.addAll(previousFlat.keySet());
        allKeys.addAll(newFlat.keySet());

        for (String k : allKeys) {
            String oldVal = previousFlat.get(k);
            String newVal = newFlat.get(k);
            if (oldVal == null && newVal != null) {
                added.add(k);
            } else if (oldVal != null && newVal == null) {
                removed.add(k);
            } else if (oldVal != null && newVal != null && !Objects.equals(oldVal, newVal)) {
                modified.add(k);
                modifiedDetails.put(k, new String[]{oldVal, newVal});
            }
        }

        // aggiorna cache
        this.previousFlat = new HashMap<>(newFlat);
        this.currentConfig = newCfg;
        this.previousHash = newHash;

        return new ReloadResult(added, removed, modified, modifiedDetails);
    }

    // Flatten di tutte le chiavi in dot-notation prendendo solo valori scalari (string)
    private Map<String, String> flatten(YamlConfiguration cfg) {
        Map<String, String> out = new HashMap<>();
        if (cfg == null) return out;
        // getKeys(true) include tutte le chiavi profonde; per ogni key che NON è section prendiamo getString
        Set<String> keys = cfg.getKeys(true);
        for (String key : keys) {
            if (cfg.isConfigurationSection(key)) continue;
            String val = cfg.getString(key, null);
            out.put(key, val == null ? "" : val);
        }
        return out;
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

    public static class ReloadResult {
        public final List<String> added;
        public final List<String> removed;
        public final List<String> modified;
        public final Map<String, String[]> modifiedDetails; // key -> [old, new]

        public ReloadResult(List<String> added, List<String> removed, List<String> modified, Map<String, String[]> modifiedDetails) {
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