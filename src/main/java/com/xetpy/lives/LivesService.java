package com.xetpy.lives;

import com.xetpy.config.HardcoreConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class LivesService {
    private static final int CRITICAL_LIVES_THRESHOLD = 2;
    private final Map<UUID, Long> ritualCooldownUntilTick = new HashMap<>();
    private final Map<UUID, Long> debtReminderTick = new HashMap<>();

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
        if (updated == LivesRules.MIN_LIVES) {
            applyLastChanceBuff(player);
        }
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

    public void applyDifficultyTick(ServerPlayer player) {
        int lives = getLives(player);
        applyDifficultyModifiers(player, lives);
    }

    public boolean tryRitualRestore(ServerPlayer player, BlockState clickedState) {
        HardcoreConfig config = config(player);
        if (!matchesRitualAltar(config, clickedState)) {
            return false;
        }

        long now = player.level().getGameTime();
        long lockedUntil = ritualCooldownUntilTick.getOrDefault(player.getUUID(), 0L);
        if (now < lockedUntil) {
            long seconds = Math.max(1L, (lockedUntil - now) / 20L);
            player.sendSystemMessage(Component.translatable("message.limited_lifes.ritual.cooldown", seconds));
            return true;
        }

        if (!canRestoreLives(player)) {
            player.sendSystemMessage(Component.translatable("message.limited_lifes.ritual.full_lives"));
            return true;
        }

        if (!hasItems(player, Items.TOTEM_OF_UNDYING, config.ritualTotemCost())
                || !hasItems(player, Items.DIAMOND_BLOCK, config.ritualDiamondBlockCost())
                || player.experienceLevel < config.ritualXpLevelsCost()) {
            player.sendSystemMessage(Component.translatable(
                    "message.limited_lifes.ritual.missing_resources",
                    config.ritualTotemCost(),
                    config.ritualDiamondBlockCost(),
                    config.ritualXpLevelsCost()
            ));
            return true;
        }

        consumeItems(player, Items.TOTEM_OF_UNDYING, config.ritualTotemCost());
        consumeItems(player, Items.DIAMOND_BLOCK, config.ritualDiamondBlockCost());
        player.giveExperienceLevels(-config.ritualXpLevelsCost());

        int updated = restoreLives(player, 1);
        ritualCooldownUntilTick.put(player.getUUID(), now + config.ritualCooldownTicks());
        player.sendSystemMessage(Component.translatable("message.limited_lifes.ritual.success", updated, LivesRules.MAX_LIVES));
        playSound(player, SoundEvents.BEACON_POWER_SELECT, 1.0F, 1.2F);
        return true;
    }

    public boolean isRitualAltar(ServerPlayer player, BlockState clickedState) {
        return matchesRitualAltar(config(player), clickedState);
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
            playSound(player, SoundEvents.NOTE_BLOCK_BELL.value(), 0.9F, 0.55F);
        }
    }

    private void sendActionBar(ServerPlayer player, int lives) {
        player.displayClientMessage(
                Component.translatable("actionbar.limited_lifes.hearts", lives, LivesRules.MAX_LIVES),
                true
        );
    }

    private void applyDifficultyModifiers(ServerPlayer player, int lives) {
        if (lives >= 6) {
            clearTrackedDebuffs(player);
            debtReminderTick.remove(player.getUUID());
            return;
        }

        if (lives <= 5 && lives >= 4) {
            applyEffect(player, MobEffects.HUNGER, 80, 0);
        } else if (lives <= 3 && lives >= 2) {
            applyEffect(player, MobEffects.HUNGER, 80, 1);
            applyEffect(player, MobEffects.WEAKNESS, 80, 0);
            applyEffect(player, MobEffects.MINING_FATIGUE, 80, 0);
        } else if (lives == LivesRules.MIN_LIVES) {
            applyEffect(player, MobEffects.HUNGER, 80, 2);
            applyEffect(player, MobEffects.WEAKNESS, 80, 2);
            applyEffect(player, MobEffects.MINING_FATIGUE, 80, 1);
            applyEffect(player, MobEffects.SLOWNESS, 80, 1);
        }

        applyDebtMode(player, lives);
    }

    private void applyDebtMode(ServerPlayer player, int lives) {
        if (lives != LivesRules.MIN_LIVES || config(player).finalMode() != HardcoreConfig.FinalMode.DEBT_MODE) {
            debtReminderTick.remove(player.getUUID());
            return;
        }

        applyEffect(player, MobEffects.WEAKNESS, 80, 3);
        applyEffect(player, MobEffects.SLOWNESS, 80, 2);
        applyEffect(player, MobEffects.MINING_FATIGUE, 80, 2);
        applyEffect(player, MobEffects.DARKNESS, 80, 0);

        long now = player.level().getGameTime();
        long nextReminderAt = debtReminderTick.getOrDefault(player.getUUID(), 0L);
        if (now >= nextReminderAt) {
            player.sendSystemMessage(Component.translatable("message.limited_lifes.debt_mode.active"));
            debtReminderTick.put(player.getUUID(), now + 20L * 15L);
        }
    }

    private void applyLastChanceBuff(ServerPlayer player) {
        int durationTicks = config(player).lastChanceDurationTicks();
        player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, durationTicks, 0, false, true, true));
        player.addEffect(new MobEffectInstance(MobEffects.SPEED, durationTicks, 0, false, true, true));
        player.sendSystemMessage(Component.translatable("message.limited_lifes.last_chance", Math.max(1, durationTicks / 20)));
        playSound(player, SoundEvents.TOTEM_USE, 1.0F, 1.0F);
    }

    private void clearTrackedDebuffs(ServerPlayer player) {
        player.removeEffect(MobEffects.HUNGER);
        player.removeEffect(MobEffects.WEAKNESS);
        player.removeEffect(MobEffects.MINING_FATIGUE);
        player.removeEffect(MobEffects.SLOWNESS);
        player.removeEffect(MobEffects.DARKNESS);
    }

    private void applyEffect(ServerPlayer player, net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect, int duration, int amplifier) {
        player.addEffect(new MobEffectInstance(effect, duration, amplifier, false, false, true));
    }

    private boolean matchesRitualAltar(HardcoreConfig config, BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString().equals(config.ritualAltarBlockId());
    }

    private boolean hasItems(ServerPlayer player, Item item, int requiredCount) {
        if (requiredCount <= 0) {
            return true;
        }
        int total = 0;
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(item)) {
                total += stack.getCount();
                if (total >= requiredCount) {
                    return true;
                }
            }
        }
        return false;
    }

    private void consumeItems(ServerPlayer player, Item item, int count) {
        if (count <= 0) {
            return;
        }
        Inventory inventory = player.getInventory();
        int remaining = count;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.is(item)) {
                continue;
            }
            int remove = Math.min(remaining, stack.getCount());
            stack.shrink(remove);
            remaining -= remove;
            if (remaining <= 0) {
                break;
            }
        }
        inventory.setChanged();
        player.inventoryMenu.broadcastChanges();
    }

    private HardcoreConfig config(ServerPlayer player) {
        return HardcoreConfig.get(player.level().getServer());
    }

    private void playSound(ServerPlayer player, SoundEvent sound, float volume, float pitch) {
        player.level().playSound(
                null,
                player.blockPosition(),
                sound,
                SoundSource.PLAYERS,
                volume,
                pitch
        );
    }
}
