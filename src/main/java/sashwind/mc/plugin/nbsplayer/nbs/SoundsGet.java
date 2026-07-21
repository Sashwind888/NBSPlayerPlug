package sashwind.mc.plugin.nbsplayer.nbs;

import org.bukkit.Sound;

public class SoundsGet {
  public static Sound getSound(byte id) {
    switch (id) {
      case 0:  return Sound.BLOCK_NOTE_BLOCK_HARP;          // Harp
      case 1:  return Sound.BLOCK_NOTE_BLOCK_BASS;          // DoubleBase（注意不是BASEDRUM）
      case 2:  return Sound.BLOCK_NOTE_BLOCK_BASEDRUM;      // BassDrum
      case 3:  return Sound.BLOCK_NOTE_BLOCK_SNARE;         // SnareDrum
      case 4:  return Sound.BLOCK_NOTE_BLOCK_HAT;           // Click（对应游戏中的Hat）
      case 5:  return Sound.BLOCK_NOTE_BLOCK_GUITAR;        // Guitar
      case 6:  return Sound.BLOCK_NOTE_BLOCK_FLUTE;         // Flute
      case 7:  return Sound.BLOCK_NOTE_BLOCK_BELL;          // Bell
      case 8:  return Sound.BLOCK_NOTE_BLOCK_CHIME;         // Chime
      case 9:  return Sound.BLOCK_NOTE_BLOCK_XYLOPHONE;     // Xylophone
      case 10: return Sound.BLOCK_NOTE_BLOCK_IRON_XYLOPHONE; // IronXylophone
      case 11: return Sound.BLOCK_NOTE_BLOCK_COW_BELL;      // CowBell
      case 12: return Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO;    // Didgeridoo
      case 13: return Sound.BLOCK_NOTE_BLOCK_BIT;           // Bit
      case 14: return Sound.BLOCK_NOTE_BLOCK_BANJO;         // Banjo
      case 15: return Sound.BLOCK_NOTE_BLOCK_PLING;         // Pling
      default: return Sound.BLOCK_NOTE_BLOCK_HARP;          // 默认（或抛异常）
    }
  }
}
