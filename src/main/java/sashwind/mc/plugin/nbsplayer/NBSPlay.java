package sashwind.mc.plugin.nbsplayer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import sashwind.mc.plugin.nbsplayer.nbs.*;
import static sashwind.mc.plugin.nbsplayer.Nbsplayer.getInstance;

public class NBSPlay {
  /** 所有目标玩家 UUID（支持多玩家同步播放） */
  private final List<UUID> playerUuids = new ArrayList<>();
  private Decoder data;
  private SoundMapper mapper;
  private BukkitRunnable runTask;
  /** 当前播放到的 NBS tick 位置 */
  private int tick = 0;
  private boolean isPlaying = false;
  /** 歌曲总 tick 数（含空 tick） */
  private int totalTicks = 0;

  /** 单玩家构造（兼容旧 API） */
  public NBSPlay(Player p, Decoder data) {
    this.playerUuids.add(p.getUniqueId());
    this.data = data;
    this.totalTicks = data.getLength();
    this.mapper = data.createSoundMapper();
  }

  /** 多玩家构造 */
  public NBSPlay(List<Player> players, Decoder data) {
    for (Player p : players) {
      this.playerUuids.add(p.getUniqueId());
    }
    this.data = data;
    this.totalTicks = data.getLength();
    this.mapper = data.createSoundMapper();
  }

  /** 添加监听玩家 */
  public void addPlayer(Player p) {
    if (!playerUuids.contains(p.getUniqueId())) {
      playerUuids.add(p.getUniqueId());
    }
  }

  /** 移除监听玩家 */
  public void removePlayer(Player p) {
    playerUuids.remove(p.getUniqueId());
  }

  /** 获取在线玩家列表 */
  private List<Player> getOnlinePlayers() {
    List<Player> online = new ArrayList<>();
    for (UUID uuid : playerUuids) {
      Player p = Bukkit.getPlayer(uuid);
      if (p != null && p.isOnline()) online.add(p);
    }
    return online;
  }

  /** 是否有监听玩家 */
  public boolean hasPlayers() {
    return !playerUuids.isEmpty();
  }

  /** 是否正在播放 */
  public boolean isPlaying() {
    return isPlaying;
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

    float bpm = data.getBPM();
    if (bpm <= 0) {
      bpm = 2.0f;
    }

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

          Line line = data.getLineAtTick(tick);
          if (line != null) {
            List<Player> onlinePlayers = getOnlinePlayers();
            if (!onlinePlayers.isEmpty()) {
              for (Block block : line.read()) {
                playBlockForAll(onlinePlayers, block);
              }
            }
          }

          tick++;
        }
      }
    };

    runTask.runTaskTimer(getInstance(), 0L, 1L);
    isPlaying = true;
  }

  /** 为所有玩家播放同一音符（同步） */
  private void playBlockForAll(List<Player> players, Block block) {
    float volume = 1.0f * (block.getVelocity() / 100.0f);
    SoundMapper.Result result = mapper.map(block._instrumentID, block._key);

    if (result.isVanilla()) {
      for (Player p : players) {
        p.getWorld().playSound(p.getLocation(), result.sound, volume, result.pitch);
      }
    } else {
      for (Player p : players) {
        p.getWorld().playSound(p.getLocation(), result.customSound, volume, result.pitch);
      }
    }
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

  /** 兼容旧代码 */
  public static float getPitchFromNoteBlock(byte note) {
    if (note < 0 || note > 24) note = 0;
    return (float) Math.pow(2.0, (note - 12.0) / 12.0);
  }
}
