package com.xetpy;

import com.xetpy.lives.LivesService;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

public class LimitedLifes implements ModInitializer {
    public static final String MOD_ID = "xetpy";
    private static final LivesService LIVES_SERVICE = new LivesService();

    @Override
    public void onInitialize() {
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            if (!alive) {
                LIVES_SERVICE.decreaseOnDeath(newPlayer);
            } else {
                LIVES_SERVICE.syncPlayerState(newPlayer);
            }
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                LIVES_SERVICE.syncPlayerState(handler.getPlayer()));

        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClientSide() || hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer serverPlayer)) {
                return InteractionResult.PASS;
            }
            if (!player.isShiftKeyDown()) {
                return InteractionResult.PASS;
            }
            BlockState clickedState = world.getBlockState(hitResult.getBlockPos());
            if (!LIVES_SERVICE.isRitualAltar(serverPlayer, clickedState)) {
                return InteractionResult.PASS;
            }
            if (!player.getMainHandItem().is(Items.TOTEM_OF_UNDYING)) {
                serverPlayer.sendSystemMessage(Component.translatable("message.limited_lifes.ritual.catalyst_required"));
                return InteractionResult.SUCCESS;
            }
            return LIVES_SERVICE.tryRitualRestore(serverPlayer, clickedState)
                    ? InteractionResult.SUCCESS
                    : InteractionResult.PASS;
        });

        ServerTickEvents.START_SERVER_TICK.register(server -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                LIVES_SERVICE.applyDifficultyTick(player);
            }
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                LivesCommand.register(dispatcher, LIVES_SERVICE));
    }
}
