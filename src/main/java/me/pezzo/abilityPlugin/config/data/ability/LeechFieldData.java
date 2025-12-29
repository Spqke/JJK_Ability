package me.pezzo.abilityPlugin.config.data.ability;

import me.pezzo.abilityPlugin.config.data.AbilityData;
import org.bukkit.Material;

public class LeechFieldData extends AbilityData {
    private final double radius;
    private final double damagePerTick;
    private final int tickInterval;
    private final int durationTicks;
    private final double knockbackReduce;
    private final int slownessLevel;

    public LeechFieldData(String name, String lore, Material item,
                          double radius, double damagePerTick, int tickInterval, int durationTicks,
                          double knockbackReduce, int slownessLevel, long cooldown) {
        super(name, lore, item, cooldown);
        this.radius = radius;
        this.damagePerTick = damagePerTick;
        this.tickInterval = tickInterval;
        this.durationTicks = durationTicks;
        this.knockbackReduce = knockbackReduce;
        this.slownessLevel = slownessLevel;
    }

    public double getRadius() { return radius; }
    public double getDamagePerTick() { return damagePerTick; }
    public int getTickInterval() { return tickInterval; }
    public int getDurationTicks() { return durationTicks; }
    public double getKnockbackReduce() { return knockbackReduce; }
    public int getSlownessLevel() { return slownessLevel; }
}