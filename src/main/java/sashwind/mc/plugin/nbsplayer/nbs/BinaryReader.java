package sashwind.mc.plugin.nbsplayer.nbs;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class BinaryReader implements AutoCloseable {
    private final DataInputStream stream;

    public BinaryReader(InputStream in) {
        this.stream = new DataInputStream(in);
    }

    public byte readByte() throws IOException {
        return stream.readByte();
    }

    /** 读取无符号字节，作为 int 返回 (0~255) */
    public int readUnsignedByte() throws IOException {
        return stream.readUnsignedByte();
    }

    public short readShort() throws IOException {
        // 读入大端序的 2 字节，转换为小端序
        short bigEndian = stream.readShort();
        return (short) (((bigEndian & 0xFF) << 8) | ((bigEndian >> 8) & 0xFF));
    }

    public int readInt() throws IOException {
        int bigEndian = stream.readInt();
        // 转为小端序
        return ((bigEndian & 0xFF) << 24)
                | ((bigEndian & 0xFF00) << 8)
                | ((bigEndian & 0xFF0000) >> 8)
                | ((bigEndian >> 24) & 0xFF);
    }

    /**
     * 读取指定长度的字符串（NBS 标准：长度在前，字节为扩展 ASCII，即 Latin‑1）
     * 同时将 0x0D 替换为空格，与原 Java 实现保持一致。
     */
    public String readString(int length) throws IOException {
        if (length <= 0) return "";
        byte[] bytes = new byte[length];
        stream.readFully(bytes);
        String s = new String(bytes, StandardCharsets.ISO_8859_1);
        return s.replace('\r', ' ');
    }

    // 保留，但 NBS 中不会用到 short 长度
    public String readString(short length) throws IOException {
        return readString((int) length);
    }

    @Override
    public void close() throws IOException {
        stream.close();
    }
}