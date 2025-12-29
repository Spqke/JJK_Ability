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

public class BluHollowEffect {

    private final Player owner;
    private Location position;
    private final double damage;
    private final double radius;
    private final double speed;
    private final int durationTicks;
    private final boolean destroyBlocks;
    private Vector velocity = new Vector(0, 0, 0);
    private final double offsetDistance;
    private final Material shellMaterial = Material.BLUE_STAINED_GLASS;


    private final Set<BlockKey> currentShellBlocks = new HashSet<>();

    private final Map<BlockKey, BlockSnapshot> modifiedBlocks = new HashMap<>();

    private static final class BlockSnapshot {
        final Material material;
        final BlockData data;
        final boolean persistent;
        BlockSnapshot(Material material, BlockData data, boolean persistent) {
            this.material = material;
            this.data = data;
            this.persistent = persistent;
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

    public BluHollowEffect(Player owner, Location start, Vector initialDirection, double damage, double radius, double speed, int durationTicks, boolean destroyBlocks, double anchorDistance) {
        this.owner = owner;
        this.offsetDistance = Math.max(0.1, anchorDistance);
        Location eye = owner.getEyeLocation();
        Vector look = eye.getDirection().normalize();
        this.position = eye.clone().add(look.multiply(this.offsetDistance));
        this.damage = damage;
        this.radius = Math.max(0.5, radius);
        this.speed = Math.max(0.05, speed);
        this.durationTicks = Math.max(1, durationTicks);
        this.destroyBlocks = destroyBlocks;
        this.velocity = initialDirection == null ? new Vector(0,0,0) : initialDirection.clone().normalize().multiply(this.speed);
    }

    public void start() {
        position.getWorld().playSound(position, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1.2f, 0.7f);

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

                if (tick >= durationTicks) {
                    position.getWorld().spawnParticle(Particle.EXPLOSION_LARGE, position.clone(), 6, 0.8, 0.8, 0.8, 0.0);
                    position.getWorld().playSound(position, Sound.ENTITY_GENERIC_EXPLODE, 1.1f, 0.6f);
                    restoreAllModifiedBlocks();
                    cancel();
                    return;
                }
                try {
                    Location eye = owner.getEyeLocation();
                    Vector look = eye.getDirection().normalize();
                    position = eye.clone().add(look.multiply(offsetDistance));
                } catch (Throwable t) {
                    position.add(velocity);
                }

                spawnInternalParticles(tick);

                Set<BlockKey> newShell = computeShellBlockKeys(position, radius);

                Set<BlockKey> toRemoveShell = new HashSet<>(currentShellBlocks);
                toRemoveShell.removeAll(newShell);
                for (BlockKey key : toRemoveShell) {
                    BlockSnapshot snap = modifiedBlocks.get(key);
                    Location loc = key.toLocation();
                    if (loc == null) continue;
                    Block b = loc.getBlock();
                    if (snap != null && snap.persistent) {
                        try { b.setType(Material.AIR, false); } catch (Throwable ignored) {}
                    } else {
                        if (snap != null) {
                            try {
                                b.setType(snap.material, false);
                                try { b.setBlockData(snap.data, false); } catch (Throwable ignored) {}
                            } catch (Throwable ignored) {}
                        } else {
                            try { b.setType(Material.AIR, false); } catch (Throwable ignored) {}
                        }
                    }
                }

                for (BlockKey key : newShell) {
                    Location loc = key.toLocation();
                    if (loc == null) continue;
                    Block b = loc.getBlock();
                    Material orig = b.getType();
                    if (orig == Material.BEDROCK) continue;


                    if (!modifiedBlocks.containsKey(key)) {
                        try {
                            modifiedBlocks.put(key, new BlockSnapshot(orig, b.getBlockData().clone(), false));
                        } catch (Throwable ex) {
                            modifiedBlocks.put(key, new BlockSnapshot(orig, b.getBlockData(), false));
                        }
                    }

                    try {
                        b.setType(shellMaterial, false);
                        try { b.setBlockData(shellMaterial.createBlockData(), false); } catch (Throwable ignored) {}
                    } catch (Throwable ignored) {}
                }

                Set<BlockKey> interior = computeInteriorBlockKeys(position, radius);
                for (BlockKey key : interior) {
                    Location loc = key.toLocation();
                    if (loc == null) continue;
                    Block b = loc.getBlock();
                    Material orig = b.getType();
                    if (orig == Material.BEDROCK) continue;

                    if (!modifiedBlocks.containsKey(key)) {
                        try {
                            modifiedBlocks.put(key, new BlockSnapshot(orig, b.getBlockData().clone(), true));
                        } catch (Throwable ex) {
                            modifiedBlocks.put(key, new BlockSnapshot(orig, b.getBlockData(), true));
                        }
                    } else {
                        BlockSnapshot existing = modifiedBlocks.get(key);
                        if (!existing.persistent) {
                            modifiedBlocks.put(key, new BlockSnapshot(existing.material, existing.data, true));
                        }
                    }

                    try { b.setType(Material.AIR, false); } catch (Throwable ignored) {}
                }

                currentShellBlocks.clear();
                currentShellBlocks.addAll(newShell);

                damageEntitiesInSphere(position, radius);

                tick++;
            }
        }.runTaskTimer(AbilityPlugin.getInstance(), 0L, 1L);
    }

