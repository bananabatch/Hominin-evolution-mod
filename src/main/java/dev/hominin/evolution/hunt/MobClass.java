package dev.hominin.evolution.hunt;

import dev.hominin.evolution.ModTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * What an animal does when something goes for it. Every mob, vanilla or the mod's own, is one of these:
 *
 * <ul>
 * <li><b>Prey</b> runs the moment it is struck - and the herd runs with it.</li>
 * <li><b>Defensive</b> turns and fights - a goat, a llama, a bee, a panda, a baboon with its troop - and only breaks
 * and runs once it is badly hurt (under 40%).</li>
 * <li><b>Megafauna</b> is defensive in its own way: it stands, or it makes its one hard run, by its own rules.</li>
 * <li><b>Predators</b> fight back until they are nearly done (under 20%), and only then run.</li>
 * </ul>
 */
public enum MobClass {
    PREY(1.0F), DEFENSIVE(0.4F), MEGAFAUNA(0.5F), PREDATOR(0.2F);

    /** Below this share of health it gives up fighting and runs. */
    private final float breaksAt;

    MobClass(float breaksAt) {
        this.breaksAt = breaksAt;
    }

    public float breaksAt() {
        return breaksAt;
    }

    public String label() {
        return switch (this) {
            case PREY -> "prey - runs";
            case DEFENSIVE -> "defensive - fights back";
            case MEGAFAUNA -> "megafauna - stands, then runs";
            case PREDATOR -> "predator - fights to the end";
        };
    }

    public static MobClass of(LivingEntity entity) {
        if (entity.getType().is(ModTags.EntityTypes.PREDATORS) || entity instanceof net.minecraft.world.entity.monster.Enemy
                || entity instanceof net.minecraft.world.entity.animal.Wolf
                || entity instanceof net.minecraft.world.entity.animal.PolarBear
                || entity instanceof dev.hominin.evolution.entity.Dinopithecus) {
            return PREDATOR;
        }
        if (entity.getType().is(ModTags.EntityTypes.MEGAFAUNA)) {
            return MEGAFAUNA;
        }
        if (entity.getType().is(ModTags.EntityTypes.FEARLESS)
                || entity instanceof net.minecraft.world.entity.animal.goat.Goat
                || entity instanceof net.minecraft.world.entity.animal.horse.Llama
                || entity instanceof net.minecraft.world.entity.animal.Bee
                || entity instanceof net.minecraft.world.entity.animal.IronGolem
                || entity instanceof net.minecraft.world.entity.animal.Panda
                || entity instanceof net.minecraft.world.entity.animal.Dolphin
                || entity instanceof net.minecraft.world.entity.animal.camel.Camel
                || entity instanceof net.minecraft.world.entity.monster.hoglin.Hoglin
                || entity instanceof dev.hominin.evolution.entity.TroopAnimal
                || entity instanceof dev.hominin.evolution.entity.Crocodile) {
            return DEFENSIVE;
        }
        return PREY;
    }

    /** Still fighting rather than running: not prey, and not yet hurt past its breaking point. */
    public static boolean stillFights(LivingEntity entity) {
        MobClass kind = of(entity);
        if (kind == PREY || kind == MEGAFAUNA) {
            return false;
        }
        return entity.getHealth() / Math.max(1.0F, entity.getMaxHealth()) > kind.breaksAt;
    }

    /** It turns on whoever hurt it - if it has anything to fight with. */
    public static void turnOn(LivingEntity entity, LivingEntity attacker) {
        if (entity instanceof Mob mob && mob.getAttributes().hasAttribute(Attributes.ATTACK_DAMAGE)
                && mob.getTarget() != attacker) {
            mob.setTarget(attacker);
        }
    }
}
