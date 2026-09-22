package dev.hominin.evolution.block;

import com.mojang.serialization.MapCodec;

import dev.hominin.evolution.entity.Pachycrocuta;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * What is left of an animal: a ribcage with meat still on it, lying where it fell.
 *
 * <p>Everything that dies out here leaves one, and old ones are scattered across the
 * country where something died unseen - those are picked much cleaner, but there is
 * far more of them. This is where scavenging comes from: not killing, but getting
 * there first.
 *
 * <p>Getting there first is the whole problem. A carcass carries on the wind, and a
 * giant hyena will take it if it beats you to it.
 */
public class CarcassBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<CarcassBlock> CODEC = simpleCodec(CarcassBlock::new);

    /** An old bone bed out in the country, rather than a fresh kill: more bones, less meat. */
    public static final BooleanProperty LARGE = BooleanProperty.create("large");

    private static final VoxelShape SHAPE_X = Block.box(1.0, 0.0, 3.0, 15.0, 7.0, 13.0);
    private static final VoxelShape SHAPE_Z = Block.box(3.0, 0.0, 1.0, 13.0, 7.0, 15.0);

    /** How close a scavenger has to be before it starts on the carcass. */
    private static final double SCAVENGE_RANGE = 3.0D;
    /** How close a hominin has to be to claim a kill off something else. */
    private static final double CLAIM_RANGE = 8.0D;

    public CarcassBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LARGE, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LARGE);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(FACING).getAxis() == Direction.Axis.X ? SHAPE_X : SHAPE_Z;
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    /** A hyena that reaches it eats it, and there is nothing left for anyone. */
    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        // A hominin sitting at the kill is enough to keep a hyena off it.
        for (dev.hominin.evolution.band.BandMember guard : level.getEntitiesOfClass(
                dev.hominin.evolution.band.BandMember.class, new net.minecraft.world.phys.AABB(pos).inflate(6.0D))) {
            if (guard.isAlive()) {
                return;
            }
        }
        for (Pachycrocuta hyena : level.getEntitiesOfClass(Pachycrocuta.class,
                new net.minecraft.world.phys.AABB(pos).inflate(SCAVENGE_RANGE))) {
            if (!hyena.isAlive()) {
                continue;
            }
            // Later hominins take kills off other animals rather than the other way about.
            Player claimant = level.getNearestPlayer(pos.getX(), pos.getY(), pos.getZ(), CLAIM_RANGE, false);
            if (claimant != null && dev.hominin.evolution.hunt.Predation.standing(claimant) >= 3
                    && dev.hominin.evolution.hunt.Predation.armed(claimant)) {
                dev.hominin.evolution.combat.Scare.scare(hyena, claimant.position(), 400);
                if (claimant instanceof net.minecraft.server.level.ServerPlayer server) {
                    dev.hominin.evolution.EvolutionManager.incrementCriterion(server, "take_kill", 1);
                }
                claimant.displayClientMessage(Component.literal(
                        "You walk up to the kill, and it gives ground."), true);
                return;
            }
            level.removeBlock(pos, false);
            level.playSound(null, pos, SoundEvents.GENERIC_EAT, SoundSource.HOSTILE, 1.0F, 0.7F);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.ITEM_SLIME, pos.getX() + 0.5D,
                    pos.getY() + 0.4D, pos.getZ() + 0.5D, 12, 0.3D, 0.2D, 0.3D, 0.0D);
            Player nearest = level.getNearestPlayer(pos.getX(), pos.getY(), pos.getZ(), 32.0D, false);
            if (nearest != null) {
                nearest.displayClientMessage(Component.literal(
                        "The hyena drags the carcass apart. There is nothing left of it."), true);
            }
            return;
        }
    }
}
