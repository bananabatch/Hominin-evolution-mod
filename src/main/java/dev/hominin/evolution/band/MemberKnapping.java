package dev.hominin.evolution.band;

import dev.hominin.evolution.ModItems;
import dev.hominin.evolution.ModTags;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Band members knap the way you do, and it is seen: in the hands, the stone held in the right and struck with the
 * hammerstone in the left, dust and chips off every blow; at a station, crouched over it - a bone tapped on the stone,
 * then the hammerstone brought down in both hands; and at the end the tool held up and looked over. The work itself -
 * how long it takes, what comes of it - stays each job's own; this is only what it looks like.
 */
public final class MemberKnapping {
    private static final int STRIKE_CYCLE = 32;
    private static final int[] STRIKES = {8, 18, 28};
    private static final int BOP = 30;
    private static final int[] TAPS = {10, 18, 26};
    private static final int[] BLOWS = {10, 24};
    public static final int INSPECT = 30;

    /**
     * One tick of knapping in the hands, {@code tick} ticks into it: the gesture kept going, and on each blow a puff
     * of stone dust and chips.
     */
    public static void inHands(BandMember member, int tick, ItemStack stone) {
        member.gesture("knap_hand_strike", 12, stone, hammer(member));
        int within = tick % STRIKE_CYCLE;
        for (int strike : STRIKES) {
            if (within == strike) {
                chips(member, hands(member), stone);
            }
        }
    }

    /**
     * One tick of knapping at a station, {@code tick} ticks into {@code total}: crouched, a bone tapped on the stone
     * for the first part, then the hammerstone in both hands for the rest.
     */
    public static void atStation(BandMember member, int tick, int total, ItemStack stone) {
        boolean bone = total > BOP + 20 && tick < BOP;
        if (bone) {
            member.gesture("station_bop", 12, new ItemStack(Items.BONE), ItemStack.EMPTY);
            for (int tap : TAPS) {
                if (tick == tap) {
                    Vec3 at = ground(member);
                    ((ServerLevel) member.level()).sendParticles(ParticleTypes.SMOKE, at.x, at.y, at.z, 2, 0.04D, 0.02D,
                            0.04D, 0.003D);
                }
            }
            return;
        }
        member.gesture("station_smash", 12, hammer(member), ItemStack.EMPTY);
        int into = total > BOP + 20 ? tick - BOP : tick;
        for (int blow : BLOWS) {
            if (into % 30 == blow) {
                chips(member, ground(member), stone);
            }
        }
    }

    /** Done: the tool held up and looked over for a moment. */
    public static void made(BandMember member, ItemStack tool) {
        if (tool.isEmpty()) {
            member.clearGesture();
            return;
        }
        member.gesture("knap_inspect", INSPECT, tool, ItemStack.EMPTY);
    }

    /** Whatever they strike with: a hammerstone if they carry one, else another stone. */
    private static ItemStack hammer(BandMember member) {
        ItemStack found = member.findCarried(stack -> stack.is(ModTags.Items.HAMMERSTONES));
        return found != null ? found : new ItemStack(ModItems.HAMMERSTONE.get());
    }

    private static Vec3 hands(BandMember member) {
        Vec3 look = Vec3.directionFromRotation(0.0F, member.getYRot());
        return member.getEyePosition().add(look.scale(0.4D)).subtract(0.0D, 0.6D, 0.0D);
    }

    private static Vec3 ground(BandMember member) {
        Vec3 look = Vec3.directionFromRotation(0.0F, member.getYRot());
        return member.position().add(look.scale(0.8D)).add(0.0D, 0.1D, 0.0D);
    }

    private static void chips(BandMember member, Vec3 at, ItemStack stone) {
        if (!(member.level() instanceof ServerLevel level)) {
            return;
        }
        level.sendParticles(ParticleTypes.SMOKE, at.x, at.y, at.z, 3, 0.05D, 0.03D, 0.05D, 0.004D);
        if (!stone.isEmpty()) {
            level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, stone), at.x, at.y, at.z, 3, 0.05D, 0.03D,
                    0.05D, 0.06D);
        }
    }

    private MemberKnapping() {
    }
}
