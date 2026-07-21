package sashwind.mc.plugin.nbsplayer.nbs;

public class Block {
    public byte _instrumentID;
    public byte _pitch;
    public byte _velocity;   // 新增

    public Block(byte instrumentID, byte pitch) {
        this(instrumentID, pitch, (byte) 100); // 默认 100% 音量
    }

    public Block(byte instrumentID, byte pitch, byte velocity) {
        _instrumentID = instrumentID;
        _pitch = pitch;
        _velocity = velocity;
    }

    public byte readID()    { return _instrumentID; }
    public byte readPitch() { return _pitch; }
    public byte getVelocity() { return _velocity; }
}