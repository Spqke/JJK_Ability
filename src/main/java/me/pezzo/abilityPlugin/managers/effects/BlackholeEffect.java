package me.pezzo.abilityPlugin.managers.effects;

import me.pezzo.abilityPlugin.AbilityPlugin;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

/**
 * Enhanced BlackholeEffect with mixed black + red visuals:
 * - hollow shell (BLACK_STAINED_GLASS) with occasional red accents (RED_STAINED_GLASS)
 * - rotating arms (POLISHED_BLACKSTONE) with red highlights (RED_CONCRETE)
 * - dense core particles mixing dark and red dust for "red glow"
 * - pull + periodic damage remain as before
 * - all temporary block changes are restored at the end
 */
public class BlackholeEffect {

    private final Player owner;
    private final Location center;
    private final double damageValue;
    private final double rangeValue;

    private static final int DURATION_TICKS = 400;
    private static final int DAMAGE_INTERVAL_TICKS = 40;
    private static final double MAX_PULL = 3.5;

    // materiali visivi
    private final Material shellMaterial = Material.BLACK_STAINED_GLASS;
    private final Material shellAccentMaterial = Material.RED_STAINED_GLASS;
    private final Material armMaterial = Material.POLISHED_BLACKSTONE;
    private final Material armAccentMaterial = Material.RED_CONCRETE;

    // Track dei blocchi modificati per restore
    private final Map<BlockKey, BlockSnapshot> modifiedBlocks = new HashMap<>();
    private final Set<BlockKey> currentShellBlocks = new HashSet<>();
    private final Set<BlockKey> currentArmBlocks = new HashSet<>();

    private static final class BlockSnapshot {
        final Material material;
        final BlockData data;
        BlockSnapshot(Material material, BlockData data) {
            this.material = material;
            this.data = data;
        }
    }

