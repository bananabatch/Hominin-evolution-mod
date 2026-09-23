package dev.hominin.evolution.item;

import java.util.List;

import dev.hominin.evolution.EvolutionManager;
import dev.hominin.evolution.band.Band;
import dev.hominin.evolution.band.BandMember;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Somebody's skull. Held out at arm's length and looked in the eye, it becomes a promise -
 * and the band, watching you do it, believes you.
 */
public class HomininSkullItem extends Item {
    /** Once a day is a vow. Any more often is a bit. */
    private static final int COOLDOWN_TICKS = 24000;
    private static final double AUDIENCE = 8.0D;

    public HomininSkullItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer server)) {
            return InteractionResultHolder.success(stack);
        }
        server.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(server,
                new dev.hominin.evolution.network.BodyAnimationPayload(server.getId(), "skull_pose"));
        PacketDistributor.sendToPlayer(server, new dev.hominin.evolution.network.SkullPosePayload());
        dev.hominin.evolution.advancement.HomininAdvancements.award(server, "hominin/tuff_pose");
        dev.hominin.evolution.band.Cohesion.add(server, 3);
        List<BandMember> watching = Band.ownNear(server, AUDIENCE);
        for (BandMember member : watching) {
            member.addBond(1);
        }
        return InteractionResultHolder.consume(stack);
    }
}
