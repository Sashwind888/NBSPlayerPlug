package sashwind.mc.plugin.nbsplayer;

import java.io.FileInputStream;

import org.bukkit.entity.Player;

import sashwind.mc.plugin.nbsplayer.nbs.Decoder;
import sashwind.mc.plugin.nbsplayer.scheduler.FoliaScheduler;

public class MusicPlay {
  private volatile boolean _isPlay = false;
  private volatile boolean _isPaused = false;
  private final Player _p;
  private volatile NBSPlay player;

  public MusicPlay(String url, Player p) {
    this._p = p;
    start(url);
  }

  public boolean isPlay() {
    return _isPlay;
  }

  /** 停止播放并重置进度 */
  public synchronized void stop() {
    _isPlay = false;
    _isPaused = false;
    if (player != null) {
      player.stop();
      player = null;
    }
    if (_p != null && _p.isOnline()) {
      _p.sendMessage("[NBSPlay] 停止播放");
    }
  }

  /** 暂停播放（保留进度，可恢复） */
  public synchronized void pause() {
    _isPaused = true;
    if (player != null) {
      player.pause();
    }
    if (_p != null && _p.isOnline()) {
      _p.sendMessage("[NBSPlay] 暂停播放");
    }
  }

  /** 从暂停位置恢复播放 */
  public synchronized void resume() {
    if (!_isPaused) return;
    _isPaused = false;
    if (player != null) {
      player.play();
    }
    if (_p != null && _p.isOnline()) {
      _p.sendMessage("[NBSPlay] 继续播放");
    }
  }

  /** 是否处于暂停状态 */
  public boolean isPaused() {
    return _isPaused;
  }

  public synchronized void start(String url) {
    if (_p == null || !_p.isOnline()) return;

    NetworkDownloader nd = new NetworkDownloader();
    _p.sendMessage("[NBSPlay] 正在下载文件...");

    nd.downloadFileAsync(url).thenAccept(file -> {
      if (file == null) {
        if (_p.isOnline()) {
          _p.sendMessage("[NBSPlay] 下载失败！");
        }
        return;
      }
      // 在玩家所属 Region 线程启动播放
      FoliaScheduler.runAtEntity(_p, () -> {
        synchronized (MusicPlay.this) {
          if (!_isPlay) {
            // 如果在下载完成前已被停止
            return;
          }
          try {
            if (_p.isOnline()) {
              _p.sendMessage("[NBSPlay] 开始播放");
            }
            Decoder decoder = new Decoder(new FileInputStream(file));
            if (player != null) {
              player.stop();
            }
            player = new NBSPlay(_p, decoder);
            player.play();
          } catch (Exception ignore) {
            if (_p.isOnline()) {
              _p.sendMessage("[NBSPlay] 播放失败！");
            }
          }
        }
      });
    }).exceptionally(ex -> {
      System.err.println("下载失败：" + ex.getMessage());
      return null;
    });

    _isPlay = true;
    _isPaused = false;
  }
}
