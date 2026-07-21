package sashwind.mc.plugin.nbsplayer;

import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import sashwind.mc.plugin.nbsplayer.nbs.*;
import static sashwind.mc.plugin.nbsplayer.Nbsplayer.getInstance;

public class NBSPlay {
  private Player player;
  private Decoder data;
  private BukkitRunnable runTask;
  /** 当前播放到的 NBS tick 位置 */
  private int tick = 0;
  private boolean isPlaying = false;
  /** 歌曲总 tick 数（含空 tick） */
  private int totalTicks = 0;

  public NBSPlay(Player p, Decoder data) {
    this.player = p;
    this.data = data;
    // 使用歌曲头部的总长度，而非 lines 数量
    this.totalTicks = data.getLength();
  }

  /**
   * 开始播放
   */
  public void play() {
    if (isPlaying) {
      stop();
    }

    if (tick < 0 || tick >= totalTicks) {
      tick = 0;
    }

    // bpm = tempo / 100，即每秒的 NBS tick 数
    float bpm = data.getBPM();
    if (bpm <= 0) {
      bpm = 2.0f; // 默认值
    }

    // 使用浮点累加器避免 Integer delay 的舍入误差
    // 每个 MC tick (50ms) 推进 bpm/20 个 NBS tick
    // 例如 bpm=12.5 → 每 MC tick 推进 0.625 NBS tick
    // 累加器达到 1.0 时播放一个 NBS tick，确保平均速度精确
    final float nbsTicksPerMCTick = bpm / 20.0f;

    runTask = new BukkitRunnable() {
      float accumulator = 0.0f;

      @Override
      public void run() {
        if (tick >= totalTicks) {
          stop();
          return;
        }

        accumulator += nbsTicksPerMCTick;
        while (accumulator >= 1.0f && tick < totalTicks) {
          accumulator -= 1.0f;

          // 播放当前 tick 的所有音符（可能跨多个 layer）
          Line line = data.getLineAtTick(tick);
          if (line != null) {
            for (Block block : line.read()) {
                Sound sound = SoundsGet.getSound(block._instrumentID);
                float pitch = getPitchFromNoteBlock(block._pitch);
                float volume = 1.0f * (block.getVelocity() / 100.0f);
                player.getWorld().playSound(player.getLocation(), sound, volume, pitch);
            }
          }
          // 若 line == null，说明此 tick 为空行，自然静默

          tick++;
        }
      }
    };

    // 每 MC tick 运行一次以获得最高精度
    runTask.runTaskTimer(getInstance(), 0L, 1L);
    isPlaying = true;
  }

  /**
   * 暂停播放
   */
  public void pause() {
    if (runTask != null) {
      runTask.cancel();
      runTask = null;
    }
    isPlaying = false;
  }

  /**
   * 停止播放
   */
  public void stop() {
    if (runTask != null) {
      runTask.cancel();
      runTask = null;
    }
    isPlaying = false;
    tick = 0;
  }

  /**
   * 将 NBS 音符号（0~24，对应 F#0~F#2）转为 Minecraft 音高值
   * @param note 0~24 的音符号
   * @return Minecraft playSound 使用的 pitch 参数
   */
  public static float getPitchFromNoteBlock(byte note) {
    if (note < 0 || note > 24) {
      note = 0;
    }
    // note=12 对应 F#1（基准音），pitch=1.0
    return (float) Math.pow(2.0, (note - 12.0) / 12.0);
  }
}
