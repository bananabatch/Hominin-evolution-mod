package dev.hominin.evolution.client;

import dev.hominin.evolution.climb.Climbing;
import dev.hominin.evolution.network.ClimbPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Climbing, from the climber's side. Movement is simulated on the client, so this is
 * where the keys turn into going up and down.
 *
 * <ul>
 *   <li>Walk into a trunk and hold jump to start climbing, and keep holding it to go up.</li>
 *   <li>Hold sneak to let yourself down. Hold neither to cling where you are.</li>
 *   <li>While climbing, leaves do not stop you - you climb up through the canopy.</li>
 *   <li>Come out of the top of the leaves and you let go, and stand on them.</li>
 *   <li>Sneak while looking down on a canopy to climb back down into it.</li>
 *   <li>Push into any other wall with jump held and you climb it too - but only
 *   {@link Climbing#WALL_CLIMB_LIMIT} blocks, enough to get out of a hole.</li>
 * </ul>
 */
public final class ClimbController {
    /** How steeply to look down before sneaking on a canopy means climbing into it. */
    private static final float LOOK_DOWN_PITCH = 50.0F;

    /** Sideways movement among branches is slow and deliberate. */
    private static final double HORIZONTAL_DRAG = 0.85D;

    /** Walls need a sustained push before they count, so jumping up a single step is still a jump. */
    private static final int WALL_PUSH_TICKS = 6;
    /** The hop over the rim when a climb runs out at the top. */
    private static final double MANTLE_VELOCITY = 0.42D;

    private static int regripCooldown;
    private static int wallPushTicks;
    private static double climbStartY;
    private static LocalPlayer lastPlayer;

    /** The server took the climb away; wait before grabbing on again. */
    static void overruled(int regripTicks) {
        regripCooldown = Math.max(regripCooldown, regripTicks);
    }

    public static void onPlayerTick(PlayerTickEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.getEntity() != mc.player || mc.player == null) {
            return;
        }
        LocalPlayer player = mc.player;
        if (player != lastPlayer) {
            // Respawned or changed dimension: a new player object, holding nothing.
            lastPlayer = player;
            regripCooldown = 0;
        }
        if (regripCooldown > 0) {
            regripCooldown--;
        }
        boolean jump = player.input.jumping;
        boolean sneak = player.input.shiftKeyDown;
        boolean unable = player.isPassenger() || player.getAbilities().flying || player.isSpectator()
                || player.isInWater() || player.isFallFlying();

        if (!Climbing.isClimbing(player)) {
            if (unable || regripCooldown > 0) {
                return;
            }
            boolean pushing = jump && player.horizontalCollision;
            boolean grabTrunk = pushing && Climbing.grippedLog(player) != null;
            wallPushTicks = pushing && Climbing.grippedWall(player) != null ? wallPushTicks + 1 : 0;
            boolean grabWall = wallPushTicks >= WALL_PUSH_TICKS;
            boolean dropIntoCanopy = sneak && player.getXRot() > LOOK_DOWN_PITCH && Climbing.onCanopy(player);
            if (grabTrunk || grabWall || dropIntoCanopy) {
                wallPushTicks = 0;
                climbStartY = player.getY();
                set(player, true);
            }
            return;
        }

        if (unable || !Climbing.canHold(player)) {
            // Out of the top of the leaves, over the rim of a wall, or away from it: let go.
            set(player, false);
            if (jump && !unable) {
                Vec3 motion = player.getDeltaMovement();
                player.setDeltaMovement(motion.x, MANTLE_VELOCITY, motion.z);
            }
            return;
        }
        if (player.onGround() && !jump && !Climbing.inLeaves(player)) {
            // Back down on solid ground.
            set(player, false);
            return;
        }
        boolean atWallLimit = player.getY() >= climbStartY + Climbing.WALL_CLIMB_LIMIT && Climbing.onlyWall(player);
        double vertical = jump && !atWallLimit ? Climbing.climbSpeed(ClientSync.stageOf(player.getUUID()))
                : sneak ? -Climbing.DESCEND_SPEED : 0.0D;
        Vec3 motion = player.getDeltaMovement();
        // Gravity is only applied after this tick's move, so this is exactly how far it goes.
        player.setDeltaMovement(motion.x * HORIZONTAL_DRAG, vertical, motion.z * HORIZONTAL_DRAG);
        player.resetFallDistance();
    }

    private static void set(LocalPlayer player, boolean climbing) {
        Climbing.setClimbing(player, climbing);
        PacketDistributor.sendToServer(new ClimbPayload(climbing));
    }

    private ClimbController() {
    }
}
