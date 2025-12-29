package me.pezzo.abilityPlugin.config.data;

import org.bukkit.Material;

public abstract class AbilityData {
    protected String name;
    protected String lore;
    protected Material item;
    protected long cooldown;

    public AbilityData(String name, String lore, Material item, long cooldown) {
        this.name = name;
        this.lore = lore;
        this.item = item;
        this.cooldown = cooldown;
    }

    // Getters
    public String getName() { return name; }
    public String getLore() { return lore; }
    public Material getItem() { return item; }
    public long getCooldown() { return cooldown; }
}