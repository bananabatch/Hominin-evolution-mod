package dev.hominin.evolution.world;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import dev.hominin.evolution.Attachments;
import dev.hominin.evolution.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * What a stretch of ground is worth: its <b>pressure</b>, 1 to 10. Good stone, a termite super colony,
 * country the big herds graze, and the things nobody tells you about - a breeding ground, fertile soil -
 * all add to it. Worth-having ground is ground other bands want: friends offer to pay for the use of it,
 * enemies want it off you, and a strong band can take it off a weaker one.
 *
 * <ul>
 * <li>Stone: one workable outcrop 1 (more do not add up), a small chert seam 2, a large one 3.</li>
 * <li>A termite super colony 4.</li>
 * <li>Good megafauna ground - open grass with water - 3.</li>
 * <li>A megafauna <b>breeding ground</b> 4 (hidden).</li>
 * <li><b>Fertile</b> soil 2 (hidden).</li>
 * </ul>
 */
public final class Land {
    /** Breeding grounds: one chance in this many regions of 384 blocks. */
    private static final int REGION = 384;
    private static final float BREEDING_CHANCE = 0.35F;
    /** How far a breeding ground's pull reaches. */
    public static final int BREEDING_RADIUS = 48;
    private static final long CACHE_TICKS = 6000L;

    /** One thing that makes the ground worth having. {@code hidden} names a secret, and {@code where} it is. */
    public record Part(String label, int value, @Nullable String hidden, @Nullable BlockPos where) {
    }

    public record Value(int total, List<Part> parts) {
        /** What this player can see of it: what they know, and whether anything is still hidden from them. */
        public List<String> describe(ServerPlayer player) {
            List<String> lines = new ArrayList<>();
            boolean secret = false;
            for (Part part : parts) {
                if (part.hidden() != null && !knows(player, part.hidden())) {
                    secret = true;
                    continue;
                }
                lines.add(part.label() + " (+" + part.value() + ")");
            }
            if (lines.isEmpty()) {
                lines.add("nothing much worth fighting over");
            }
            if (secret) {
                lines.add("and something more you cannot put your finger on");
            }
            return lines;
        }

        public boolean hidesSomethingFrom(ServerPlayer player) {
            for (Part part : parts) {
                if (part.hidden() != null && !knows(player, part.hidden())) {
                    return true;
                }
            }
            return false;
        }
    }

    public static String label(int pressure) {
        return pressure >= 8 ? "rich - everyone wants this" : pressure >= 5 ? "good ground - worth fighting for"
                : pressure >= 3 ? "decent" : "poor - nobody will fight you for it";
    }

    // ------------------------------------------------------------ secrets, and knowing them

    public static boolean knows(ServerPlayer player, String secret) {
        return player.getData(Attachments.PLAYER_EVOLUTION_DATA).getCriterionCounters().getOrDefault("knows_" + secret, 0) > 0;
    }

    public static void learn(ServerPlayer player, String secret) {
        player.getData(Attachments.PLAYER_EVOLUTION_DATA).getCriterionCounters().put("knows_" + secret, 1);
    }

    private static long mix(long seed, long a, long b) {
        long h = seed * 31L + a * 0x9E3779B97F4A7C15L + b * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        return h;
    }

    /** The breeding ground of this region, if it has one: open grass the herds come back to every year. */
    @Nullable
    public static BlockPos breedingGroundIn(ServerLevel level, int regionX, int regionZ) {
        long h = mix(level.getSeed() ^ 0xB12EEDL, regionX, regionZ);
        if ((Math.abs(h) % 1000L) >= (long) (BREEDING_CHANCE * 1000.0F)) {
            return null;
        }
        int x = regionX * REGION + 48 + (int) (Math.abs(h >> 8) % (REGION - 96));
        int z = regionZ * REGION + 48 + (int) (Math.abs(h >> 20) % (REGION - 96));
        BlockPos pos = new BlockPos(x, 64, z);
        var biome = level.getBiome(pos);
        if (!biome.is(BiomeTags.IS_SAVANNA) && !biome.is(net.minecraft.world.level.biome.Biomes.PLAINS)
                && !biome.is(net.minecraft.world.level.biome.Biomes.SUNFLOWER_PLAINS)) {
            return null;
        }
        return pos;
    }

