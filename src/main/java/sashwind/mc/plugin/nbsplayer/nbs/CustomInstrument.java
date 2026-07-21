package sashwind.mc.plugin.nbsplayer.nbs;

/**
 * 自定义乐器数据（从 NBS 文件末尾解析）
 */
public class CustomInstrument {
    /** 在自定义乐器列表中的索引（从0开始） */
    public final int index;
    /** 乐器显示名称 */
    public final String name;
    /** 资源包中的声音文件名/路径（如 "my_sounds/piano.ogg"） */
    public final String soundFile;
    /** 基准音高 0-87（A0=0, C8=87，默认 45 = F#4） */
    public final byte pitch;
    /** 钢琴窗是否自动按键 */
    public final boolean pressKey;

    public CustomInstrument(int index, String name, String soundFile, byte pitch, boolean pressKey) {
        this.index = index;
        this.name = name;
        this.soundFile = soundFile;
        this.pitch = pitch;
        this.pressKey = pressKey;
    }
}
