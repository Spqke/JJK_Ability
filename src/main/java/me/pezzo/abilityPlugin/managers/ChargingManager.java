package me.pezzo.abilityPlugin.managers;

import me.pezzo.abilityPlugin.AbilityPlugin;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.ChatMessageType;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.Particle.DustOptions;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestisce il caricamento dell'abilità Dash:
 * - startCharging avviato al RIGHT_CLICK sullo item Dash
 * - mentre tiene premuto aggiorna percentuale (max 8s) e mostra action bar + aura particelle attorno al corpo
 * - al rilascio calcola ratio [0..1] e invoca AbilityManager.executeChargedDash
 */
public class ChargingManager {

    private final AbilityPlugin plugin;
    private final Map<UUID, ChargingState> charging = new ConcurrentHashMap<>();
    private static final long MAX_CHARGE_MS = 8000L;

    private static final class ChargingState {
        final long startTime;
        final String itemDisplayName;
        final BukkitRunnable task;
        ChargingState(long startTime, String itemDisplayName, BukkitRunnable task) {
            this.startTime = startTime;
            this.itemDisplayName = itemDisplayName;
            this.task = task;
        }
    }

    public ChargingManager(AbilityPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Avvia il caricamento per il giocatore (se non è già in caricamento).
     * itemDisplayName serve per validare che il giocatore stia ancora tenendo lo stesso item.
     */
    public void startCharging(Player player, String itemDisplayName) {
        UUID id = player.getUniqueId();
        if (charging.containsKey(id)) return;

        long start = System.currentTimeMillis();

        BukkitRunnable task = new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                // tick counter
                ticks++;

                if (!player.isOnline()) {
                    stopAndRelease(player, start);
                    return;
                }

                // Rilevazione se il giocatore "sta ancora usando" l'item.
                boolean stillUsing;
                try {
                    stillUsing = player.isHandRaised();
                } catch (NoSuchMethodError ex) {
                    // Fallback conservativo: controlla che l'oggetto in main hand abbia ancora lo stesso display name
                    stillUsing = player.getInventory().getItemInMainHand() != null
                            && player.getInventory().getItemInMainHand().hasItemMeta()
                            && player.getInventory().getItemInMainHand().getItemMeta().hasDisplayName()
                            && player.getInventory().getItemInMainHand().getItemMeta().getDisplayName().equals(itemDisplayName);
                }

                // Controllo cambio item (se il player ha swappato o rimosso l'item)
                boolean sameItem = player.getInventory().getItemInMainHand() != null
                        && player.getInventory().getItemInMainHand().hasItemMeta()
                        && player.getInventory().getItemInMainHand().getItemMeta().hasDisplayName()
                        && player.getInventory().getItemInMainHand().getItemMeta().getDisplayName().equals(itemDisplayName);

                long now = System.currentTimeMillis();
                long elapsed = Math.max(0, now - start);
                long capped = Math.min(MAX_CHARGE_MS, elapsed);
                double ratio = (double) capped / (double) MAX_CHARGE_MS;
                int percent = (int) Math.round(ratio * 100.0);

                // Action bar: percentuale
                String bar = "§bDash Charge §f" + percent + "%";
                try {
                    player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(bar));
                } catch (Throwable ignored) {
                    // no spigot available -> ignore
                }

                // Aura particelle attorno al CORPO del giocatore: più carica -> più particelle e raggio
                Color lightBlue = Color.fromRGB(130, 210, 255);
                Color warmYellow = Color.fromRGB(255, 220, 90);
                DustOptions blue = new DustOptions(lightBlue, (float) (0.8 + 0.8 * ratio));
                DustOptions yellow = new DustOptions(warmYellow, (float) (0.6 + 0.6 * ratio));

                int count = 6 + (int) (ratio * 40); // da ~6 a ~46 particelle per tick
                double spread = 0.6 + ratio * 1.2;

                for (int i = 0; i < count; i++) {
                    double rx = (Math.random() - 0.5) * spread;
                    double rz = (Math.random() - 0.5) * spread;
                    // Y intorno al torso (non solo testa): 0.2 .. 1.4
                    double ry = 0.2 + Math.random() * 1.2;
                    player.getWorld().spawnParticle(Particle.REDSTONE,
                            player.getLocation().clone().add(rx, ry, rz),
                            1, 0, 0, 0, 0,
                            (Math.random() < 0.6) ? blue : yellow);
                }

                // Non considerare un eventuale "release" nei primissimi tick per evitare dash immediato causato da valori API non affidabili.
                if (ticks < 2) {
                    return;
                }

                // Se il giocatore ha rilasciato il tasto o ha cambiato item -> stop & release
                if (!stillUsing || !sameItem) {
                    stopAndRelease(player, start);
                }
            }
        };

        // schedule every tick per UI reattiva; parte con 1 tick di delay per evitare run immediato
        task.runTaskTimer(plugin, 1L, 1L);
        charging.put(id, new ChargingState(start, itemDisplayName, task));
    }

    /**
     * Ferma il task (senza eseguire l'effetto) se presente.
     */
    public void cancelCharging(Player player) {
        ChargingState s = charging.remove(player.getUniqueId());
        if (s != null) {
            try { s.task.cancel(); } catch (Throwable ignored) {}
            // clear action bar
            try {
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(""));
            } catch (Throwable ignored) {}
        }
    }

    /**
     * Interno: ferma e chiama AbilityManager con il ratio calcolato.
     */
    private void stopAndRelease(Player player, long start) {
        UUID id = player.getUniqueId();
        ChargingState s = charging.remove(id);
        if (s != null) {
            try { s.task.cancel(); } catch (Throwable ignored) {}
        }
        // clear action bar
        try {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(""));
        } catch (Throwable ignored) {}

        long now = System.currentTimeMillis();
        long elapsed = Math.max(0, now - start);
        double ratio = Math.min(1.0, (double) elapsed / (double) MAX_CHARGE_MS);

        // Se ratio == 0 -> piccolo dash minimo (comportamento simile prima)
        plugin.getAbilityManager().executeChargedDash(player, ratio);
    }
}