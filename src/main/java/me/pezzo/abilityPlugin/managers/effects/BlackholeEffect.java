package me.pezzo.abilityPlugin.managers.effects;

import me.pezzo.abilityPlugin.AbilityPlugin;
import org.bukkit.Location;
import org.bukkit.Material;
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
 * Enhanced BlackholeEffect:
 * - visualCenter separato dal centro di attrazione (le entità vengono attratte dal centro originale,
 *   mentre la parte visiva del "buco nero" è sollevata)
 * - core visivo multilayer (vetri neri/grigi, concrete scure, polished blackstone) che swirlano
 * - floating blocks distribuiti nella cupola (come prima)
 * - nessuna particella (rimosse per richiesta)
 * - suoni ambient leggeri rimangono
 *
 * Tutti i blocchi temporanei vengono ripristinati al termine.
 */
public class BlackholeEffect {

    private final Player owner;
    private final Location center;        // centro LOGICO (attrazione / danno)
    private final double damageValue;
    private final double rangeValue;

    private static final int DURATION_TICKS = 400;
    private static final int DAMAGE_INTERVAL_TICKS = 40;
    private static final double MAX_PULL = 3.5;

    // materiali visivi generali
    private final Material shellMaterial = Material.BLACK_STAINED_GLASS;
    private final Material shellAccentMaterial = Material.RED_STAINED_GLASS;
    private final Material armMaterial = Material.POLISHED_BLACKSTONE;
    private final Material armAccentMaterial = Material.RED_CONCRETE;

