package me.pezzo.abilityPlugin.config.data.ability;

import me.pezzo.abilityPlugin.config.data.AbilityData;
import org.bukkit.Material;

public class BluHollowData extends AbilityData {
    private final double damage;
    private final double radius;
    private final double speed;
    private final int durationTicks;
    private final boolean destroyBlocks;
    private final double anchorDistance;

    public BluHollowData(String name, String lore, Material item, double damage, double radius, double speed, int durationTicks, boolean destroyBlocks, double anchorDistance, long cooldown) {
        super(name, lore, item, cooldown);
        this.damage = damage;
        this.radius = radius;
        this.speed = speed;
        this.durationTicks = durationTicks;
        this.destroyBlocks = destroyBlocks;
        this.anchorDistance = anchorDistance;
    }

    public double getDamage() { return damage; }
    public double getRadius() { return radius; }
    public double getSpeed() { return speed; }
    public int getDurationTicks() { return durationTicks; }
    public boolean isDestroyBlocks() { return destroyBlocks; }
    public double getAnchorDistance() { return anchorDistance; }
}