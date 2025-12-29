package me.pezzo.abilityPlugin.commands;

import me.pezzo.abilityPlugin.AbilityPlugin;
import me.pezzo.abilityPlugin.config.AbilityConfig;
import me.pezzo.abilityPlugin.config.AbilityConfig.ReloadResult;
import me.pezzo.abilityPlugin.config.LanguageConfig;
import me.pezzo.abilityPlugin.config.data.AbilityData;
import me.pezzo.abilityPlugin.enums.AbilityType;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import revxrsal.commands.annotation.Command;
import revxrsal.commands.annotation.Subcommand;
import revxrsal.commands.bukkit.annotation.CommandPermission;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

@Command("tability")
@CommandPermission("tability.command.use")
public record AbilityCommand(AbilityPlugin plugin) {

    @Subcommand("info")
    @CommandPermission("tability.command.info")
    public void info(CommandSender sender) {
        LanguageConfig lang = plugin.getLanguageConfig();
        sender.sendMessage("========================================");
        sender.sendMessage(lang.getString("ability.info.authors", "Authors: TempBanneds"));
        sender.sendMessage(lang.getString("ability.info.list", "Abilities incluse: Dash, Blackhole, BluHollow, LeechField"));
        sender.sendMessage("Last Update: 29/12/2025");
        sender.sendMessage("========================================");
    }

    @Subcommand("give")
    @CommandPermission("tability.command.give")
    public void give(CommandSender sender, Player target, AbilityType ability) {
        AbilityData data = plugin.getAbilityConfig().getAbility(ability.name().toLowerCase());
        if (data == null) {
            String msg = plugin.getLanguageConfig().format("ability.not_found", Map.of("ability", ability.name()));
            sender.sendMessage(msg);
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
        String msg = plugin.getLanguageConfig().format("ability.given", Map.of("ability", ability.name(), "player", target.getName()));
        sender.sendMessage(msg);
    }

    @Subcommand("reload")
    @CommandPermission("tability.command.reload")
    public void reload(CommandSender sender) {
        try {
            LanguageConfig langCfg = plugin.getLanguageConfig();
            AbilityConfig cfg = plugin.getAbilityConfig();

            sender.sendMessage(langCfg.format("reload.started", null));

            LanguageConfig.ReloadResult langResult = langCfg.reload();
            ReloadResult abilityResult = cfg.reload();

            sender.sendMessage(langCfg.format("reload.completed", null));

            int totalChanges = abilityResult.added.size() + abilityResult.removed.size() + abilityResult.modified.size()
                    + langResult.added.size() + langResult.removed.size() + langResult.modified.size();

            if (totalChanges == 0) {
                sender.sendMessage(langCfg.getString("reload.none", "§7Nessuna modifica rilevata."));
                return;
            }

            if (!abilityResult.added.isEmpty()) {
                sender.sendMessage(langCfg.format("reload.added", Map.of("files", String.join(", ", abilityResult.added))));
            }
            if (!abilityResult.removed.isEmpty()) {
                sender.sendMessage(langCfg.format("reload.removed", Map.of("files", String.join(", ", abilityResult.removed))));
            }
            if (!abilityResult.modified.isEmpty()) {
                sender.sendMessage(langCfg.format("reload.modified", Map.of("files", String.join(", ", abilityResult.modified))));
                for (Entry<String, List<String>> e : abilityResult.modifiedDetails.entrySet()) {
                    sender.sendMessage(langCfg.format("reload.detail.header", Map.of("file", e.getKey() + ".yml")));
                    List<String> diffs = e.getValue();
                    if (diffs.isEmpty()) {
                        sender.sendMessage(langCfg.getString("reload.detail.none", "  §7(modificato ma nessun cambiamento top-level rilevato)"));
                    } else {
                        for (String d : diffs) sender.sendMessage("  §7- " + d);
                    }
                }
            }

            if (!langResult.added.isEmpty()) {
                sender.sendMessage("§aLang keys aggiunte: " + String.join(", ", langResult.added));
            }
            if (!langResult.removed.isEmpty()) {
                sender.sendMessage("§cLang keys rimosse: " + String.join(", ", langResult.removed));
            }
            if (!langResult.modified.isEmpty()) {
                sender.sendMessage("§eLang keys modificate: " + String.join(", ", langResult.modified));
                for (Entry<String, String[]> e : langResult.modifiedDetails.entrySet()) {
                    String key = e.getKey();
                    String oldV = e.getValue()[0];
                    String newV = e.getValue()[1];
                    sender.sendMessage("  §6" + key + ": ' " + oldV + " ' -> ' " + newV + " '");
                }
            }

            String who = (sender instanceof Player ? ((Player) sender).getName() : "Console");
            plugin.getLogger().info(who + " ha ricaricato le abilità e lang. Changes: abilities added=" + abilityResult.added.size()
                    + " removed=" + abilityResult.removed.size() + " modified=" + abilityResult.modified.size()
                    + " | lang added=" + langResult.added.size() + " removed=" + langResult.removed.size() + " modified=" + langResult.modified.size());
        } catch (Exception e) {
            String msg = plugin.getLanguageConfig().format("error.reload", Map.of("error", e.getMessage()));
            sender.sendMessage(msg);
            plugin.getLogger().severe("Errore durante reload abilità/lang: " + e.getMessage());
        }
    }
}