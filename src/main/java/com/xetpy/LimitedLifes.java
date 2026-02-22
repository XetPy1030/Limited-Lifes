package com.xetpy;

import com.xetpy.lives.LivesService;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

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

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                LivesCommand.register(dispatcher, LIVES_SERVICE));
    }
}
