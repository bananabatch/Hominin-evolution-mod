package dev.hominin.evolution.entity;

/** An animal that goes up trees: {@link ClimbTreeGoal} tells it when it is holding a trunk. */
public interface TreeClimber {
    void setClimbing(boolean climbing);
}