    private void spawnInternalParticles(int tick) {
        int particlesPerTick = 20;
        for (int i = 0; i < particlesPerTick; i++) {
            double u = Math.random() * 2.0 - 1.0;
            double theta = Math.random() * Math.PI * 2.0;
            double r = Math.cbrt(Math.random()) * (radius * 0.7); // distribuirle nel volume
            double x = r * Math.sqrt(1 - u * u) * Math.cos(theta);
            double y = r * u;
            double z = r * Math.sqrt(1 - u * u) * Math.sin(theta);
            Location p = position.clone().add(x, y, z);
            position.getWorld().spawnParticle(Particle.REDSTONE, p, 1, 0, 0, 0, 0, new Particle.DustOptions(Color.fromRGB(90, 170, 255), 0.9f));
        }
        int ringPoints = 14;
        for (int i = 0; i < ringPoints; i++) {
            double ang = (tick * 0.12) + (i * (Math.PI * 2) / ringPoints);
            double rx = Math.cos(ang) * (radius * 0.95);
            double rz = Math.sin(ang) * (radius * 0.95);
            Location p = position.clone().add(rx, Math.sin(tick * 0.08) * 0.2, rz);
            position.getWorld().spawnParticle(Particle.SPELL, p, 1, 0.02, 0.02, 0.02, 0.01);
        }
        if (tick % 10 == 0) {
            position.getWorld().spawnParticle(Particle.END_ROD, position.clone(), 8, radius*0.4, radius*0.4, radius*0.4, 0.01);
        }
    }

    private void damageEntitiesInSphere(Location center, double radius) {
        Set<UUID> damaged = new HashSet<>();
        for (Entity e : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (!(e instanceof LivingEntity)) continue;
            if (e.equals(owner)) continue;
            if (e.isDead()) continue;
            double d = e.getLocation().distance(center);
            if (d <= radius) {
                UUID id = e.getUniqueId();
                if (damaged.add(id)) {
                    LivingEntity le = (LivingEntity) e;
                    le.damage(damage, owner);
                    if (!le.isDead()) {
                        Vector push = velocity.clone().normalize().multiply(0.6);
                        push.setY(Math.max(push.getY(), 0.2));
                        le.setVelocity(push);
                    }
                }
            }
        }
    }

    private Set<BlockKey> computeShellBlockKeys(Location center, double radius) {
        Set<BlockKey> out = new HashSet<>();
        int cr = (int) Math.ceil(radius);
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();
        double r2 = radius * radius;
        double innerThreshold = Math.max(0.6, radius - 0.7);
        double inner2 = innerThreshold * innerThreshold;
        for (int x = -cr; x <= cr; x++) {
            for (int y = -cr; y <= cr; y++) {
                for (int z = -cr; z <= cr; z++) {
                    double dx = x + 0.5;
                    double dy = y + 0.5;
                    double dz = z + 0.5;
                    double dist2 = dx*dx + dy*dy + dz*dz;
                    if (dist2 <= r2 && dist2 >= inner2) {
                        out.add(new BlockKey(center.getWorld(), cx + x, cy + y, cz + z));
                    }
                }
            }
        }
        return out;
    }

    private Set<BlockKey> computeInteriorBlockKeys(Location center, double radius) {
        Set<BlockKey> out = new HashSet<>();
        int cr = (int) Math.ceil(radius);
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();
        double r2 = radius * radius;
        double innerThreshold = Math.max(0.6, radius - 0.7);
        double inner2 = innerThreshold * innerThreshold;
        for (int x = -cr; x <= cr; x++) {
            for (int y = -cr; y <= cr; y++) {
                for (int z = -cr; z <= cr; z++) {
                    double dx = x + 0.5;
                    double dy = y + 0.5;
                    double dz = z + 0.5;
                    double dist2 = dx*dx + dy*dy + dz*dz;
                    if (dist2 < inner2) {
                        out.add(new BlockKey(center.getWorld(), cx + x, cy + y, cz + z));
                    }
                }
            }
        }
        return out;
    }

    private void restoreAllModifiedBlocks() {
        Map<BlockKey, BlockSnapshot> toRestore = new HashMap<>(modifiedBlocks);
        modifiedBlocks.clear();
        currentShellBlocks.clear();

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