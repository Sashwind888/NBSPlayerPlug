package sashwind.mc.plugin.nbsplayer.nbs;

public class Block {
    /** NBS 乐器 ID */
    public byte _instrumentID;
    /** 归一化音高键（NBS raw - 33），范围 -33~54，扩展音域 -12~36 */
    public int _key;
    /** 音符音量 0~100 */
    public byte _velocity;

    public Block(byte instrumentID, int key) {
        this(instrumentID, key, (byte) 100);
    }

    public Block(byte instrumentID, int key, byte velocity) {
        _instrumentID = instrumentID;
        _key = key;
        _velocity = velocity;
    }

    public byte readID()    { return _instrumentID; }
    public int  readKey()   { return _key; }
    public byte getVelocity() { return _velocity; }
}
