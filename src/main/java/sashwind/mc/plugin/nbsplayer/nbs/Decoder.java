package sashwind.mc.plugin.nbsplayer.nbs;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import static sashwind.mc.plugin.nbsplayer.Nbsplayer.getInstance;

/**
 * NBS 文件解析器（v2 — 支持自定义乐器和扩展音域）。
 * 严格按照官方 NoteBlockAPI 的 NBSDecoder 实现解析。
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
    private int firstCustomInstrument; // 第一个自定义乐器索引（即原版乐器数量）
    private byte beat;               // 拍号
    private byte loop;               // 循环标志（0/1）
    private byte loopCount;
    private int loopStart;
    /** 自定义乐器列表（从 NBS 末尾解析） */
    private List<CustomInstrument> customInstruments = new ArrayList<>();

    // ---------- 构造函数 ----------
    public Decoder(InputStream in) {
        try (BinaryReader reader = new BinaryReader(in)) {
            // 1. 读取长度/版本标志
            int length0 = reader.readShort() & 0xFFFF;
            if (length0 == 0) {
                // 新格式
                version = reader.readByte() & 0xFF;
                firstCustomInstrument = reader.readByte() & 0xFF;
                if (version >= 3) {
                    length = reader.readShort() & 0xFFFF;
                } else {
                    length = 0;
                }
            } else {
                // 旧格式 (version 0)
                version = 0;
                length = length0;
                firstCustomInstrument = 10;
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

            // 5. 其他元数据
            reader.readByte();          // autoSave (boolean)
            reader.readByte();          // autoSaveDuration
            beat = reader.readByte();   // timeSignature
            reader.readInt();           // minutesSpent
            reader.readInt();           // leftClicks
            reader.readInt();           // rightClicks
            reader.readInt();           // blocksAdded
            reader.readInt();           // blocksRemoved
            reader.readString(reader.readInt()); // 导入文件名

            // 6. 循环信息（版本 >= 4）
            if (version >= 4) {
                loop = reader.readByte();
                loopCount = reader.readByte();
                loopStart = reader.readShort() & 0xFFFF;
            }

            // 7. 读取音符数据
            int tick = -1;
            while (true) {
                int jumpTicks = reader.readShort() & 0xFFFF;
                if (jumpTicks == 0) break;
                tick += jumpTicks;

                int layer = -1;
                Line line = new Line();
                line.setTick(tick);

                while (true) {
                    int jumpLayers = reader.readShort() & 0xFFFF;
                    if (jumpLayers == 0) break;
                    layer += jumpLayers;

                    byte instrument = reader.readByte();
                    // v2: 完整 key 范围，不做 0~24 钳位
                    // NBS 格式 key 存储为 实际音高 + 33
                    // 归一化后范围 -33~54（NBS key 0~87，对应 A0~C8）
                    // 扩展音域：-12~36（需要 extranotes 资源包）
                    byte rawKey = reader.readByte();
                    int key = (rawKey & 0xFF) - 33;

                    if (version >= 4) {
                        byte velocity = reader.readByte();
                        reader.readByte();   // panning
                        reader.readShort();  // pitch（微调，暂忽略）
                        line.add(instrument, key, velocity);
                    } else {
                        line.add(instrument, key, (byte) 100);
                    }
                }

                lines.add(line);
            }

            // 旧版本用最后 tick 作为长度
            if (version > 0 && version < 3) {
                length = tick;
            }

            // 8. 读取图层信息（名称/音量/panning）
            for (int i = 0; i < height; i++) {
                reader.readString(reader.readInt());  // layer name
                if (version >= 4) {
                    reader.readByte();  // layer lock
                }
                reader.readByte();  // layer volume
                if (version >= 2) {
                    reader.readUnsignedByte(); // layer panning
                }
            }

            // 9. 读取自定义乐器（v2 新增）
            int customAmnt = reader.readByte() & 0xFF;
            for (int i = 0; i < customAmnt; i++) {
                String ciName = reader.readString(reader.readInt());
                String ciFile = reader.readString(reader.readInt());
                byte ciPitch = reader.readByte();
                byte ciPressKey = reader.readByte();
                customInstruments.add(new CustomInstrument(
                    i, ciName, ciFile, ciPitch, ciPressKey == 1));
            }

        } catch (IOException e) {
            getInstance().getLogger().log(Level.WARNING, "NBS 数据解析失败", e);
        }
    }

    // ---------- Getter 方法 ----------
    public List<Line> getLines() {
        return lines;
    }

    public Line getLineAtTick(int tick) {
        for (Line line : lines) {
            int lineTick = line.getTick();
            if (lineTick == tick) return line;
            if (lineTick > tick) break;
        }
        return null;
    }

    public float getBPM() { return bpm; }
    public int getLength() { return length; }
    public String getName() { return name; }
    public String getAuthor() { return author; }
    public String getOriginAuthor() { return originAuthor; }
    public String getDescription() { return description; }
    public int getVersion() { return version; }
    public int getHeight() { return height; }

    /** @return 原版乐器数量（等同于 firstCustomInstrument） */
    public int getVanillaInstrumentCount() {
        return firstCustomInstrument;
    }

    /** @return 自定义乐器列表 */
    public List<CustomInstrument> getCustomInstruments() {
        return customInstruments;
    }

    /** 创建适用于此歌曲的 SoundMapper */
    public SoundMapper createSoundMapper() {
        return new SoundMapper(firstCustomInstrument, customInstruments);
    }
}
