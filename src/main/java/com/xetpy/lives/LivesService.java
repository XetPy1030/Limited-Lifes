package com.xetpy.lives;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

public class LivesService {
    private static final int CRITICAL_LIVES_THRESHOLD = 2;

    public int getLives(ServerPlayer player) {
        return store(player).getLives(player.getUUID());
    }

    public int setLives(ServerPlayer player, int lives) {
        int previous = getLives(player);
        int updated = store(player).setLives(player.getUUID(), lives);
        recalculateMaxHealth(player, updated);
        notifyOnLivesChanged(player, previous, updated);
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
        int currentLives = getLives(player);
        recalculateMaxHealth(player, currentLives);
        sendActionBar(player, currentLives);
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

    private void notifyOnLivesChanged(ServerPlayer player, int previousLives, int updatedLives) {
        sendActionBar(player, updatedLives);

        if (updatedLives < previousLives) {
            player.sendSystemMessage(Component.translatable("message.limited_lifes.hearts_left", updatedLives));
        } else if (updatedLives > previousLives) {
            player.sendSystemMessage(Component.translatable("message.limited_lifes.hearts_restored", updatedLives));
        }

        if (updatedLives <= CRITICAL_LIVES_THRESHOLD && updatedLives < previousLives) {
            player.sendSystemMessage(Component.translatable("message.limited_lifes.hearts_critical", updatedLives));
            player.level().playSound(
                    null,
                    player.blockPosition(),
                    SoundEvents.NOTE_BLOCK_BELL.value(),
                    SoundSource.PLAYERS,
                    0.9F,
                    0.55F
            );
        }
    }

    private void sendActionBar(ServerPlayer player, int lives) {
        player.displayClientMessage(
                Component.translatable("actionbar.limited_lifes.hearts", lives, LivesRules.MAX_LIVES),
                true
        );
    }
}