    // Track dei blocchi modificati per restore
    private final Map<BlockKey, BlockSnapshot> modifiedBlocks = new HashMap<>();
    private final Set<BlockKey> currentShellBlocks = new HashSet<>();
    private final Set<BlockKey> currentArmBlocks = new HashSet<>();
    private final Set<BlockKey> currentFloatingBlocks = new HashSet<>();
    private final Set<BlockKey> currentCoreBlocks = new HashSet<>();

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
        // visual offset factor: quanto sopra il center vogliamo posizionare il buco nero visivo
        final double visualOffsetFactor = Math.min(0.7, 0.9); // fattore moltiplicato per rangeValue (puoi regolare)
        final double visualOffset = Math.min(4.5, Math.max(1.8, rangeValue * 0.45)); // valore effettivo in blocchi

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
                    center.getWorld().playSound(center.clone().add(0, visualOffset, 0), Sound.ENTITY_WITHER_DEATH, 1f, 0.6f);
                    restoreAllModifiedBlocks();
                    cancel();
                    return;
                }

                // calcoliamo il centro visivo sollevato (puoi modificare visualOffset calcolato sopra)
                Location visualCenter = center.clone().add(0.0, visualOffset, 0.0);

                // suoni ambient sul centro visivo
                maybePlayAmbientSounds(tick, rnd, visualCenter);

                // Shell visiva (sfera) attorno al centro visivo
                Set<BlockKey> newShell = computeHollowSphereKeys(visualCenter, rangeValue, 0.6);
                updateTemporaryBlocksWithAccent(currentShellBlocks, newShell, shellMaterial, shellAccentMaterial, 0.12, rnd);

                // Bracci rotanti attorno al centro visivo
                Set<BlockKey> newArms = computeRotatingArmsKeys(visualCenter, rangeValue, tick);
                updateTemporaryBlocksWithAccent(currentArmBlocks, newArms, armMaterial, armAccentMaterial, 0.18, rnd);

                // Floating blocks dentro la cupola attorno al centro visivo
                Set<BlockKey> newFloating = computeFloatingOrbitKeys(visualCenter, rangeValue, tick, rnd);
                updateTemporaryBlocksWithAccent(currentFloatingBlocks, newFloating, shellMaterial, shellAccentMaterial, 0.5, rnd);

                // Core visivo sollevato: multilayer swirl concentric
                double coreRadius = Math.max(1.0, Math.min(4.0, rangeValue * 0.32));
                Set<BlockKey> newCore = computeCoreSwirlKeys(visualCenter, coreRadius, tick, rnd);
                Material[] corePalette = new Material[] {
                        Material.BLACK_STAINED_GLASS,
                        Material.GRAY_STAINED_GLASS,
                        Material.LIGHT_GRAY_STAINED_GLASS,
                        Material.BLACK_CONCRETE,
                        Material.GRAY_CONCRETE,
                        Material.POLISHED_BLACKSTONE
                };
                updateTemporaryBlocksWithPalette(currentCoreBlocks, newCore, corePalette, rnd);

                // Pull delle entità: USIAMO il centro LOGICO (non il visualCenter) così entità vengono attratte dal punto "reale"
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

    // suoni ambient sul centro visivo
    private void maybePlayAmbientSounds(int tick, Random rnd, Location visualCenter) {
        if (tick % 20 == 0) {
            visualCenter.getWorld().playSound(visualCenter, Sound.BLOCK_PORTAL_AMBIENT, 0.7f, 0.85f);
            if (rnd.nextDouble() < 0.12) visualCenter.getWorld().playSound(visualCenter, Sound.ENTITY_WITHER_HURT, 0.9f, 0.5f);
        }
        if (tick % 40 == 0 && rnd.nextDouble() < 0.08) {
            visualCenter.getWorld().playSound(visualCenter, Sound.ENTITY_ENDER_DRAGON_FLAP, 0.5f, 0.95f);
        }
    }

    private void updateTemporaryBlocksWithAccent(Set<BlockKey> currentSet, Set<BlockKey> targetSet, Material placeMaterial, Material accentMaterial, double accentChance, Random rnd) {
        Set<BlockKey> toRemove = new HashSet<>(currentSet);
        toRemove.removeAll(targetSet);
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

        for (BlockKey key : targetSet) {
            if (currentSet.contains(key)) continue;
            Location loc = key.toLocation();
            if (loc == null) continue;
            Block b = loc.getBlock();
            Material orig = b.getType();
            if (orig == Material.BEDROCK) continue;
            if (!modifiedBlocks.containsKey(key)) {
                try {
                    modifiedBlocks.put(key, new BlockSnapshot(orig, b.getBlockData().clone()));
                } catch (Throwable ex) {
                    modifiedBlocks.put(key, new BlockSnapshot(orig, b.getBlockData()));
                }
            }
            Material chosen = (rnd.nextDouble() < accentChance) ? accentMaterial : placeMaterial;
            try {
                b.setType(chosen, false);
                try { b.setBlockData(chosen.createBlockData(), false); } catch (Throwable ignored) {}
            } catch (Throwable ignored) {}
            currentSet.add(key);
        }
    }

    private void updateTemporaryBlocksWithPalette(Set<BlockKey> currentSet, Set<BlockKey> targetSet, Material[] palette, Random rnd) {
        Set<BlockKey> toRemove = new HashSet<>(currentSet);
        toRemove.removeAll(targetSet);
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

        for (BlockKey key : targetSet) {
            if (currentSet.contains(key)) continue;
            Location loc = key.toLocation();
            if (loc == null) continue;
            Block b = loc.getBlock();
            Material orig = b.getType();
            if (orig == Material.BEDROCK) continue;
            if (!modifiedBlocks.containsKey(key)) {
                try {
                    modifiedBlocks.put(key, new BlockSnapshot(orig, b.getBlockData().clone()));
                } catch (Throwable ex) {
                    modifiedBlocks.put(key, new BlockSnapshot(orig, b.getBlockData()));
                }
            }
            Material chosen = palette[rnd.nextInt(palette.length)];
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

    private Set<BlockKey> computeFloatingOrbitKeys(Location center, double radius, int tick, Random rnd) {
        Set<BlockKey> out = new HashSet<>();
        int totalOrbs = Math.max(8, (int)Math.min(36, Math.ceil(radius * 4)));
        double baseRadius = Math.max(0.8, radius * 0.6);

        double minYFactor = -0.1;
        double maxYFactor = 0.9;

        for (int i = 0; i < totalOrbs; i++) {
            double speedFactor = 0.03 + (i % 5) * 0.007;
            double angle = (tick * speedFactor) + (i * (2 * Math.PI / totalOrbs)) + Math.sin(i * 0.37) * 0.2;
            double pulse = 0.7 + 0.3 * Math.sin(tick * 0.02 + i);
            double orbitR = baseRadius * (0.4 + (i % 6) * 0.08) * pulse;
            double x = Math.cos(angle) * orbitR;
            double z = Math.sin(angle) * orbitR;
            double layerFactor = ((i % Math.max(1, totalOrbs/6)) / (double)Math.max(1, (totalOrbs/6)));
            double yBase = minYFactor + layerFactor * (maxYFactor - minYFactor);
            double yOsc = Math.sin(tick * (0.04 + (i % 3) * 0.01) + i * 0.5) * (radius * 0.35);
            double y = (yBase * radius) + yOsc;
            Location pos = center.clone().add(x, y, z);
            out.add(new BlockKey(center.getWorld(), pos.getBlockX(), pos.getBlockY(), pos.getBlockZ()));
            if (rnd.nextDouble() < 0.12) {
                double sx = x + (rnd.nextDouble()-0.5)*0.6;
                double sz = z + (rnd.nextDouble()-0.5)*0.6;
                double sy = y + (rnd.nextDouble()-0.5)*0.5;
                Location p2 = center.clone().add(sx, sy, sz);
                out.add(new BlockKey(center.getWorld(), p2.getBlockX(), p2.getBlockY(), p2.getBlockZ()));
            }
        }
        return out;
    }

    /**
     * Core swirl: genera più anelli concentrici con rotazione e pulsazione.
     * Distribuisce blocchi in piccoli cluster vicino al centro visivo per dare densità.
     */
    private Set<BlockKey> computeCoreSwirlKeys(Location center, double coreRadius, int tick, Random rnd) {
        Set<BlockKey> out = new HashSet<>();

        int rings = 3 + (int)Math.floor(coreRadius); // numero anelli
        for (int ring = 0; ring < rings; ring++) {
            double ringFactor = 1.0 - (ring / (double)rings) * 0.85; // anelli più interni sono più densi
            double ringR = coreRadius * (0.12 + ringFactor * 0.6);
            int points = Math.max(10, (int)(12 + coreRadius * 8 * ringFactor));
            double ringSpin = tick * (0.18 + ring * 0.06) * (ring % 2 == 0 ? 1.0 : -1.0);

            for (int p = 0; p < points; p++) {
                double baseAngle = (p * (2 * Math.PI / points)) + ringSpin + Math.sin(p * 0.37 + tick * 0.06) * 0.25;
                double radiusJitter = ringR * (0.7 + 0.6 * Math.sin(tick * 0.03 + p));
                double x = Math.cos(baseAngle) * radiusJitter;
                double z = Math.sin(baseAngle) * radiusJitter;

                // stack verticale con lieve offset per spessore del core
                double y = (Math.sin(tick * 0.09 + p * 0.21) * 0.18) + (ring - rings/2.0) * 0.14;
                // jitter per evitare pattern regolari
                double jx = (rnd.nextDouble() - 0.5) * 0.18;
                double jz = (rnd.nextDouble() - 0.5) * 0.18;
                double jy = (rnd.nextDouble() - 0.5) * 0.06;

                Location pos = center.clone().add(x + jx, y + jy, z + jz);
                out.add(new BlockKey(center.getWorld(), pos.getBlockX(), pos.getBlockY(), pos.getBlockZ()));

                // piccoli cluster interni per densità
                if (rnd.nextDouble() < 0.2) {
                    double smallR = Math.random() * (coreRadius * 0.18);
                    double a2 = baseAngle + (Math.random() - 0.5) * 0.7;
                    Location p2 = center.clone().add(Math.cos(a2) * smallR, y + (Math.random()-0.5)*0.06, Math.sin(a2) * smallR);
                    out.add(new BlockKey(center.getWorld(), p2.getBlockX(), p2.getBlockY(), p2.getBlockZ()));
                }
            }
        }

        // aggiungiamo un piccolo "nocciolo" centrale molto scuro (qualche blocco)
        for (int i = 0; i < Math.max(3, (int)(coreRadius * 3)); i++) {
            double ang = rnd.nextDouble() * Math.PI * 2;
            double r = rnd.nextDouble() * (coreRadius * 0.18);
            double x = Math.cos(ang) * r;
            double z = Math.sin(ang) * r;
            double y = (rnd.nextDouble() - 0.5) * 0.12;
            Location p = center.clone().add(x, y, z);
            out.add(new BlockKey(center.getWorld(), p.getBlockX(), p.getBlockY(), p.getBlockZ()));
        }

        return out;
    }

    private void restoreAllModifiedBlocks() {
        Map<BlockKey, BlockSnapshot> toRestore = new HashMap<>(modifiedBlocks);
        modifiedBlocks.clear();
        currentShellBlocks.clear();
        currentArmBlocks.clear();
        currentFloatingBlocks.clear();
        currentCoreBlocks.clear();
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