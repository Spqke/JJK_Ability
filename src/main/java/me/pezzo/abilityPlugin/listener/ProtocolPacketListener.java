package me.pezzo.abilityPlugin.listener;

import me.pezzo.abilityPlugin.AbilityPlugin;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.wrappers.EnumWrappers.PlayerDigType;
import org.bukkit.entity.Player;

/**
 * Registra un PacketAdapter per intercettare il rilascio del right-click
 * tramite il packet BLOCK_DIG con PlayerDigType.RELEASE_USE_ITEM.
 */
public final class ProtocolPacketListener {

    public ProtocolPacketListener(AbilityPlugin host) {
        if (host == null) return;

        if (host.getServer().getPluginManager().getPlugin("ProtocolLib") == null) {
            host.getLogger().info("ProtocolLib non trovato: packet-based release disabilitata.");
            return;
        }

        try {
            ProtocolManager manager = ProtocolLibrary.getProtocolManager();
            manager.addPacketListener(new PacketAdapter(host, ListenerPriority.NORMAL, PacketType.Play.Client.BLOCK_DIG) {
                @Override
                public void onPacketReceiving(PacketEvent event) {
                    if (event == null || event.getPlayer() == null) return;
                    Player p = event.getPlayer();
                    try {
                        // Leggiamo il tipo di dig (es. START_DIG, FINISH_DIG, RELEASE_USE_ITEM, ...)
                        PlayerDigType type = event.getPacket().getEnumModifier(PlayerDigType.class, 0).read(0);
                        if (type == PlayerDigType.RELEASE_USE_ITEM) {
                            // Usiamo 'host' (l'istanza del tuo plugin) per chiamare il ChargingManager
                            if (host.getChargingManager().isCharging(p)) {
                                host.getChargingManager().releaseCharging(p);
                            }
                        }
                    } catch (Throwable t) {
                        host.getLogger().warning("Errore durante packet handling: " + t.getMessage());
                    }
                }
            });
            host.getLogger().info("ProtocolLib packet listener registrato (RELEASE_USE_ITEM via BLOCK_DIG).");
        } catch (Throwable t) {
            host.getLogger().warning("Impossibile registrare il packet listener ProtocolLib: " + t.getMessage());
        }
    }
}