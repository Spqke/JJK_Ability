package me.pezzo.abilityPlugin.managers;

import me.pezzo.abilityPlugin.AbilityPlugin;
import me.pezzo.abilityPlugin.config.AbilityConfig;
import me.pezzo.abilityPlugin.config.data.ability.BlackholeData;
import me.pezzo.abilityPlugin.config.data.ability.BluHollowData;
import me.pezzo.abilityPlugin.config.data.ability.DashData;
import me.pezzo.abilityPlugin.config.data.ability.LeechFieldData;
import me.pezzo.abilityPlugin.managers.effects.BlackholeEffect;
import me.pezzo.abilityPlugin.managers.effects.BluHollowEffect;
import me.pezzo.abilityPlugin.managers.effects.DashEffect;
import me.pezzo.abilityPlugin.managers.effects.LeechFieldEffect;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

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

        // Legge i messaggi di fase dal lang.yml (fallback ai valori hard-coded se mancanti)
        String p0 = plugin.getLanguageConfig().getString("ability.blackhole.phase.0", org.bukkit.ChatColor.DARK_RED + "" + org.bukkit.ChatColor.BOLD + "Reversals");
        String p1 = plugin.getLanguageConfig().getString("ability.blackhole.phase.1", org.bukkit.ChatColor.RED + "" + org.bukkit.ChatColor.BOLD + "Divergence");
        String p2 = plugin.getLanguageConfig().getString("ability.blackhole.phase.2", org.bukkit.ChatColor.RED + "" + org.bukkit.ChatColor.ITALIC + "" + org.bukkit.ChatColor.BOLD + "Positive Energy");

        player.sendMessage(p0);
        new BukkitRunnable() {
            int step = 0;
            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    return;
                }
                if (step == 1) {
                    player.sendMessage(p1);
                } else if (step == 2) {
                    player.sendMessage(p2);
                } else if (step >= 3) {
                    Location target;
                    try {
                        Block b = player.getTargetBlockExact(40);
                        if (b != null) target = b.getLocation().add(0.5, 1.0, 0.5);
                        else target = player.getLocation().add(player.getLocation().getDirection().normalize().multiply(10));
                    } catch (Throwable ex) {
                        target = player.getLocation().add(player.getLocation().getDirection().normalize().multiply(10));
                    }
                    new BlackholeEffect(player, target, blackholeData.getDamage(), blackholeData.getRange()).start();
                    cancel();
                    return;
                }
                step++;
            }
        }.runTaskTimer(plugin, 0L, 10L);
    }

    public void executeBluHollow(Player player) {
        UUID id = player.getUniqueId();
        BluHollowData blu = abilityConfig.getBluHollowData();
        if (blu == null) {
            player.sendMessage(plugin.getLanguageConfig().format("ability.load_error", java.util.Map.of("ability", "bluhollow", "error", "missing")));
            return;
        }
        if (!cooldownManager.tryUse(id, "bluhollow", blu.getCooldown())) {
            long remain = cooldownManager.getRemainingMillis(id, "bluhollow");
            double seconds = Math.ceil(remain / 100.0) / 10.0;
            player.sendMessage(plugin.getLanguageConfig().format("ability.cooldown", java.util.Map.of("ability", "BluHollow", "seconds", String.valueOf(seconds))));
            return;
        }

        // Legge i messaggi di fase dal lang.yml (fallback ai valori hard-coded se mancanti)
        String b0 = plugin.getLanguageConfig().getString("ability.bluhollow.phase.0", org.bukkit.ChatColor.BLUE + "" + org.bukkit.ChatColor.BOLD + "Limitless");
        String b1 = plugin.getLanguageConfig().getString("ability.bluhollow.phase.1", org.bukkit.ChatColor.AQUA + "" + org.bukkit.ChatColor.BOLD + "Convergence");
        String b2 = plugin.getLanguageConfig().getString("ability.bluhollow.phase.2", org.bukkit.ChatColor.DARK_AQUA + "" + org.bukkit.ChatColor.ITALIC + "" + org.bukkit.ChatColor.BOLD + "Negative Energy");

        player.sendMessage(b0);
        new BukkitRunnable() {
            int step = 0;
            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    return;
                }
                if (step == 1) {
                    player.sendMessage(b1);
                } else if (step == 2) {
                    player.sendMessage(b2);
                } else if (step >= 3) {
                    Location start;
                    try {
                        start = player.getEyeLocation().add(player.getLocation().getDirection().normalize().multiply(1.5));
                    } catch (Throwable ex) {
                        start = player.getLocation().add(player.getLocation().getDirection().normalize().multiply(1.5));
                    }
                    Vector dir = player.getLocation().getDirection().normalize();
                    new BluHollowEffect(player, start, dir, blu.getDamage(), blu.getRadius(), blu.getSpeed(), blu.getDurationTicks(), blu.isDestroyBlocks(), blu.getAnchorDistance()).start();
                    cancel();
                    return;
                }
                step++;
            }
        }.runTaskTimer(plugin, 0L, 8L);
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
        player.sendMessage(plugin.getLanguageConfig().getString("ability.used_leech", "&2Hai creato un Leechs Field!"));
        new LeechFieldEffect(player, leechData).start();
    }
}