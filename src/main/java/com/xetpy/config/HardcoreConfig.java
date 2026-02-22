package com.xetpy.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.DimensionDataStorage;

public class HardcoreConfig extends SavedData {
    private static final String STORAGE_KEY = "xetpy_hardcore_config";
    private static final Codec<FinalMode> FINAL_MODE_CODEC = Codec.STRING.xmap(
            raw -> FinalMode.fromId(raw),
            FinalMode::id
    );
    private static final Codec<HardcoreConfig> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    FINAL_MODE_CODEC.optionalFieldOf("finalMode", FinalMode.DEBT_MODE).forGetter(cfg -> cfg.finalMode),
                    Codec.STRING.optionalFieldOf("ritualAltarBlockId", "minecraft:enchanting_table").forGetter(cfg -> cfg.ritualAltarBlockId),
                    Codec.INT.optionalFieldOf("ritualTotemCost", 1).forGetter(cfg -> cfg.ritualTotemCost),
                    Codec.INT.optionalFieldOf("ritualDiamondBlockCost", 8).forGetter(cfg -> cfg.ritualDiamondBlockCost),
                    Codec.INT.optionalFieldOf("ritualXpLevelsCost", 20).forGetter(cfg -> cfg.ritualXpLevelsCost),
                    Codec.INT.optionalFieldOf("ritualCooldownTicks", 1200).forGetter(cfg -> cfg.ritualCooldownTicks),
                    Codec.INT.optionalFieldOf("lastChanceDurationTicks", 300).forGetter(cfg -> cfg.lastChanceDurationTicks)
            ).apply(instance, HardcoreConfig::new)
    );
    private static final SavedDataType<HardcoreConfig> TYPE =
            new SavedDataType<>(STORAGE_KEY, HardcoreConfig::new, CODEC, DataFixTypes.LEVEL);

    private FinalMode finalMode;
    private String ritualAltarBlockId;
    private int ritualTotemCost;
    private int ritualDiamondBlockCost;
    private int ritualXpLevelsCost;
    private int ritualCooldownTicks;
    private int lastChanceDurationTicks;

    public HardcoreConfig() {
        this(
                FinalMode.DEBT_MODE,
                "minecraft:enchanting_table",
                1,
                8,
                20,
                1200,
                300
        );
    }

    private HardcoreConfig(
            FinalMode finalMode,
            String ritualAltarBlockId,
            int ritualTotemCost,
            int ritualDiamondBlockCost,
            int ritualXpLevelsCost,
            int ritualCooldownTicks,
            int lastChanceDurationTicks
    ) {
        this.finalMode = finalMode == null ? FinalMode.DEBT_MODE : finalMode;
        this.ritualAltarBlockId = ritualAltarBlockId == null || ritualAltarBlockId.isBlank()
                ? "minecraft:enchanting_table"
                : ritualAltarBlockId;
        this.ritualTotemCost = Math.max(0, ritualTotemCost);
        this.ritualDiamondBlockCost = Math.max(0, ritualDiamondBlockCost);
        this.ritualXpLevelsCost = Math.max(0, ritualXpLevelsCost);
        this.ritualCooldownTicks = Math.max(0, ritualCooldownTicks);
        this.lastChanceDurationTicks = Math.max(20, lastChanceDurationTicks);
    }

    public static HardcoreConfig get(MinecraftServer server) {
        if (server.getLevel(Level.OVERWORLD) == null) {
            throw new IllegalStateException("Overworld is not available");
        }
        DimensionDataStorage dataStorage = server.getLevel(Level.OVERWORLD).getDataStorage();
        return dataStorage.computeIfAbsent(TYPE);
    }

    public FinalMode finalMode() {
        return finalMode;
    }

    public String ritualAltarBlockId() {
        return ritualAltarBlockId;
    }

    public int ritualTotemCost() {
        return ritualTotemCost;
    }

    public int ritualDiamondBlockCost() {
        return ritualDiamondBlockCost;
    }

    public int ritualXpLevelsCost() {
        return ritualXpLevelsCost;
    }

    public int ritualCooldownTicks() {
        return ritualCooldownTicks;
    }

    public int lastChanceDurationTicks() {
        return lastChanceDurationTicks;
    }

    public enum FinalMode {
        BAN("ban"),
        SPECTATOR("spectator"),
        PRISON("prison"),
        DEBT_MODE("debt_mode");

        private final String id;

        FinalMode(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public static FinalMode fromId(String id) {
            for (FinalMode mode : values()) {
                if (mode.id.equalsIgnoreCase(id)) {
                    return mode;
                }
            }
            return DEBT_MODE;
        }
    }
}
