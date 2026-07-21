package sashwind.mc.plugin.nbsplayer.nbs;

import org.bukkit.Sound;

import java.util.List;

/**
 * v2 声音映射器 — 支持扩展音域（需 extranotes 资源包）和自定义乐器。
 *
 * 原版 Minecraft note block 音域：key 0~24（F#0 ~ F#2）
 * extranotes 资源包扩展范围：key -12~36（4 个八度）
 *
 * 自定义乐器使用 NBS 文件内置的 soundFile 名称播放。
 */
public class SoundMapper {

    /** 原版乐器名称（按 NBS instrument ID 0~15 顺序） */
    private static final String[] VANILLA_NAMES = {
        "harp",            // 0
        "bass",            // 1
        "basedrum",        // 2
        "snare",           // 3
        "hat",             // 4
        "guitar",          // 5
        "flute",           // 6
        "bell",            // 7
        "chime",           // 8
        "xylophone",       // 9
        "iron_xylophone",  // 10
        "cow_bell",        // 11
        "didgeridoo",      // 12
        "bit",             // 13
        "banjo",           // 14
        "pling"            // 15
    };

    /** 原版 Bukkit Sound 枚举映射 */
    private static final Sound[] VANILLA_SOUNDS = {
        Sound.BLOCK_NOTE_BLOCK_HARP,
        Sound.BLOCK_NOTE_BLOCK_BASS,
        Sound.BLOCK_NOTE_BLOCK_BASEDRUM,
        Sound.BLOCK_NOTE_BLOCK_SNARE,
        Sound.BLOCK_NOTE_BLOCK_HAT,
        Sound.BLOCK_NOTE_BLOCK_GUITAR,
        Sound.BLOCK_NOTE_BLOCK_FLUTE,
        Sound.BLOCK_NOTE_BLOCK_BELL,
        Sound.BLOCK_NOTE_BLOCK_CHIME,
        Sound.BLOCK_NOTE_BLOCK_XYLOPHONE,
        Sound.BLOCK_NOTE_BLOCK_IRON_XYLOPHONE,
        Sound.BLOCK_NOTE_BLOCK_COW_BELL,
        Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO,
        Sound.BLOCK_NOTE_BLOCK_BIT,
        Sound.BLOCK_NOTE_BLOCK_BANJO,
        Sound.BLOCK_NOTE_BLOCK_PLING
    };

    /** 声音映射结果 */
    public static class Result {
        /** 非 null 时表示原版 Sound 枚举 */
        public final Sound sound;
        /** 非 null 时表示自定义/扩展的声音名称（如 "block.note_block.harp_-1"） */
        public final String customSound;
        /** Minecraft pitch 参数 */
        public final float pitch;

        public Result(Sound sound, float pitch) {
            this.sound = sound;
            this.customSound = null;
            this.pitch = pitch;
        }

        public Result(String customSound, float pitch) {
            this.sound = null;
            this.customSound = customSound;
            this.pitch = pitch;
        }

        /** 是否使用原版 Sound 枚举播放 */
        public boolean isVanilla() {
            return sound != null;
        }
    }

    private final int vanillaCount;
    private final List<CustomInstrument> customInstruments;

    /**
     * @param vanillaCount 原版乐器数量（通常 9~16）
     * @param customInstruments 自定义乐器列表
     */
    public SoundMapper(int vanillaCount, List<CustomInstrument> customInstruments) {
        this.vanillaCount = Math.min(vanillaCount, VANILLA_NAMES.length);
        this.customInstruments = customInstruments;
    }

    /**
     * 将 (instrumentId, key) 映射为可播放的声音结果。
     *
     * @param instrumentId NBS 乐器 ID
     * @param key          归一化后的音高键（NBS raw - 33），范围 -33~54
     * @return 声音映射结果
     */
    public Result map(int instrumentId, int key) {
        // ---- 原版乐器 ----
        if (instrumentId >= 0 && instrumentId < vanillaCount) {
            return mapVanilla(instrumentId, key);
        }

        // ---- 自定义乐器 ----
        int customIndex = instrumentId - vanillaCount;
        if (customIndex >= 0 && customIndex < customInstruments.size()) {
            return mapCustom(customInstruments.get(customIndex), key);
        }

        // 回退：未知乐器用 harp
        return mapVanilla(0, key);
    }

    /**
     * 原版乐器映射，支持 extranotes 扩展音域。
     *
     * 每个声音文件通过 Minecraft pitch 参数 (0.5~2.0) 可覆盖约 2 个八度。
     * 不钳位 key——始终用实际 key 计算 pitch，确保远距离音符音高正确。
     *
     * _-1  录制: F#-1 (key=-12) → 覆盖 key -24 ~ 0   (F#-2 ~ F#0)
     * 原版  录制: F#1  (key=12)  → 覆盖 key   0 ~ 24  (F#0  ~ F#2)
     * _1   录制: F#3  (key=36)  → 覆盖 key  24 ~ 48  (F#2  ~ F#4)
     */
    private Result mapVanilla(int instId, int key) {
        String name = VANILLA_NAMES[instId];

        if (key >= 0 && key <= 24) {
            float pitch = keyToPitch(key, 12);
            return new Result(VANILLA_SOUNDS[instId], pitch);
        } else if (key < 0) {
            // 使用 _-1 声音 + 实际 key（不钳位）
            float pitch = keyToPitch(key, -12);
            return new Result("block.note_block." + name + "_-1", pitch);
        } else {
            // key > 24：使用 _1 声音 + 实际 key（不钳位）
            float pitch = keyToPitch(key, 36);
            return new Result("block.note_block." + name + "_1", pitch);
        }
    }

    /**
     * 自定义乐器映射。
     * 使用自定义乐器的基准音高计算 pitch，声音标识符为乐器定义的 soundFile。
     */
    private Result mapCustom(CustomInstrument ci, int key) {
        // ci.pitch 是 NBS 原始空间 (0~87)，需转为归一化空间 (-33~54)
        int normalizedRefKey = (ci.pitch & 0xFF) - 33;
        float pitch = keyToPitch(key, normalizedRefKey);
        return new Result(ci.soundFile, pitch);
    }

    /**
     * 将 NBS key 转为 Minecraft pitch 值。
     *
     * @param key      归一化后的 NBS 音高键
     * @param refKey   基准键（此键对应 pitch=1.0）
     * @return pitch 值（限制在 0.5 ~ 2.0 范围）
     */
    public static float keyToPitch(int key, int refKey) {
        float pitch = (float) Math.pow(2.0, (key - refKey) / 12.0);
        return Math.max(0.5f, Math.min(2.0f, pitch));
    }

    /** 兼容旧代码：按原版 instrument ID 获取 Sound 枚举 */
    public static Sound getVanillaSound(byte id) {
        if (id >= 0 && id < VANILLA_SOUNDS.length) {
            return VANILLA_SOUNDS[id];
        }
        return Sound.BLOCK_NOTE_BLOCK_HARP;
    }
}
