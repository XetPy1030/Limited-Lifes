package com.xetpy.lives;


import net.minecraft.world.damagesource.DamageSource;

public final class LivesRules {
    public static final int DEFAULT_LIVES = 10;
    public static final int MIN_LIVES = 1;
    public static final int MAX_LIVES = 10;
    public static final int DEATH_LOSS = 1;
    public static final float HEALTH_PER_LIFE = 2.0F;

    private LivesRules() {
    }

    public static int clampLives(int lives) {
        return Math.max(MIN_LIVES, Math.min(MAX_LIVES, lives));
    }

    public static float livesToMaxHealth(int lives) {
        return clampLives(lives) * HEALTH_PER_LIFE;
    }

    public static int getLossForDeath(DamageSource source) {
        // Hook for future balancing by death reason (void/lava/pvp/etc).
        return DEATH_LOSS;
    }
}
