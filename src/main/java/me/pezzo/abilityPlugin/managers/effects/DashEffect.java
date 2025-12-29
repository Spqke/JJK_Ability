package me.pezzo.abilityPlugin.managers.effects;

import me.pezzo.abilityPlugin.AbilityPlugin;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Particle.DustOptions;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public class DashEffect {

    private final double multiplier;
    private final Player owner;
    private final double chargeRatio;
    private final int trailTicksBase = 6;

    public DashEffect(Player owner, double multiplier) {
        this(owner, multiplier, 0.0);
    }

    public DashEffect(Player owner, double multiplier, double chargeRatio) {
        this.owner = owner;
        this.multiplier = multiplier;
        this.chargeRatio = Math.max(0.0, Math.min(1.0, chargeRatio));
    }

    public void start() {
        Vector dir = owner.getLocation().getDirection().normalize().multiply(multiplier * (1.0 + chargeRatio * 2.0));
        dir.setY(0.18 + chargeRatio * 0.25);
        owner.setVelocity(dir);

        float pitch = (float) (1.0 + chargeRatio * 0.2);
        owner.getWorld().playSound(owner.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, pitch);

        Color lightBlue = Color.fromRGB(120, 200, 255);
        Color warmYellow = Color.fromRGB(255, 220, 80);
        DustOptions blue = new DustOptions(lightBlue, (float) (0.8 + chargeRatio * 1.2));
        DustOptions yellow = new DustOptions(warmYellow, (float) (0.6 + chargeRatio * 1.0));

        int blastCount = 10 + (int) (chargeRatio * 30);
        for (int i = 0; i < blastCount; i++) {
            double rx = (Math.random() - 0.5) * (1.5 + chargeRatio * 2.5);
            double ry = (Math.random() - 0.5) * (0.8 + chargeRatio * 1.2);
            double rz = (Math.random() - 0.5) * (1.5 + chargeRatio * 2.5);
            owner.getWorld().spawnParticle(Particle.REDSTONE, owner.getLocation().clone().add(rx, ry + 0.8, rz), 1, 0, 0, 0, 0, (Math.random() < 0.6) ? blue : yellow);
            owner.getWorld().spawnParticle(Particle.EXPLOSION_NORMAL, owner.getLocation().clone().add(rx*0.4, ry*0.4 + 0.6, rz*0.4), 1, 0.1, 0.1, 0.1, 0.02);
        }

        int trailTicks = trailTicksBase + (int) (chargeRatio * 24);

        new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                if (!owner.isOnline()) {
                    cancel();
                    return;
                }

                if (tick >= trailTicks) {
                    owner.getWorld().spawnParticle(Particle.SMOKE_LARGE, owner.getLocation().clone().add(0, 0.5, 0), 8, 0.4, 0.2, 0.4, 0.02);
                    cancel();
                    return;
                }

                Location loc = owner.getLocation().clone().add(0, 0.5, 0);

                // cloud base + colored streaks
                owner.getWorld().spawnParticle(Particle.CLOUD, loc, 6 + (int)(chargeRatio*6), 0.2, 0.2, 0.2, 0.02);
                owner.getWorld().spawnParticle(Particle.REDSTONE, loc, 2 + (int)(chargeRatio*8), 0.15, 0.15, 0.15, 0, blue);
                owner.getWorld().spawnParticle(Particle.REDSTONE, loc.clone().add(0, 0.15, 0), 1 + (int)(chargeRatio*6), 0.12, 0.12, 0.12, 0, yellow);
                // occasional sparks
                if (Math.random() < 0.35 + 0.5*chargeRatio) {
                    owner.getWorld().spawnParticle(Particle.CRIT, loc, 2, 0.1, 0.1, 0.1, 0.02);
                }

                tick++;
            }
        }.runTaskTimer(AbilityPlugin.getInstance(), 0L, 1L);
    }
}