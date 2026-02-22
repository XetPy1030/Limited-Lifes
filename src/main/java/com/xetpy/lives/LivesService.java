package com.xetpy.lives;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

public class LivesService {
    public int getLives(ServerPlayer player) {
        return store(player).getLives(player.getUUID());
    }

    public int setLives(ServerPlayer player, int lives) {
        int updated = store(player).setLives(player.getUUID(), lives);
        recalculateMaxHealth(player, updated);
        return updated;
    }

    public int decreaseOnDeath(ServerPlayer player) {
        int current = getLives(player);
        int loss = LivesRules.getLossForDeath(null);
        int updated = setLives(player, current - loss);
        return updated;
    }

    public int restoreLives(ServerPlayer player, int amount) {
        int current = getLives(player);
        int updated = setLives(player, current + Math.max(0, amount));
        return updated;
    }

    public boolean canRestoreLives(ServerPlayer player) {
        return getLives(player) < LivesRules.MAX_LIVES;
    }

    public void syncPlayerState(ServerPlayer player) {
        recalculateMaxHealth(player, getLives(player));
    }

    public void recalculateMaxHealth(ServerPlayer player, int lives) {
        AttributeInstance maxHealthAttribute = player.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealthAttribute == null) {
            return;
        }

        double targetMaxHealth = LivesRules.livesToMaxHealth(lives);
        maxHealthAttribute.setBaseValue(targetMaxHealth);
        if (player.getHealth() > targetMaxHealth) {
            player.setHealth((float) targetMaxHealth);
        }
    }

    private PlayerLivesStore store(ServerPlayer player) {
        return PlayerLivesStore.get(player.level().getServer());
    }
}
