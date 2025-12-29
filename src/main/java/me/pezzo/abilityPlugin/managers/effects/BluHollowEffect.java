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
 * BluHollowEffect: singola sfera visuale semi-trasparente (glass shell) con particelle interne animate.
 * - Quando la sfera tocca blocchi, li distrugge (imposta AIR) e salva lo stato originale per ripristinarli alla fine.
 * - La shell di vetro è visiva e si sposta con la view; non lascia tracce di vetro dietro (viene ripristinata mentre si muove).
 * - Tutte le modifiche vengono ripristinate al termine dell'effetto (o se il giocatore disconnette).
 */
public class BluHollowEffect {

    private final Player owner;
    private Location position;
    private final double damage;
    private final double radius;
    private final double speed;
    private final int durationTicks;
    private final boolean destroyBlocks; // se true, i blocchi interni vengono impostati ad AIR (distrutti) fino al restore finale
    private Vector velocity = new Vector(0, 0, 0);
    private final double offsetDistance; // distanza fissa dagli occhi del giocatore (la sfera è ancorata alla view)
    private final Material shellMaterial = Material.BLUE_STAINED_GLASS; // materiale della shell semi-trasparente

    // Blocchi che attualmente formano la shell (da ripristinare quando la shell si sposta)
    private final Set<BlockKey> currentShellBlocks = new HashSet<>();

    // Mappa dei blocchi modificati permanentemente (salvati per restore finale).
    // Contiene sia blocchi "distrutti" (persistenti) che blocchi originali sovrascritti dalla shell la prima volta.
    private final Map<BlockKey, BlockSnapshot> modifiedBlocks = new HashMap<>();

    // Snapshot con flag persistent: se true => blocco distrutto (deve rimanere AIR fino al restore finale)
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

    // chiave immutabile per identificare blocchi nelle mappe (evita problemi con Location mutabile)
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

                // aggiorna centro sfera in base alla view del giocatore
                try {
                    Location eye = owner.getEyeLocation();
                    Vector look = eye.getDirection().normalize();
                    position = eye.clone().add(look.multiply(offsetDistance));
                } catch (Throwable t) {
                    position.add(velocity);
                }

                // anima particelle interne (random swirl + ring)
                spawnInternalParticles(tick);

                // calcola il set di blocchi shell target per il centro corrente
                Set<BlockKey> newShell = computeShellBlockKeys(position, radius);

                // RESTORE dei blocchi che facevano parte della shell precedente ma non della nuova shell:
                // se il blocco è stato salvato come "persistent" allora deve rimanere AIR (è stato distrutto),
                // altrimenti ripristiniamo lo stato originale.
                Set<BlockKey> toRemoveShell = new HashSet<>(currentShellBlocks);
                toRemoveShell.removeAll(newShell);
                for (BlockKey key : toRemoveShell) {
                    // se il blocco è nella modifiedBlocks e persistent -> impostiamo AIR (distrutto)
                    BlockSnapshot snap = modifiedBlocks.get(key);
                    Location loc = key.toLocation();
                    if (loc == null) continue;
                    Block b = loc.getBlock();
                    if (snap != null && snap.persistent) {
                        // Lasciamo AIR (distrutto)
                        try { b.setType(Material.AIR, false); } catch (Throwable ignored) {}
                    } else {
                        // ripristina allo stato originale (se è stato salvato) oppure metti AIR se non salvato
                        if (snap != null) {
                            try {
                                b.setType(snap.material, false);
                                try { b.setBlockData(snap.data, false); } catch (Throwable ignored) {}
                            } catch (Throwable ignored) {}
                            // se non persistent e già ripristinato, manteniamo snapshot per il restore finale (non necessario, ma ok)
                        } else {
                            // se non avevamo snapshot (caso raro), lasciamo AIR
                            try { b.setType(Material.AIR, false); } catch (Throwable ignored) {}
                        }
                    }
                }

