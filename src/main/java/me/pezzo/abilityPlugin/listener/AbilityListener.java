package me.pezzo.abilityPlugin.listener;

import me.pezzo.abilityPlugin.AbilityPlugin;
import me.pezzo.abilityPlugin.config.data.ability.BlackholeData;
import me.pezzo.abilityPlugin.config.data.ability.BluHollowData;
import me.pezzo.abilityPlugin.config.data.ability.DashData;
import me.pezzo.abilityPlugin.config.data.ability.LeechFieldData;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

public record AbilityListener(AbilityPlugin plugin) implements Listener {

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Action action = event.getAction();
        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            ItemStack item = player.getInventory().getItemInMainHand();
            if (item == null || item.getType() == Material.AIR) return;
            if (!item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) return;
            String name = item.getItemMeta().getDisplayName();
            var dashData = plugin.getAbilityConfig().getDashData();
            var blackholeData = plugin.getAbilityConfig().getBlackholeData();
            var leechData = plugin.getAbilityConfig().getLeechFieldData();
            var bluData = plugin.getAbilityConfig().getBluHollowData();
            if (dashData != null && dashData.getName() != null) {
                String dashName = ChatColor.translateAlternateColorCodes('&', dashData.getName());
                if (name.equals(dashName)) {
                    if (plugin.getChargingManager().isCharging(player)) {
                        plugin.getChargingManager().releaseCharging(player);
                    } else {
                        plugin.getChargingManager().startCharging(player, dashName);
                    }
                    event.setCancelled(true);
                    return;
                }
            }
            if (blackholeData != null && blackholeData.getName() != null) {
                String blackholeName = ChatColor.translateAlternateColorCodes('&', blackholeData.getName());
                if (name.equals(blackholeName)) {
                    plugin.getAbilityManager().executeBlackhole(player);
                    event.setCancelled(true);
                    return;
                }
            }
            if (bluData != null && bluData.getName() != null) {
                String bluName = ChatColor.translateAlternateColorCodes('&', bluData.getName());
                if (name.equals(bluName)) {
                    plugin.getAbilityManager().executeBluHollow(player);
                    event.setCancelled(true);
                    return;
                }
            }
            if (leechData != null && leechData.getName() != null) {
                String leechName = ChatColor.translateAlternateColorCodes('&', leechData.getName());
                if (name.equals(leechName)) {
                    plugin.getAbilityManager().executeLeech(player);
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }
}