package com.xetpy;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.xetpy.lives.LivesRules;
import com.xetpy.lives.LivesService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class LivesCommand {
    private static final SimpleCommandExceptionType TARGET_REQUIRED_EXCEPTION =
            new SimpleCommandExceptionType(Component.translatable("commands.limited_lifes.lives.target_required"));

    private LivesCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, LivesService livesService) {
        dispatcher.register(Commands.literal("lives")
                .requires(source -> Commands.LEVEL_GAMEMASTERS.check(source.permissions()))
                .executes(context -> {
                    CommandSourceStack source = context.getSource();
                    if (!(source.getEntity() instanceof ServerPlayer player)) {
                        throw TARGET_REQUIRED_EXCEPTION.create();
                    }
                    return showLives(source, livesService, player);
                })
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(context -> {
                            CommandSourceStack source = context.getSource();
                            ServerPlayer target = EntityArgument.getPlayer(context, "target");
                            return showLives(source, livesService, target);
                        }))
                .then(Commands.literal("set")
                        .then(Commands.argument("target", EntityArgument.player())
                                .then(Commands.argument("value", IntegerArgumentType.integer(LivesRules.MIN_LIVES, LivesRules.MAX_LIVES))
                                        .executes(context -> {
                                            ServerPlayer target = EntityArgument.getPlayer(context, "target");
                                            int value = IntegerArgumentType.getInteger(context, "value");
                                            int updated = livesService.setLives(target, value);

                                            context.getSource().sendSuccess(
                                                    () -> Component.translatable(
                                                            "commands.limited_lifes.lives.set.success",
                                                            target.getDisplayName(),
                                                            updated,
                                                            LivesRules.MAX_LIVES
                                                    ),
                                                    true
                                            );
                                            return updated;
                                        })))));
    }

    private static int showLives(CommandSourceStack source, LivesService livesService, ServerPlayer target) {
        int lives = livesService.getLives(target);
        source.sendSuccess(
                () -> Component.translatable(
                        "commands.limited_lifes.lives.get.success",
                        target.getDisplayName(),
                        lives,
                        LivesRules.MAX_LIVES
                ),
                false
        );
        return lives;
    }
}
