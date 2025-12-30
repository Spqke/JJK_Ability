package me.pezzo.abilityPlugin.managers;

import me.pezzo.abilityPlugin.AbilityPlugin;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.ChatMessageType;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.Particle.DustOptions;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ChargingManager {

    private final AbilityPlugin plugin;
    private final Map<UUID, ChargingState> charging = new ConcurrentHashMap<>();
    private static final long MAX_CHARGE_MS = 8000L;
    private static final int MIN_TICKS_BEFORE_RELEASE = 6;
    private static final int MISS_THRESHOLD = 4; // tick di tolleranza per false positives
    private static final long MIN_HOLD_MS = 200L; // se il giocatore ha tenuto meno di questa soglia, non eseguire il dash

    private static final class ChargingState {
        final long startTime;
        final String itemDisplayName;
        final Material itemType;
        final BukkitRunnable task;
        int missCount = 0;

        ChargingState(long startTime, String itemDisplayName, Material itemType, BukkitRunnable task) {
            this.startTime = startTime;
            this.itemDisplayName = itemDisplayName;
            this.itemType = itemType;
            this.task = task;
        }
    }

    public ChargingManager(AbilityPlugin plugin) {
        this.plugin = plugin;
    }

    public void startCharging(Player player, String itemDisplayName) {
        UUID id = player.getUniqueId();
        if (charging.containsKey(id)) return;

        long start = System.currentTimeMillis();

        plugin.getLogger().info("[Charging] start for " + player.getName() + " item='" + itemDisplayName + "'");

        final Material startType;
        ItemStack startItem = player.getInventory().getItemInMainHand();
        startType = (startItem == null) ? Material.AIR : startItem.getType();

        final ChargingState[] stateRef = new ChargingState[1];

        BukkitRunnable task = new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                ticks++;

                if (!player.isOnline()) {
                    releaseCharging(player);
                    return;
                }

                ItemStack current = player.getInventory().getItemInMainHand();
                boolean sameItem = false;
                if (current != null && current.getType() == startType && current.hasItemMeta() && current.getItemMeta().hasDisplayName()) {
                    String curName = current.getItemMeta().getDisplayName();
                    sameItem = itemDisplayName != null && itemDisplayName.equals(curName);
                }

                long now = System.currentTimeMillis();
                long elapsed = Math.max(0, now - start);
                long capped = Math.min(MAX_CHARGE_MS, elapsed);
                double ratio = (double) capped / (double) MAX_CHARGE_MS;
                int percent = (int) Math.round(ratio * 100.0);

                // Action bar in tempo reale con formato "Dash: X%"
                String bar = "§bDash: §f" + percent + "%";
                try {
                    player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(bar));
                } catch (Throwable ignored) {
                }

                Color lightBlue = Color.fromRGB(130, 210, 255);
                Color warmYellow = Color.fromRGB(255, 220, 90);
                DustOptions blue = new DustOptions(lightBlue, (float) (0.8 + 0.8 * ratio));
                DustOptions yellow = new DustOptions(warmYellow, (float) (0.6 + 0.6 * ratio));

                int count = 6 + (int) (ratio * 40);
                double spread = 0.6 + ratio * 1.2;

                for (int i = 0; i < count; i++) {
                    double rx = (Math.random() - 0.5) * spread;
                    double rz = (Math.random() - 0.5) * spread;
                    double ry = 0.2 + Math.random() * 1.2;
                    player.getWorld().spawnParticle(
                            Particle.REDSTONE,
                            player.getLocation().clone().add(rx, ry, rz),
                            1,
                            0, 0, 0, 0,
                            (Math.random() < 0.6) ? blue : yellow
                    );
                }

                if (ticks < MIN_TICKS_BEFORE_RELEASE) {
                    return;
                }

                ChargingState st = stateRef[0];
                if (st == null) return;

                if (!sameItem) {
                    st.missCount++;
                } else {
                    st.missCount = 0;
                }

                // solo se il mismatch persiste per MISS_THRESHOLD tick consecutivi -> CANCEL (abort)
                if (st.missCount >= MISS_THRESHOLD) {
                    plugin.getLogger().info(String.format(
                            "[Charging] cancel for %s: consecutive misses=%d ticks=%d percent=%d",
                            player.getName(),
                            st.missCount,
                            ticks,
                            percent
                    ));
                    // annulliamo la charging (non rilasciamo il dash)
                    cancelCharging(player);
                }
            }
        };

        ChargingState s = new ChargingState(start, itemDisplayName, startType, task);
        stateRef[0] = s;
        task.runTaskTimer(plugin, 1L, 1L);
        charging.put(id, s);
    }

    public void cancelCharging(Player player) {
        ChargingState s = charging.remove(player.getUniqueId());
        if (s != null) {
            try {
                s.task.cancel();
            } catch (Throwable ignored) {
            }
            try {
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(""));
            } catch (Throwable ignored) {
            }
        }
    }

    public boolean isCharging(Player player) {
        return charging.containsKey(player.getUniqueId());
    }

    public void releaseCharging(Player player) {
        UUID id = player.getUniqueId();
        ChargingState s = charging.remove(id);
        if (s != null) {
            try {
                s.task.cancel();
            } catch (Throwable ignored) {
            }
            try {
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(""));
            } catch (Throwable ignored) {
            }

            long now = System.currentTimeMillis();
            long elapsed = Math.max(0, now - s.startTime);

            // se il giocatore ha tenuto premuto meno di MIN_HOLD_MS, non eseguire il dash
            if (elapsed < MIN_HOLD_MS) {
                plugin.getLogger().info(String.format(
                        "[Charging] release ignored for %s after %dms (below min hold %dms)",
                        player.getName(),
                        elapsed,
                        MIN_HOLD_MS
                ));
                return;
            }

            double ratio = Math.min(1.0, (double) elapsed / (double) MAX_CHARGE_MS);

            plugin.getLogger().info(String.format(
                    "[Charging] release for %s after %dms (ratio=%.2f)",
                    player.getName(),
                    elapsed,
                    ratio
            ));
            plugin.getAbilityManager().executeChargedDash(player, ratio);
        }
    }

    @SuppressWarnings("unused")
    private void stopAndRelease(Player player, long start) {
        UUID id = player.getUniqueId();
        ChargingState s = charging.remove(id);
        if (s != null) {
            try {
                s.task.cancel();
            } catch (Throwable ignored) {
            }
        }

        try {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(""));
        } catch (Throwable ignored) {
        }

        long now = System.currentTimeMillis();
        long elapsed = Math.max(0, now - start);
        double ratio = Math.min(1.0, (double) elapsed / (double) MAX_CHARGE_MS);

        plugin.getAbilityManager().executeChargedDash(player, ratio);
    }
}