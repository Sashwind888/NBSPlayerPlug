package sashwind.mc.plugin.nbsplayer.nbs;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import static sashwind.mc.plugin.nbsplayer.Nbsplayer.getInstance;

/**
 * 严格按照官方 NoteBlockAPI 的 NBSDecoder 实现解析
 */
public class Decoder {
    // ---------- 解码结果 ----------
    /** 所有有音符的行（按 tick 升序排列，含 tick 位置信息） */
    private final List<Line> lines = new ArrayList<>();
    private int version;
    private String name = "";
    private String author = "";
    private String originAuthor = "";
    private String description = "";
    private int length;              // 总 Tick 数
    private int height;              // 图层数
    private float bpm;
    private int firstCustomInstrument; // 第一个自定义乐器索引
    private byte beat;               // 拍号
    private byte loop;               // 循环标志（0/1）
    private byte loopCount;
    private int loopStart;

    // ---------- 构造函数 ----------
    public Decoder(InputStream in) {
        try (BinaryReader reader = new BinaryReader(in)) {
            // 1. 读取长度/版本标志
            int length0 = reader.readShort() & 0xFFFF; // 转为无符号
            if (length0 == 0) {
                // 新格式
                version = reader.readByte() & 0xFF;
                firstCustomInstrument = reader.readByte() & 0xFF;
                if (version >= 3) {
                    length = reader.readShort() & 0xFFFF;
                } else {
                    length = 0; // 稍后由 tick 计数确定
                }
            } else {
                // 旧格式 (version 0)
                version = 0;
                length = length0;
                firstCustomInstrument = 10; // 官方默认值
            }

            // 2. 图层高度
            height = reader.readShort() & 0xFFFF;

            // 3. 字符串信息
            name = reader.readString(reader.readInt());
            author = reader.readString(reader.readInt());
            originAuthor = reader.readString(reader.readInt());
            description = reader.readString(reader.readInt());

            // 4. 速度 (tempo / 100 = NBS ticks per second)
            int tempo = reader.readShort() & 0xFFFF;
            bpm = tempo / 100f;

            // 5. 其他元数据（按顺序跳过）
            reader.readByte();          // autoSave (boolean)
            reader.readByte();          // autoSaveDuration
            beat = reader.readByte();   // timeSignature
            reader.readInt();           // minutesSpent
            reader.readInt();           // leftClicks
            reader.readInt();           // rightClicks
            reader.readInt();           // blocksAdded
            reader.readInt();           // blocksRemoved
            // 导入文件名（总是读取，无论版本）
            reader.readString(reader.readInt());

            // 6. 循环信息（版本 >= 4）
            if (version >= 4) {
                loop = reader.readByte();
                loopCount = reader.readByte();
                loopStart = reader.readShort() & 0xFFFF;
            }

            // 7. 读取音符数据（使用跳转逻辑）
            int tick = -1;
            while (true) {
                int jumpTicks = reader.readShort() & 0xFFFF;
                if (jumpTicks == 0) break;
                tick += jumpTicks;

                int layer = -1;
                Line line = new Line();
                line.setTick(tick);  // 记录实际 tick 位置

                while (true) {
                    int jumpLayers = reader.readShort() & 0xFFFF;
                    if (jumpLayers == 0) break;
                    layer += jumpLayers;

                    byte instrument = reader.readByte();
                    // NBS 格式中 key 存储为 实际音高 + 33（范围 33~57，对应 F#0~F#2）
                    // 需要减去 33 并钳位到 0~24
                    byte key = reader.readByte();
                    key = (byte) Math.max(0, Math.min(24, (key & 0xFF) - 33));

                    // 版本 4+ 有 velocity, panning, pitch
                    if (version >= 4) {
                        byte velocity = reader.readByte();   // 音符音量 (0~100%)
                        reader.readByte();                   // panning（立体声左右，暂忽略）
                        reader.readShort();                  // pitch（微调，暂忽略）
                        line.add(instrument, key, velocity);
                    } else {
                        line.add(instrument, key, (byte) 100); // 旧版本默认 100% 音量
                    }
                }

                // 保存所有行（包括空行也保存，以保持索引=tick的对应关系）
                // 由于我们使用 jumpTicks 跳转，空 tick 没有对应的 Line，
                // 但我们在播放时使用 tick 位置信息处理时序
                lines.add(line);
            }

            // 旧版本用最后 tick 作为长度
            if (version > 0 && version < 3) {
                length = tick;
            }

            // 注意：没有继续读取图层名称/音量/pan 和自定义乐器，
            // 因为本类只关心音符数据，无需解析剩余内容。

        } catch (IOException e) {
            getInstance().getLogger().log(Level.WARNING, "NBS 数据解析失败", e);
        }
    }

    // ---------- Getter 方法 ----------
    /** @return 所有有音符的行（按 tick 升序排列） */
    public List<Line> getLines() {
        return lines;
    }

    /**
     * 根据 tick 查找对应的行（二分查找，lines 已按 tick 排序）
     * @return 该 tick 处的 Line，若没有则返回 null
     */
    public Line getLineAtTick(int tick) {
        // 线性查找（对于大多数 NBS 文件足够快），可优化为二分
        for (Line line : lines) {
            int lineTick = line.getTick();
            if (lineTick == tick) return line;
            if (lineTick > tick) break; // 已过目标 tick，不存在
        }
        return null;
    }

    public float getBPM() {
        return bpm;
    }

    /** @return 歌曲总 tick 数（含空 tick） */
    public int getLength() {
        return length;
    }

    public String getName() {
        return name;
    }

    public String getAuthor() {
        return author;
    }

    public String getOriginAuthor() {
        return originAuthor;
    }

    public String getDescription() {
        return description;
    }

    public int getVersion() {
        return version;
    }

    public int getHeight() {
        return height;
    }

    public int getFirstCustomInstrument() {
        return firstCustomInstrument;
    }

    public byte getBeat() {
        return beat;
    }

    public byte getLoop() {
        return loop;
    }

    public byte getLoopCount() {
        return loopCount;
    }

    public int getLoopStart() {
        return loopStart;
    }
}
