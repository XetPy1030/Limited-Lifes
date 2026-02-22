package com.xetpy.lives;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.DimensionDataStorage;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerLivesStore extends SavedData {
    private static final String STORAGE_KEY = "xetpy_player_lives";
    private static final String PLAYERS_KEY = "players";
    private static final Codec<Map<UUID, Integer>> LIVES_BY_PLAYER_CODEC = Codec.unboundedMap(Codec.STRING, Codec.INT).xmap(
            PlayerLivesStore::deserializeLivesMap,
            PlayerLivesStore::serializeLivesMap
    );
    private static final Codec<PlayerLivesStore> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    LIVES_BY_PLAYER_CODEC.optionalFieldOf(PLAYERS_KEY, Map.of()).forGetter(store -> store.livesByPlayer)
            ).apply(instance, PlayerLivesStore::new)
    );
    private static final SavedDataType<PlayerLivesStore> TYPE =
            new SavedDataType<>(STORAGE_KEY, PlayerLivesStore::new, CODEC, DataFixTypes.LEVEL);

    private final Map<UUID, Integer> livesByPlayer;

    public PlayerLivesStore() {
        this(new HashMap<>());
    }

    private PlayerLivesStore(Map<UUID, Integer> livesByPlayer) {
        this.livesByPlayer = new HashMap<>();
        livesByPlayer.forEach((uuid, lives) -> this.livesByPlayer.put(uuid, LivesRules.clampLives(lives)));
    }

    public static PlayerLivesStore get(MinecraftServer server) {
        if (server.getLevel(Level.OVERWORLD) == null) {
            throw new IllegalStateException("Overworld is not available");
        }

        DimensionDataStorage dataStorage = server.getLevel(Level.OVERWORLD).getDataStorage();
        return dataStorage.computeIfAbsent(TYPE);
    }

    public int getLives(UUID playerUuid) {
        Integer current = livesByPlayer.get(playerUuid);
        if (current == null) {
            int initial = LivesRules.DEFAULT_LIVES;
            livesByPlayer.put(playerUuid, initial);
            setDirty();
            return initial;
        }

        int normalized = LivesRules.clampLives(current);
        if (normalized != current) {
            livesByPlayer.put(playerUuid, normalized);
            setDirty();
        }
        return normalized;
    }

    public int setLives(UUID playerUuid, int lives) {
        int normalized = LivesRules.clampLives(lives);
        livesByPlayer.put(playerUuid, normalized);
        setDirty();
        return normalized;
    }

    private static Map<UUID, Integer> deserializeLivesMap(Map<String, Integer> rawMap) {
        Map<UUID, Integer> parsed = new HashMap<>();
        for (Map.Entry<String, Integer> entry : rawMap.entrySet()) {
            try {
                parsed.put(UUID.fromString(entry.getKey()), LivesRules.clampLives(entry.getValue()));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return parsed;
    }

    private static Map<String, Integer> serializeLivesMap(Map<UUID, Integer> rawMap) {
        Map<String, Integer> serialized = new HashMap<>();
        for (Map.Entry<UUID, Integer> entry : rawMap.entrySet()) {
            serialized.put(entry.getKey().toString(), LivesRules.clampLives(entry.getValue()));
        }
        return serialized;
    }
}
