package me.pezzo.abilityPlugin.managers;

import me.pezzo.abilityPlugin.AbilityPlugin;
import me.pezzo.abilityPlugin.config.AbilityConfig;
import me.pezzo.abilityPlugin.config.data.ability.BlackholeData;
import me.pezzo.abilityPlugin.config.data.ability.DashData;
import me.pezzo.abilityPlugin.config.data.ability.LeechFieldData;
import me.pezzo.abilityPlugin.managers.effects.BlackholeEffect;
import me.pezzo.abilityPlugin.managers.effects.DashEffect;
import me.pezzo.abilityPlugin.managers.effects.LeechFieldEffect;
import org.bukkit.entity.Player;

import java.util.UUID;

public class AbilityManager {

    private final CooldownManager cooldownManager = new CooldownManager();
    private final AbilityConfig abilityConfig;
    private final AbilityPlugin plugin;

    public AbilityManager(AbilityPlugin plugin, AbilityConfig config) {
        this.plugin = plugin;
        this.abilityConfig = config;
    }

    public void executeDash(Player player) {
        UUID id = player.getUniqueId();
        DashData dashData = abilityConfig.getDashData();
        if (dashData == null) {
            player.sendMessage(plugin.getLanguageConfig().format("ability.load_error", java.util.Map.of("ability", "dash", "error", "missing")));
            return;
        }

        if (!cooldownManager.tryUse(id, "dash", dashData.getCooldown())) {
            long remain = cooldownManager.getRemainingMillis(id, "dash");
            double seconds = Math.ceil(remain / 100.0) / 10.0;
            player.sendMessage(plugin.getLanguageConfig().format("ability.cooldown", java.util.Map.of("ability", "Dash", "seconds", String.valueOf(seconds))));
            return;
        }

        player.sendMessage(plugin.getLanguageConfig().getString("ability.used_dash", "&bHai usato il Dash!"));
        new DashEffect(player, dashData.getBoost()).start();
    }

    public void executeBlackhole(Player player) {
        UUID id = player.getUniqueId();
        BlackholeData blackholeData = abilityConfig.getBlackholeData();
        if (blackholeData == null) {
            player.sendMessage(plugin.getLanguageConfig().format("ability.load_error", java.util.Map.of("ability", "blackhole", "error", "missing")));
            return;
        }

        if (!cooldownManager.tryUse(id, "blackhole", blackholeData.getCooldown())) {
            long remain = cooldownManager.getRemainingMillis(id, "blackhole");
            double seconds = Math.ceil(remain / 100.0) / 10.0;
            player.sendMessage(plugin.getLanguageConfig().format("ability.cooldown", java.util.Map.of("ability", "Blackhole", "seconds", String.valueOf(seconds))));
            return;
        }

        player.sendMessage(plugin.getLanguageConfig().getString("ability.used_blackhole", "&5Hai creato un Blackhole!"));
        new BlackholeEffect(player, blackholeData.getDamage(), blackholeData.getRange()).start();
    }

    public void executeLeech(Player player) {
        UUID id = player.getUniqueId();
        LeechFieldData leechData = abilityConfig.getLeechFieldData();
        if (leechData == null) {
            player.sendMessage(plugin.getLanguageConfig().format("ability.load_error", java.util.Map.of("ability", "leechfield", "error", "missing")));
            return;
        }

        if (!cooldownManager.tryUse(id, "leechfield", leechData.getCooldown())) {
            long remain = cooldownManager.getRemainingMillis(id, "leechfield");
            double seconds = Math.ceil(remain / 100.0) / 10.0;
            player.sendMessage(plugin.getLanguageConfig().format("ability.cooldown", java.util.Map.of("ability", "LeechField", "seconds", String.valueOf(seconds))));
            return;
        }

        player.sendMessage(plugin.getLanguageConfig().getString("ability.used_leech", "&2Hai creato un Leech Field!"));
        new LeechFieldEffect(player, leechData).start();
    }
}