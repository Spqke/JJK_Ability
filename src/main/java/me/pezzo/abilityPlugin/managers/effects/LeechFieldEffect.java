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

import java.util.UUID;

/**
 * LeechField effect:
 * - disegna un quadrato (particelle rosse) attorno al centro
 * - ogni tick_interval infligge danno alle entità dentro il quadrato
 * - applica Slowness e riduce/annulla knockback secondo la config
 * - particelle "succhiamento" intorno alle entità colpite
 */
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

        // DustOptions per particelle rosse
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
                    // effetto finale
                    center.getWorld().spawnParticle(Particle.EXPLOSION_NORMAL, center.clone(), 6, 0.6, 0.6, 0.6, 0.0);
                    center.getWorld().playSound(center, Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.0f);
                    cancel();
                    return;
                }

                // disegna il quadrato ogni 2 tick per performance
                if (tick % 2 == 0) drawSquareParticles(redDust);

                // ogni tick_interval applica danno, slowness e particelle di succhiamento
                if (tick % data.getTickInterval() == 0) {
                    for (Entity e : center.getWorld().getNearbyEntities(center, data.getRadius(), data.getRadius(), data.getRadius())) {
                        if (e == null || e.isDead()) continue;
                        if (!(e instanceof LivingEntity)) continue;
                        if (e.equals(owner)) continue;

                        // controlla che sia dentro il quadrato (delta X/Z)
                        Location eloc = e.getLocation();
                        double dx = Math.abs(eloc.getX() - center.getX());
                        double dz = Math.abs(eloc.getZ() - center.getZ());
                        if (dx > data.getRadius() || dz > data.getRadius()) continue;

                        LivingEntity le = (LivingEntity) e;

                        // salva velocity pre-danno
                        Vector preVel = le.getVelocity().clone();

                        double beforeHealth = le.getHealth();
                        // infligge danno (usa damage per applicare source)
                        le.damage(data.getDamagePerTick(), owner);

                        // riduci knockback applicando moltiplicatore sulla velocity risultante
                        Vector afterVel = le.getVelocity();
                        // se knockbackReduce == 0 -> azzera knockback; if 1 -> lascia invariato
                        double k = Math.max(0.0, Math.min(1.0, data.getKnockbackReduce()));
                        afterVel.multiply(k);
                        le.setVelocity(afterVel);

                        // cura il caster della metà del danno effettivamente inflitto
                        double actualDamage = Math.max(0.0, beforeHealth - (le.isDead() ? 0.0 : le.getHealth()));
                        double heal = actualDamage * 0.5;
                        if (heal > 0.0 && owner.isValid()) {
                            double newHealth = Math.min(owner.getMaxHealth(), owner.getHealth() + heal);
                            owner.setHealth(newHealth);
                        }

                        // applica Slowness: durata tick_interval * 2 per sicurezza (amplifier = level-1)
                        int slownessAmplifier = Math.max(0, data.getSlownessLevel() - 1);
                        int slownessDuration = Math.max(1, data.getTickInterval() * 2);
                        le.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, slownessDuration, slownessAmplifier, false, false, false));

                        // particelle di "aspirazione" tra entity e centro
                        spawnSuckParticles(le);

                        // suono leggero
                        le.getWorld().playSound(le.getLocation(), Sound.ENTITY_WITHER_SKELETON_HURT, 0.45f, 1.2f);
                    }
                } else {
                    // particelle leggere attorno alle entità dentro il quadrato, ogni 4 tick
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
                // quadrato centrato su center con raggio "data.getRadius()"
                double r = data.getRadius();
                // distanza step tra particelle: calcoliamo un passo dipendente dalla dimensione
                double step = Math.max(0.3, (r * 2) / Math.max(8, (int) (r * 6)));

                // linee parallele agli assi: x vary, z = +/-r ; z vary, x = +/-r
                // bordo superiore (z = +r)
                for (double x = -r; x <= r; x += step) {
                    Location loc = center.clone().add(x, 0.5, r);
                    center.getWorld().spawnParticle(Particle.REDSTONE, loc, 1, 0, 0, 0, 0, dust);
                }
                // bordo inferiore (z = -r)
                for (double x = -r; x <= r; x += step) {
                    Location loc = center.clone().add(x, 0.5, -r);
                    center.getWorld().spawnParticle(Particle.REDSTONE, loc, 1, 0, 0, 0, 0, dust);
                }
                // bordo destro (x = +r)
                for (double z = -r; z <= r; z += step) {
                    Location loc = center.clone().add(r, 0.5, z);
                    center.getWorld().spawnParticle(Particle.REDSTONE, loc, 1, 0, 0, 0, 0, dust);
                }
                // bordo sinistro (x = -r)
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

                // spawn su 4 punti progressivi tra entity e centro
                int points = 4;
                for (int i = 1; i <= points; i++) {
                    double factor = (double) i / (points + 1);
                    Location loc = tloc.clone().add(dir.clone().multiply(factor * dist));
                    // particella "anima" scura / viola
                    center.getWorld().spawnParticle(Particle.SPELL_WITCH, loc, 3, 0.12, 0.12, 0.12, 0.01);
                    // un piccolo red dust vicino a target per effetto di drenaggio
                    center.getWorld().spawnParticle(Particle.REDSTONE, loc.clone().add(0, 0.15, 0), 1, 0, 0, 0, 0, new DustOptions(Color.fromRGB(200, 40, 40), 0.9f));
                }
            }

        }.runTaskTimer(AbilityPlugin.getInstance(), 0L, 1L);
    }
}