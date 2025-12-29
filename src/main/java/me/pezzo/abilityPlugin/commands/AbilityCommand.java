package me.pezzo.abilityPlugin.commands;

import me.pezzo.abilityPlugin.AbilityPlugin;
import me.pezzo.abilityPlugin.config.data.AbilityData;
import me.pezzo.abilityPlugin.enums.AbilityType;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import revxrsal.commands.annotation.Command;
import revxrsal.commands.annotation.Subcommand;
import revxrsal.commands.bukkit.annotation.CommandPermission;

import java.util.ArrayList;
import java.util.List;

@Command("tability")
@CommandPermission("tability.command.use")
public record AbilityCommand(AbilityPlugin plugin) {

    @Subcommand("info")
    @CommandPermission("tability.command.info")
    public void info(CommandSender sender) {
        sender.sendMessage("========================================");
        sender.sendMessage("Authors: TempBanneds");
        sender.sendMessage("Ability: Dash, BlackHoles");
        sender.sendMessage("Last Update: 29/12/2025");
        sender.sendMessage("========================================");
    }

    @Subcommand("give")
    @CommandPermission("tability.command.give")
    public void give(CommandSender sender, Player target, AbilityType ability) {
        AbilityData data = plugin.getAbilityConfig().getAbility(ability.name().toLowerCase());
        if (data == null) {
            sender.sendMessage("§cConfigurazione per l'abilità " + ability.name() + " non trovata!");
            return;
        }

        ItemStack item = new ItemStack(data.getItem());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', data.getName()));
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.translateAlternateColorCodes('&', data.getLore()));
            lore.add("§eCooldown: " + data.getCooldown() / 1000.0 + "s");
            meta.setLore(lore);
            item.setItemMeta(meta);
        }

        target.getInventory().addItem(item);
        sender.sendMessage("Hai dato " + ability.name() + " a " + target.getName());
    }
}
