package sashwind.mc.plugin.nbsplayer.nbs;

import java.util.ArrayList;
import java.util.List;

public class Line {
  List<Block> blocks = new ArrayList<>();
  /** 该行在 NBS 文件中的实际 tick 位置 */
  private int tick;

  public void add(byte instrumentID, byte pitch) {
    blocks.add(new Block(instrumentID, pitch));
  }
  public void add(byte instrumentID, byte pitch, byte velocity) {
    blocks.add(new Block(instrumentID, pitch, velocity));
  }
  public List<Block> read() {
    return blocks;
  }

  public int getTick() {
    return tick;
  }

  public void setTick(int tick) {
    this.tick = tick;
  }
}
