package sashwind.mc.plugin.nbsplayer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import sashwind.mc.plugin.nbsplayer.nbs.*;
import sashwind.mc.plugin.nbsplayer.scheduler.FoliaScheduler;
import sashwind.mc.plugin.nbsplayer.scheduler.WrappedTask;

public class NBSPlay {
  /** 所有目标玩家 UUID（支持多玩家同步播放，使用线程安全集合） */
  private final List<UUID> playerUuids = new CopyOnWriteArrayList<>();
  private final Decoder data;
  private final SoundMapper mapper;
  private volatile WrappedTask currentTask;
  /** 当前播放到的 NBS tick 位置 */
  private volatile int tick = 0;
  private volatile boolean isPlaying = false;
  /** 歌曲总 tick 数（含空 tick） */
  private final int totalTicks;
  /** 播放完成回调 */
  private volatile Runnable onCompletion;

  /** 单玩家构造（兼容旧 API） */
  public NBSPlay(Player p, Decoder data) {
    if (p != null) {
      this.playerUuids.add(p.getUniqueId());
    }
    this.data = data;
    this.totalTicks = data.getLength();
    this.mapper = data.createSoundMapper();
  }

  /** 多玩家构造 */
  public NBSPlay(List<Player> players, Decoder data) {
    if (players != null) {
      for (Player p : players) {
        if (p != null) {
          this.playerUuids.add(p.getUniqueId());
        }
      }
    }
    this.data = data;
    this.totalTicks = data.getLength();
    this.mapper = data.createSoundMapper();
  }

  /** 设置播放完成回调 */
  public void setOnCompletion(Runnable onCompletion) {
    this.onCompletion = onCompletion;
  }

  /** 添加监听玩家 */
  public synchronized void addPlayer(Player p) {
    if (p == null) return;
    if (!playerUuids.contains(p.getUniqueId())) {
      playerUuids.add(p.getUniqueId());
      // 若当前在单玩家循环中且新增了玩家，平滑切换为全局多玩家循环
      if (isPlaying && playerUuids.size() > 1) {
        if (currentTask != null) {
          currentTask.cancel();
          currentTask = null;
        }
        float bpm = data.getBPM();
        if (bpm <= 0) bpm = 2.0f;
        startMultiPlayerLoop(bpm / 20.0f);
      }
    }
  }

  /** 移除监听玩家 */
  public synchronized void removePlayer(Player p) {
    if (p == null) return;
    playerUuids.remove(p.getUniqueId());
    if (playerUuids.isEmpty() && isPlaying) {
      stop();
    }
  }

  /** 获取在线玩家列表 */
  private List<Player> getOnlinePlayers() {
    List<Player> online = new ArrayList<>();
    for (UUID uuid : playerUuids) {
      Player p = Bukkit.getPlayer(uuid);
      if (p != null && p.isOnline()) {
        online.add(p);
      }
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
  public synchronized void play() {
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
    isPlaying = true;

    // 单玩家场景：直接在玩家所属 EntityScheduler 执行
    if (playerUuids.size() == 1) {
      Player target = Bukkit.getPlayer(playerUuids.get(0));
      if (target != null && target.isOnline()) {
        startSinglePlayerLoop(target, nbsTicksPerMCTick);
        return;
      }
    }

    // 多玩家或通用场景：通过 GlobalRegionScheduler 驱动 tick，音效通过 EntityScheduler 分发
    startMultiPlayerLoop(nbsTicksPerMCTick);
  }

  /**
   * 单玩家主播放循环（运行于玩家 EntityScheduler，与玩家 Region 同步）
   */
  private void startSinglePlayerLoop(Player player, float nbsTicksPerMCTick) {
    final float[] accumulator = new float[]{0.0f};

    currentTask = FoliaScheduler.runAtEntityTimer(player, task -> {
      if (!isPlaying || tick >= totalTicks) {
        stop();
        return;
      }
      if (!player.isOnline()) {
        stop();
        return;
      }

      accumulator[0] += nbsTicksPerMCTick;
      while (accumulator[0] >= 1.0f && tick < totalTicks) {
        accumulator[0] -= 1.0f;

        Line line = data.getLineAtTick(tick);
        if (line != null) {
          for (Block block : line.read()) {
            playBlockForPlayer(player, block);
          }
        }
        tick++;
      }

      if (tick >= totalTicks) {
        stop();
      }
    }, () -> {
      // 玩家注销或不可用
      stop();
    }, 1L, 1L);
  }

  /**
   * 多玩家主播放循环（运行于 GlobalRegionScheduler，音符分发到各玩家所属 Region）
   */
  private void startMultiPlayerLoop(float nbsTicksPerMCTick) {
    final float[] accumulator = new float[]{0.0f};

    currentTask = FoliaScheduler.runGlobalTimer(task -> {
      if (!isPlaying || tick >= totalTicks) {
        stop();
        return;
      }

      List<Player> onlinePlayers = getOnlinePlayers();
      if (onlinePlayers.isEmpty()) {
        stop();
        return;
      }

      accumulator[0] += nbsTicksPerMCTick;
      while (accumulator[0] >= 1.0f && tick < totalTicks) {
        accumulator[0] -= 1.0f;

        Line line = data.getLineAtTick(tick);
        if (line != null) {
          List<Block> blocks = line.read();
          if (!blocks.isEmpty()) {
            for (Player p : onlinePlayers) {
              FoliaScheduler.runAtEntity(p, () -> {
                for (Block block : blocks) {
                  playBlockForPlayer(p, block);
                }
              });
            }
          }
        }
        tick++;
      }

      if (tick >= totalTicks) {
        stop();
      }
    }, 1L, 1L);
  }

  /** 为单个玩家播放音符（确保在玩家自身 Region 线程执行） */
  private void playBlockForPlayer(Player p, Block block) {
    if (p == null || !p.isOnline()) return;
    float volume = 1.0f * (block.getVelocity() / 100.0f);
    SoundMapper.Result result = mapper.map(block._instrumentID, block._key);
    Location loc = p.getLocation();

    if (result.isVanilla()) {
      p.getWorld().playSound(loc, result.sound, volume, result.pitch);
    } else {
      p.getWorld().playSound(loc, result.customSound, volume, result.pitch);
    }
  }

  /**
   * 暂停播放
   */
  public synchronized void pause() {
    if (currentTask != null) {
      currentTask.cancel();
      currentTask = null;
    }
    isPlaying = false;
  }

  /**
   * 停止播放
   */
  public synchronized void stop() {
    if (currentTask != null) {
      currentTask.cancel();
      currentTask = null;
    }
    boolean wasPlaying = isPlaying;
    isPlaying = false;
    tick = 0;

    if (wasPlaying && onCompletion != null) {
      Runnable cb = onCompletion;
      onCompletion = null;
      cb.run();
    }
  }

  /** 兼容旧代码 */
  public static float getPitchFromNoteBlock(byte note) {
    if (note < 0 || note > 24) note = 0;
    return (float) Math.pow(2.0, (note - 12.0) / 12.0);
  }
}