                // Applichiamo la nuova shell (salvando snapshots se necessario). Inoltre, distruggiamo i blocchi interni persistenti.
                for (BlockKey key : newShell) {
                    Location loc = key.toLocation();
                    if (loc == null) continue;
                    Block b = loc.getBlock();
                    Material orig = b.getType();
                    if (orig == Material.BEDROCK) continue;

                    // salva snapshot se prima non esiste
                    if (!modifiedBlocks.containsKey(key)) {
                        try {
                            modifiedBlocks.put(key, new BlockSnapshot(orig, b.getBlockData().clone(), false));
                        } catch (Throwable ex) {
                            // in casi rari clone() può fallare, salviamo dati base
                            modifiedBlocks.put(key, new BlockSnapshot(orig, b.getBlockData(), false));
                        }
                    }

                    // imposta shell (vetro) sopra il blocco; se il blocco era stato precedentemente segnato persistent true,
                    // lo sovrascriviamo visivamente con vetro (al leaving della shell tornerà ad AIR perché persistent)
                    try {
                        b.setType(shellMaterial, false);
                        try { b.setBlockData(shellMaterial.createBlockData(), false); } catch (Throwable ignored) {}
                    } catch (Throwable ignored) {}
                }

                // ORA: gestiamo i blocchi interni alla sfera (distruzione persistente).
                // Per ogni blocco nel volume (non solo shell) salviamo snapshot se necessario e impostiamo AIR.
                // Questo garantisce che la sfera "distrugga tutto quello che tocca".
                Set<BlockKey> interior = computeInteriorBlockKeys(position, radius);
                for (BlockKey key : interior) {
                    Location loc = key.toLocation();
                    if (loc == null) continue;
                    Block b = loc.getBlock();
                    Material orig = b.getType();
                    if (orig == Material.BEDROCK) continue;

                    // se non salvato, salviamo con flag persistent=true
                    if (!modifiedBlocks.containsKey(key)) {
                        try {
                            modifiedBlocks.put(key, new BlockSnapshot(orig, b.getBlockData().clone(), true));
                        } catch (Throwable ex) {
                            modifiedBlocks.put(key, new BlockSnapshot(orig, b.getBlockData(), true));
                        }
                    } else {
                        // se esiste ma era snapshot non-persistent (per esempio la shell lo aveva salvato prima),
                        // lo convertiamo in persistente solo se non era già persistente
                        BlockSnapshot existing = modifiedBlocks.get(key);
                        if (!existing.persistent) {
                            // sostituiamo con persistent=true mantenendo il materiale/data originali
                            modifiedBlocks.put(key, new BlockSnapshot(existing.material, existing.data, true));
                        }
                    }

                    // imposta AIR (distrugge il blocco)
                    try { b.setType(Material.AIR, false); } catch (Throwable ignored) {}
                }

                // Aggiorna current shell
                currentShellBlocks.clear();
                currentShellBlocks.addAll(newShell);

                // Danno: infligge danno alle entità che stanno dentro la sfera (una volta per tick)
                damageEntitiesInSphere(position, radius);

                tick++;
            }
        }.runTaskTimer(AbilityPlugin.getInstance(), 0L, 1L);
    }

    private void spawnInternalParticles(int tick) {
        // particelle animate all'interno della sfera: swirl + occasional flare
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
        // un anello rotante
        int ringPoints = 14;
        for (int i = 0; i < ringPoints; i++) {
            double ang = (tick * 0.12) + (i * (Math.PI * 2) / ringPoints);
            double rx = Math.cos(ang) * (radius * 0.95);
            double rz = Math.sin(ang) * (radius * 0.95);
            Location p = position.clone().add(rx, Math.sin(tick * 0.08) * 0.2, rz);
            position.getWorld().spawnParticle(Particle.SPELL, p, 1, 0.02, 0.02, 0.02, 0.01);
        }
        // occasional flare
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
        double innerThreshold = Math.max(0.6, radius - 0.7); // sotto questa distanza consideriamo interno
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
        double innerThreshold = Math.max(0.6, radius - 0.7); // consideriamo interno distanza < innerThreshold
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

    /**
     * Ripristina finalmente tutti i blocchi modificati al loro stato originale.
     */
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