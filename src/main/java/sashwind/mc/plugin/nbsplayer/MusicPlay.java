package sashwind.mc.plugin.nbsplayer;

import java.io.File;
import java.io.FileInputStream;
import java.util.concurrent.CompletableFuture;

import org.bukkit.entity.Player;

import sashwind.mc.plugin.nbsplayer.nbs.Decoder;

public class MusicPlay {
  boolean _isPlay = false;
  boolean _isPaused = false;
  Player _p = null;
  NBSPlay player;

  public MusicPlay(String url, Player p) {
    _p = p;
    start(url);
  }
  public boolean isPlay() {
    return _isPlay;
  }
  /** 停止播放并重置进度 */
  public void stop() {
    _isPlay = false;
    _isPaused = false;
    if (player != null) player.stop();
    _p.sendMessage("[NBSPlay] 停止播放");
  }
  /** 暂停播放（保留进度，可恢复） */
  public void pause() {
    _isPaused = true;
    if (player != null) player.pause();
    _p.sendMessage("[NBSPlay] 暂停播放");
  }
  /** 从暂停位置恢复播放 */
  public void resume() {
    if (!_isPaused) return;
    _isPaused = false;
    if (player != null) player.play();
    _p.sendMessage("[NBSPlay] 继续播放");
  }
  /** 是否处于暂停状态 */
  public boolean isPaused() {
    return _isPaused;
  }
  public void start(String url) {
    NetworkDownloader nd = new NetworkDownloader();
    _p.sendMessage("[NBSPlay] 正在下载文件...");

    nd.downloadFileAsync(url).thenAccept(file -> {
      try {
        _p.sendMessage("[NBSPlay] 开始播放");
        Decoder decoder = new Decoder(new FileInputStream(file));
        player = new NBSPlay(_p, decoder);
        player.play();
      } catch (Exception ignore) {
        _p.sendMessage("[NBSPlay] 播放失败！");
      }
    })
    .exceptionally(ex -> {
      System.err.println("下载失败：" + ex.getMessage());
      return null;
    });

    _isPlay = true;
    _isPaused = false;
  }

}
