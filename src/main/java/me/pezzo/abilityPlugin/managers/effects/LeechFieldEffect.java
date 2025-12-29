package me.pezzo.abilityPlugin.managers.effects;

import me.pezzo.abilityPlugin.AbilityPlugin;
import me.pezzo.abilityPlugin.config.data.ability.LeechFieldData;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Particle.DustOptions;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public class LeechFieldEffect {

    private final Player owner;
    private final Location center;
    private final LeechFieldData data;

    public LeechFieldEffect(Player owner, LeechFieldData data) {
        this.owner = owner;
        this.center = owner.getLocation().clone().add(0, 0.5, 0);
        this.data = data;
    }

    public void start() {
        owner.getWorld().playSound(center, Sound.ENTITY_EVOKER_PREPARE_SUMMON, 1f, 1.0f);

        DustOptions redDust = new DustOptions(Color.fromRGB(255, 30, 30), 1.0f);

        new BukkitRunnable() {
            int tick = 0;
            final int radiusBlocks = Math.max(1, (int) Math.ceil(data.getRadius()));

            @Override
            public void run() {
                if (!owner.isOnline() || owner.isDead()) {
                    cancel();
                    return;
                }

                if (tick >= data.getDurationTicks()) {
                    center.getWorld().spawnParticle(Particle.EXPLOSION_NORMAL, center.clone(), 6, 0.6, 0.6, 0.6, 0.0);
                    center.getWorld().playSound(center, Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.0f);
                    cancel();
                    return;
                }


                if (tick % 2 == 0) drawSquareParticles(redDust);
                if (tick % data.getTickInterval() == 0) {
                    for (Entity e : center.getWorld().getNearbyEntities(center, data.getRadius(), data.getRadius(), data.getRadius())) {
                        if (e == null || e.isDead()) continue;
                        if (!(e instanceof LivingEntity)) continue;
                        if (e.equals(owner)) continue;

                        Location eloc = e.getLocation();
                        double dx = Math.abs(eloc.getX() - center.getX());
                        double dz = Math.abs(eloc.getZ() - center.getZ());
                        if (dx > data.getRadius() || dz > data.getRadius()) continue;

                        LivingEntity le = (LivingEntity) e;

                        Vector preVel = le.getVelocity().clone();

                        double beforeHealth = le.getHealth();
                        le.damage(data.getDamagePerTick(), owner);

                        Vector afterVel = le.getVelocity();
                        double k = Math.max(0.0, Math.min(1.0, data.getKnockbackReduce()));
                        afterVel.multiply(k);
                        le.setVelocity(afterVel);

                        double actualDamage = Math.max(0.0, beforeHealth - (le.isDead() ? 0.0 : le.getHealth()));
                        double heal = actualDamage * 0.5;
                        if (heal > 0.0 && owner.isValid()) {
                            double newHealth = Math.min(owner.getMaxHealth(), owner.getHealth() + heal);
                            owner.setHealth(newHealth);
                        }


                        int slownessAmplifier = Math.max(0, data.getSlownessLevel() - 1);
                        int slownessDuration = Math.max(1, data.getTickInterval() * 2);
                        le.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, slownessDuration, slownessAmplifier, false, false, false));

                        spawnSuckParticles(le);

                        le.getWorld().playSound(le.getLocation(), Sound.ENTITY_WITHER_SKELETON_HURT, 0.45f, 1.2f);
                    }
                } else {
                    if (tick % 4 == 0) {
                        for (Entity e : center.getWorld().getNearbyEntities(center, data.getRadius(), data.getRadius(), data.getRadius())) {
                            if (!(e instanceof LivingEntity)) continue;
                            if (e.equals(owner)) continue;
                            Location el = e.getLocation().clone().add(0, 0.6, 0);
                            center.getWorld().spawnParticle(Particle.SPELL_WITCH, el, 2, 0.15, 0.15, 0.15, 0.01);
                        }
                    }
                }

                tick++;
            }

            private void drawSquareParticles(DustOptions dust) {
                double r = data.getRadius();
                double step = Math.max(0.3, (r * 2) / Math.max(8, (int) (r * 6)));

                for (double x = -r; x <= r; x += step) {
                    Location loc = center.clone().add(x, 0.5, r);
                    center.getWorld().spawnParticle(Particle.REDSTONE, loc, 1, 0, 0, 0, 0, dust);
                }
                for (double x = -r; x <= r; x += step) {
                    Location loc = center.clone().add(x, 0.5, -r);
                    center.getWorld().spawnParticle(Particle.REDSTONE, loc, 1, 0, 0, 0, 0, dust);
                }
                for (double z = -r; z <= r; z += step) {
                    Location loc = center.clone().add(r, 0.5, z);
                    center.getWorld().spawnParticle(Particle.REDSTONE, loc, 1, 0, 0, 0, 0, dust);
                }
                for (double z = -r; z <= r; z += step) {
                    Location loc = center.clone().add(-r, 0.5, z);
                    center.getWorld().spawnParticle(Particle.REDSTONE, loc, 1, 0, 0, 0, 0, dust);
                }
            }

            private void spawnSuckParticles(LivingEntity target) {
                Location tloc = target.getLocation().clone().add(0, 0.6, 0);
                Vector dir = center.toVector().subtract(tloc.toVector());
                double dist = Math.max(0.1, dir.length());
                dir.normalize();

                int points = 4;
                for (int i = 1; i <= points; i++) {
                    double factor = (double) i / (points + 1);
                    Location loc = tloc.clone().add(dir.clone().multiply(factor * dist));
                    center.getWorld().spawnParticle(Particle.SPELL_WITCH, loc, 3, 0.12, 0.12, 0.12, 0.01);
                    center.getWorld().spawnParticle(Particle.REDSTONE, loc.clone().add(0, 0.15, 0), 1, 0, 0, 0, 0, new DustOptions(Color.fromRGB(200, 40, 40), 0.9f));
                }
            }

        }.runTaskTimer(AbilityPlugin.getInstance(), 0L, 1L);
    }
}