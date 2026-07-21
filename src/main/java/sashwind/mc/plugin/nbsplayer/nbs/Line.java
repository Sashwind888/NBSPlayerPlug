package sashwind.mc.plugin.nbsplayer.nbs;

import java.util.ArrayList;
import java.util.List;

public class Line {
  List<Block> blocks = new ArrayList<>();
  /** 该行在 NBS 文件中的实际 tick 位置 */
  private int tick;

  public void add(byte instrumentID, int key) {
    blocks.add(new Block(instrumentID, key));
  }
  public void add(byte instrumentID, int key, byte velocity) {
    blocks.add(new Block(instrumentID, key, velocity));
  }
  public List<Block> read() {
    return blocks;
  }

  public int getTick() { return tick; }
  public void setTick(int tick) { this.tick = tick; }
}