    private static final class BlockKey {
        final UUID world;
        final int x, y, z;
        BlockKey(World w, int x, int y, int z) {
            this.world = w.getUID();
            this.x = x; this.y = y; this.z = z;
        }
        Location toLocation() {
            World w = AbilityPlugin.getInstance().getServer().getWorld(world);
            return w == null ? null : new Location(w, x, y, z);
        }
        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof BlockKey)) return false;
            BlockKey b = (BlockKey) o;
            return x==b.x && y==b.y && z==b.z && Objects.equals(world, b.world);
        }
        @Override public int hashCode() { return Objects.hash(world, x, y, z); }
    }

    public BlackholeEffect(Player owner, Location center, double damageValue, double rangeValue) {
        this.owner = owner;
        this.center = center.clone();
        this.damageValue = damageValue;
        this.rangeValue = Math.max(1.0, rangeValue);
    }

    public void start() {
        center.getWorld().playSound(center, Sound.ENTITY_WITHER_SPAWN, 1f, 0.7f);

        new BukkitRunnable() {
            int tick = 0;
            final Random rnd = new Random();

            @Override
            public void run() {
                if (!owner.isOnline()) {
                    restoreAllModifiedBlocks();
                    cancel();
                    return;
                }

                if (tick >= DURATION_TICKS) {
                    // finale: esplosione visiva, suono e restore dei blocchi
                    center.getWorld().spawnParticle(Particle.EXPLOSION_HUGE, center.clone(), 6, 0.8, 0.8, 0.8, 0);
                    center.getWorld().playSound(center, Sound.ENTITY_WITHER_DEATH, 1f, 0.6f);
                    restoreAllModifiedBlocks();
                    cancel();
                    return;
                }

                // Visual: core particles con mix nero/rosso + spirale rossa
                spawnCoreParticles(tick, rnd);

                // Disegna shell (hollow sphere) - superficie solo, per ridurre il numero di blocchi
                Set<BlockKey> newShell = computeHollowSphereKeys(center, rangeValue, 0.6);

                // Aggiorna shell: con accenti rossi casuali per punti "più caldi"
                updateTemporaryBlocksWithAccent(currentShellBlocks, newShell, shellMaterial, shellAccentMaterial, 0.12, rnd);

                // Disegna bracci rotanti del vortice con accenti rossi
                Set<BlockKey> newArms = computeRotatingArmsKeys(center, rangeValue, tick);
                updateTemporaryBlocksWithAccent(currentArmBlocks, newArms, armMaterial, armAccentMaterial, 0.18, rnd);

                // Pull delle entità e danno periodico
                for (Entity e : center.getWorld().getNearbyEntities(center, rangeValue, rangeValue, rangeValue)) {
                    if (e.equals(owner) || e.isDead()) continue;
                    Location eloc = e.getLocation().clone();
                    Vector toCenter = center.toVector().subtract(eloc.toVector());
                    double distance = toCenter.length();
                    if (distance <= 0.001) continue;
                    double strengthFactor = 1.0 + ((rangeValue - Math.min(distance, rangeValue)) / rangeValue);
                    double pull = Math.min(1.0 * strengthFactor, MAX_PULL);
                    Vector vel = toCenter.normalize().multiply(pull);
                    vel.setY(Math.max(vel.getY(), -0.5) + 0.12);
                    e.setVelocity(vel);

                    if (tick % DAMAGE_INTERVAL_TICKS == 0 && e instanceof LivingEntity) {
                        ((LivingEntity)e).damage(damageValue, owner);
                    }
                }

                tick++;
            }
        }.runTaskTimer(AbilityPlugin.getInstance(), 0L, 1L);
    }

    private void spawnCoreParticles(int tick, Random rnd) {
        // Core: mix dark + red dust to create a red glow inside the blackness
        Particle.DustOptions darkDust = new Particle.DustOptions(Color.fromRGB(20, 20, 30), 1.8f);
        Particle.DustOptions redDust = new Particle.DustOptions(Color.fromRGB(220, 40, 40), 1.6f);

        // denso intorno al centro: mix di dark e red
        center.getWorld().spawnParticle(Particle.REDSTONE, center.clone(), 120, rangeValue*0.45, rangeValue*0.45, rangeValue*0.45, 0, darkDust);
        center.getWorld().spawnParticle(Particle.REDSTONE, center.clone().add(0, 0.2, 0), 40, rangeValue*0.35, rangeValue*0.35, rangeValue*0.35, 0, redDust);
        center.getWorld().spawnParticle(Particle.SMOKE_LARGE, center.clone(), 70, rangeValue*0.35, rangeValue*0.35, rangeValue*0.35, 0.06);

        // red spiral lines that pulse
        double radius = Math.max(0.8, Math.min(rangeValue*0.6, 6.0));
        for (int i = 0; i < 28; i++) {
            double theta = Math.toRadians((tick * 12.0) + (i * 14.0));
            double r = radius * (1.0 - (i / 28.0) * 0.7);
            double x = Math.cos(theta) * r;
            double z = Math.sin(theta) * r;
            double y = (i % 6) * 0.08 - 0.18;
            Location p = center.clone().add(x, y, z);
            // alternate between red and purple-ish to emphasize the red glow
            center.getWorld().spawnParticle(Particle.SPELL_WITCH, p, 1, 0,0,0, 0.01);
            center.getWorld().spawnParticle(Particle.REDSTONE, p.clone().add(0, 0.2, 0), 1, 0,0,0, 0, (i % 3 == 0) ? redDust : darkDust);
        }

        // occasional red flares + portal-ish particles
        if (tick % 10 == 0) {
            center.getWorld().spawnParticle(Particle.END_ROD, center.clone().add((rnd.nextDouble()-0.5)*rangeValue, (rnd.nextDouble()-0.5)*rangeValue*0.5, (rnd.nextDouble()-0.5)*rangeValue), 10, 0.1,0.1,0.1, 0.01);
            center.getWorld().spawnParticle(Particle.FLAME, center.clone().add(0, 0.3, 0), 6, 0.2, 0.2, 0.2, 0.02);
        }

        // low-frequency heavy bassy ambient
        if (tick % 20 == 0) {
            center.getWorld().playSound(center, Sound.BLOCK_PORTAL_AMBIENT, 0.7f, 0.85f);
            // occasional deep rumble
            if (rnd.nextDouble() < 0.12) center.getWorld().playSound(center, Sound.ENTITY_WITHER_HURT, 0.9f, 0.5f);
        }
    }

    /**
     * Aggiorna i blocchi temporanei come prima, ma con possibilità di mettere un "accent" rosso su alcuni blocchi.
     * accentChance: probabilità [0..1] che un blocco target venga messo con accentMaterial invece di placeMaterial
     */
    private void updateTemporaryBlocksWithAccent(Set<BlockKey> currentSet, Set<BlockKey> targetSet, Material placeMaterial, Material accentMaterial, double accentChance, Random rnd) {
        Set<BlockKey> toRemove = new HashSet<>(currentSet);
        toRemove.removeAll(targetSet);
        // restore usciti
        for (BlockKey key : toRemove) {
            BlockSnapshot snap = modifiedBlocks.get(key);
            Location loc = key.toLocation();
            if (loc == null) continue;
            Block b = loc.getBlock();
            if (snap != null) {
                try {
                    b.setType(snap.material, false);
                    try { b.setBlockData(snap.data, false); } catch (Throwable ignored) {}
                } catch (Throwable ignored) {}
            } else {
                try { b.setType(Material.AIR, false); } catch (Throwable ignored) {}
            }
            currentSet.remove(key);
        }

        // aggiungi nuovi: con possibile accento rosso
        for (BlockKey key : targetSet) {
            if (currentSet.contains(key)) continue;
            Location loc = key.toLocation();
            if (loc == null) continue;
            Block b = loc.getBlock();
            Material orig = b.getType();
            if (orig == Material.BEDROCK) continue;
            // salva snapshot solo la prima volta
            if (!modifiedBlocks.containsKey(key)) {
                try {
                    modifiedBlocks.put(key, new BlockSnapshot(orig, b.getBlockData().clone()));
                } catch (Throwable ex) {
                    modifiedBlocks.put(key, new BlockSnapshot(orig, b.getBlockData()));
                }
            }
            // decide if accent
            Material chosen = (rnd.nextDouble() < accentChance) ? accentMaterial : placeMaterial;
            try {
                b.setType(chosen, false);
                try { b.setBlockData(chosen.createBlockData(), false); } catch (Throwable ignored) {}
            } catch (Throwable ignored) {}
            currentSet.add(key);
        }
    }

    private Set<BlockKey> computeHollowSphereKeys(Location center, double radius, double shellThickness) {
        Set<BlockKey> out = new HashSet<>();
        int cr = (int)Math.ceil(radius);
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();
        double r2 = radius*radius;
        double inner = Math.max(0.5, radius - shellThickness);
        double inner2 = inner*inner;
        for (int x = -cr; x <= cr; x++) {
            for (int y = -cr; y <= cr; y++) {
                for (int z = -cr; z <= cr; z++) {
                    double dx = x + 0.5;
                    double dy = y + 0.5;
                    double dz = z + 0.5;
                    double d2 = dx*dx + dy*dy + dz*dz;
                    if (d2 <= r2 && d2 >= inner2) {
                        out.add(new BlockKey(center.getWorld(), cx + x, cy + y, cz + z));
                    }
                }
            }
        }
        return out;
    }

    private Set<BlockKey> computeRotatingArmsKeys(Location center, double radius, int tick) {
        Set<BlockKey> out = new HashSet<>();
        int arms = Math.max(3, (int)Math.min(6, radius));
        double armLen = Math.max(2.0, radius * 0.9);
        double spin = tick * 0.08;
        for (int a = 0; a < arms; a++) {
            double baseAngle = (a * (2 * Math.PI / arms)) + spin;
            for (double d = 0.6; d <= armLen; d += 0.8) {
                double angle = baseAngle + Math.sin(tick * 0.06 + d) * 0.25;
                double x = Math.cos(angle) * d;
                double z = Math.sin(angle) * d;
                double y = Math.sin(d * 0.6 + tick * 0.06) * 0.6;
                Location pos = center.clone().add(x, y, z);
                out.add(new BlockKey(center.getWorld(), pos.getBlockX(), pos.getBlockY(), pos.getBlockZ()));
            }
        }
        return out;
    }

    private void restoreAllModifiedBlocks() {
        Map<BlockKey, BlockSnapshot> toRestore = new HashMap<>(modifiedBlocks);
        modifiedBlocks.clear();
        currentShellBlocks.clear();
        currentArmBlocks.clear();
        for (Map.Entry<BlockKey, BlockSnapshot> e : toRestore.entrySet()) {
            BlockKey key = e.getKey();
            BlockSnapshot snap = e.getValue();
            Location loc = key.toLocation();
            if (loc == null) continue;
            try {
                Block b = loc.getBlock();
                if (snap.material == Material.AIR) {
                    b.setType(Material.AIR, false);
                } else {
                    b.setType(snap.material, false);
                    try { b.setBlockData(snap.data, false); } catch (Throwable ignored) {}
                }
            } catch (Throwable ex) {
                AbilityPlugin.getInstance().getLogger().warning("Impossibile ripristinare blocco in " + loc + ": " + ex.getMessage());
            }
        }
    }
}