package me.pezzo.abilityPlugin;

import me.pezzo.abilityPlugin.commands.AbilityCommand;
import me.pezzo.abilityPlugin.config.AbilityConfig;
import me.pezzo.abilityPlugin.config.LanguageConfig;
import me.pezzo.abilityPlugin.listener.AbilityListener;
import me.pezzo.abilityPlugin.managers.AbilityManager;
import me.pezzo.abilityPlugin.managers.ChargingManager;
import org.bukkit.plugin.java.JavaPlugin;
import revxrsal.commands.bukkit.BukkitCommandHandler;

public final class AbilityPlugin extends JavaPlugin {

    private static AbilityPlugin instance;
    private BukkitCommandHandler commandHandler;
    private AbilityManager abilityManager;
    private AbilityConfig abilityConfig;
    private LanguageConfig languageConfig;
    private ChargingManager chargingManager;

    @Override
    public void onEnable() {
        instance = this;

        languageConfig = new LanguageConfig(this);

        abilityConfig = new AbilityConfig(this);
        abilityManager = new AbilityManager(this, abilityConfig);

        chargingManager = new ChargingManager(this);

        registerCommands();
        registerListeners();

        getLogger().info("========================================");
        getLogger().info("AbilityPlugin enabled!");
        getLogger().info("========================================");
    }

    @Override
    public void onDisable() {
        getServer().getScheduler().cancelTasks(this);

        instance = null;
        abilityManager = null;
        abilityConfig = null;
        languageConfig = null;
        chargingManager = null;
        commandHandler = null;
    }

    public static AbilityPlugin getInstance() {
        return instance;
    }

    public AbilityManager getAbilityManager() {
        return abilityManager;
    }

    public AbilityConfig getAbilityConfig() {
        return abilityConfig;
    }

    public LanguageConfig getLanguageConfig() {
        return languageConfig;
    }

    public ChargingManager getChargingManager() {
        return chargingManager;
    }

    private void registerCommands() {
        commandHandler = BukkitCommandHandler.create(this);
        commandHandler.register(new AbilityCommand(this));
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new AbilityListener(this), this);
    }

}