    /** The nearest breeding ground within this reach, if any. */
    @Nullable
    public static BlockPos breedingGroundNear(ServerLevel level, BlockPos pos, int within) {
        int rx = Math.floorDiv(pos.getX(), REGION);
        int rz = Math.floorDiv(pos.getZ(), REGION);
        BlockPos best = null;
        double bestDistance = (double) within * within;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos ground = breedingGroundIn(level, rx + dx, rz + dz);
                if (ground == null) {
                    continue;
                }
                double ddx = ground.getX() - pos.getX();
                double ddz = ground.getZ() - pos.getZ();
                double distance = ddx * ddx + ddz * ddz;
                if (distance <= bestDistance) {
                    bestDistance = distance;
                    best = ground;
                }
            }
        }
        return best;
    }

    public static String breedingKey(BlockPos ground) {
        return "breeding_" + Math.floorDiv(ground.getX(), REGION) + "_" + Math.floorDiv(ground.getZ(), REGION);
    }

    // ------------------------------------------------------------ scoring ground

    private record Cached(Value value, long at) {
    }

    private static final Map<Long, Cached> cache = new HashMap<>();

    /** The pressure of the ground this far round this point - worked out at most every five minutes. */
    public static Value of(ServerLevel level, BlockPos centre, int radius) {
        long key = BlockPos.asLong(centre.getX() >> 3, radius, centre.getZ() >> 3);
        Cached cached = cache.get(key);
        long now = level.getGameTime();
        if (cached != null && now - cached.at() < CACHE_TICKS) {
            return cached.value();
        }
        if (cache.size() > 512) {
            cache.clear();
        }
        Value value = score(level, centre, radius);
        cache.put(key, new Cached(value, now));
        return value;
    }

    /** Your own ground's pressure - or, packed up, the ground you are standing on. */
    public static Value ofPlayer(ServerPlayer player) {
        BlockPos camp = dev.hominin.evolution.hunt.Predation.campOf(player);
        return of(player.serverLevel(), camp, dev.hominin.evolution.hunt.Predation.territoryRadius(player));
    }

    /**
     * Glass in the foot of the outcrop this sample hit. The ground is only sampled every few blocks, and a vein of
     * obsidian is three or four blocks: so round every outcrop hit, the gap to the next sample is looked over too.
     */
    private static boolean glassAround(ServerLevel level, int x, int y, int z, int step) {
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int dx = -step / 2; dx <= step / 2; dx++) {
            for (int dz = -step / 2; dz <= step / 2; dz++) {
                if (!level.hasChunk((x + dx) >> 4, (z + dz) >> 4)) {
                    continue;
                }
                for (int dy = -2; dy <= 3; dy++) {
                    if (level.getBlockState(at.set(x + dx, y + dy, z + dz)).is(ModBlocks.OBSIDIAN_DEPOSIT.get())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static Value score(ServerLevel level, BlockPos centre, int radius) {
        List<Part> parts = new ArrayList<>();
        // Stone: the best of what is there counts, not all of it.
        int chert = 0;
        boolean obsidian = false;
        boolean workable = false;
        boolean water = false;
        int gravel = 0;
        int step = 4;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -radius; dx <= radius; dx += step) {
            for (int dz = -radius; dz <= radius; dz += step) {
                if (dx * dx + dz * dz > radius * radius) {
                    continue;
                }
                int x = centre.getX() + dx;
                int z = centre.getZ() + dz;
                if (!level.hasChunk(x >> 4, z >> 4)) {
                    continue;
                }
                int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                if (!water && level.getFluidState(pos.set(x, top - 1, z)).is(FluidTags.WATER)) {
                    water = true;
                }
                if (level.getBlockState(pos.set(x, top - 1, z)).is(net.minecraft.world.level.block.Blocks.GRAVEL)) {
                    gravel++;
                }
                for (int y = top - 6; y <= top + 1; y++) {
                    BlockState state = level.getBlockState(pos.set(x, y, z));
                    if (state.is(ModBlocks.OBSIDIAN_DEPOSIT.get())) {
                        obsidian = true;
                        break;
                    }
                    if (state.is(ModBlocks.CHERT_DEPOSIT.get()) || state.is(ModBlocks.FINE_CHERT_DEPOSIT.get())) {
                        chert++;
                        obsidian |= glassAround(level, x, y, z, step);
                        break;
                    }
                    if (state.is(ModBlocks.QUARTZITE_DEPOSIT.get()) || state.is(ModBlocks.BASALT_DEPOSIT.get())
                            || state.is(ModBlocks.LIMESTONE_DEPOSIT.get())) {
                        workable = true;
                        obsidian |= glassAround(level, x, y, z, step);
                        break;
                    }
                }
            }
        }
        if (obsidian && chert > 0) {
            parts.add(new Part(chert >= 4 ? "a large chert and obsidian seam" : "a chert and obsidian seam",
                    chert >= 4 ? 6 : 5, null, null));
        } else if (obsidian) {
            parts.add(new Part("an obsidian seam", 4, null, null));
        } else if (chert >= 4) {
            parts.add(new Part("a large chert seam", 3, null, null));
        } else if (chert > 0) {
            parts.add(new Part("a chert seam", 2, null, null));
        } else if (workable) {
            parts.add(new Part("workable stone", 1, null, null));
        }
        if (gravel >= 10) {
            parts.add(new Part("gravel beds to sift", 2, null, null));
        } else if (gravel >= 3) {
            parts.add(new Part("gravel to sift", 1, null, null));
        }
        if (dev.hominin.evolution.survival.Termites.nearestColony(level, centre, radius) != null) {
            parts.add(new Part("a termite super colony", 4, null, null));
        }
        var biome = level.getBiome(centre);
        boolean grass = biome.is(BiomeTags.IS_SAVANNA) || biome.is(net.minecraft.world.level.biome.Biomes.PLAINS)
                || biome.is(net.minecraft.world.level.biome.Biomes.SUNFLOWER_PLAINS);
        if (grass && water) {
            parts.add(new Part("grass and water the big herds come to", 3, null, null));
        }
        BlockPos breeding = breedingGroundNear(level, centre, radius + BREEDING_RADIUS / 2);
        if (breeding != null) {
            parts.add(new Part("a breeding ground for the big herds", 4, breedingKey(breeding), breeding));
        }
        for (Pois.Poi poi : Pois.inGround(level, centre, radius)) {
            String what = switch (poi.kind()) {
                case OBSIDIAN -> "an obsidian pool";
                case SPRING -> "a spring that never runs dry";
                case LICK -> "a salt lick";
                case CHERT -> "a chert super deposit";
                case BONOBO -> "bonobo country - nothing hunts there";
                case HAVEN -> "a haven - every band knows it";
                case GRAVEL -> "a gravel deposit";
                case SUPER_GRAVEL -> "a gravel super deposit";
                case TIDE_POOL -> "tide pools";
                case OASIS -> "an oasis - water, stone and animals at dawn";
                default -> "an old tool deposit";
            };
            if (parts.stream().noneMatch(p -> p.label().equals(what))) {
                parts.add(new Part(what, poi.kind().pressure, null, null));
            }
        }
        BlockPos fertile = fertileNear(level, centre, radius);
        if (fertile != null) {
            parts.add(new Part("fertile soil", 2,
                    "fertile_" + dev.hominin.evolution.survival.Soils.patchKey(fertile.getX() >> 4, fertile.getZ() >> 4), fertile));
        }
        int total = 0;
        for (Part part : parts) {
            total += part.value();
        }
        return new Value(Mth.clamp(total, 1, 10), parts);
    }

    /** Somewhere in a fertile patch within this ground, if there is one. */
    @Nullable
    private static BlockPos fertileNear(ServerLevel level, BlockPos centre, int radius) {
        int reach = radius >> 4;
        int cx = centre.getX() >> 4;
        int cz = centre.getZ() >> 4;
        for (int dx = -reach; dx <= reach; dx += 1) {
            for (int dz = -reach; dz <= reach; dz += 1) {
                if (dx * dx + dz * dz <= reach * reach
                        && dev.hominin.evolution.survival.Soils.fertile(level, cx + dx, cz + dz)) {
                    return new BlockPos(((cx + dx) << 4) + 8, centre.getY(), ((cz + dz) << 4) + 8);
                }
            }
        }
        return null;
    }

    private Land() {
    }
}
