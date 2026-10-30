package sashwind.mc.plugin.nbsplayer;

import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import sashwind.mc.plugin.nbsplayer.nbs.Decoder;
import sashwind.mc.plugin.nbsplayer.scheduler.FoliaScheduler;
import sashwind.mc.plugin.nbsplayer.scheduler.WrappedTask;

public class MusicPlayer {
    private String name;
    private final List<String> playlist = new CopyOnWriteArrayList<>();
    private final List<String> randomPlaylist = new CopyOnWriteArrayList<>();
    private volatile byte mode;
    private final List<UUID> listenerUuids = new CopyOnWriteArrayList<>();      // 收听者 UUID
    private final List<UUID> controllerUuids = new CopyOnWriteArrayList<>();    // 控制者 UUID
    private volatile UUID creatorUuid;                                          // 创建者 UUID
    private volatile int currentIndex = 0;
    private volatile boolean isPaused = false;
    private volatile boolean isSwitching = false;

    /** v2: 所有监听者共享同一个 NBSPlay 实例，保证同步 */
    private volatile NBSPlay sharedPlayer = null;
    /** 当前正在播放的 URL（用于判断是否需要重新下载） */
    private volatile String currentUrl = null;
    /** 自动切歌检测任务 */
    private volatile WrappedTask autoNextCheckerTask = null;

    // 构造方法
    public MusicPlayer() {
        this(null, null);
    }

    public MusicPlayer(String name) {
        this(name, null);
    }

    public MusicPlayer(String name, Player creator) {
        this.name = name;
        this.mode = (byte) ModeList.sequence.ordinal();
        this.currentIndex = 0;
        if (creator != null) {
            this.creatorUuid = creator.getUniqueId();
            if (!controllerUuids.contains(creator.getUniqueId())) {
                controllerUuids.add(creator.getUniqueId());
            }
        }
    }

    // ---------- 收听者管理 ----------
    public synchronized void addListener(Player p) {
        if (p == null) return;
        if (!listenerUuids.contains(p.getUniqueId())) {
            listenerUuids.add(p.getUniqueId());
        }
        // 如果正在播放，将新玩家加入共享播放器
        if (sharedPlayer != null && isPlaying()) {
            sharedPlayer.addPlayer(p);
        }
    }

    public synchronized void removeListener(Player p) {
        if (p == null) return;
        listenerUuids.remove(p.getUniqueId());
        if (sharedPlayer != null) {
            sharedPlayer.removePlayer(p);
        }
    }

    public List<Player> getOnlineListeners() {
        List<Player> online = new ArrayList<>();
        for (UUID uuid : listenerUuids) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                online.add(p);
            }
        }
        return online;
    }

    public List<UUID> getListenerUuids() { return new ArrayList<>(listenerUuids); }
    public synchronized void setListenerUuids(List<UUID> uuids) {
        this.listenerUuids.clear();
        if (uuids != null) {
            this.listenerUuids.addAll(uuids);
        }
    }

    // ---------- 控制者管理 ----------
    public synchronized void addController(Player p) {
        if (p == null) return;
        if (!controllerUuids.contains(p.getUniqueId())) {
            controllerUuids.add(p.getUniqueId());
        }
    }

    public synchronized void removeController(Player p) {
        if (p == null) return;
        if (creatorUuid != null && creatorUuid.equals(p.getUniqueId())) return;
        controllerUuids.remove(p.getUniqueId());
    }

    public List<Player> getOnlineControllers() {
        List<Player> online = new ArrayList<>();
        for (UUID uuid : controllerUuids) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                online.add(p);
            }
        }
        return online;
    }

    public List<UUID> getControllerUuids() { return new ArrayList<>(controllerUuids); }
    public synchronized void setControllerUuids(List<UUID> uuids) {
        this.controllerUuids.clear();
        if (uuids != null) {
            this.controllerUuids.addAll(uuids);
        }
    }

    public int getControllerCount() { return controllerUuids.size(); }
    public int getListenerCount() { return listenerUuids.size(); }

    public boolean hasControl(Player p) {
        if (p == null) return false;
        return controllerUuids.contains(p.getUniqueId());
    }

    public boolean isCreator(Player p) {
        return p != null && isCreator(p.getUniqueId());
    }

    public boolean isCreator(UUID uuid) {
        return creatorUuid != null && creatorUuid.equals(uuid);
    }

    public boolean canManage(Player p) {
        return isCreator(p) || hasControl(p);
    }

    public UUID getCreatorUuid() { return creatorUuid; }
    public void setCreatorUuid(UUID uuid) { this.creatorUuid = uuid; }

    // ---------- 播放列表管理 ----------
    public synchronized void setPlayMode(ModeList m) {
        mode = (byte) m.ordinal();
        if (m == ModeList.random) {
            randomPlaylist.clear();
            randomPlaylist.addAll(playlist);
            Collections.shuffle(randomPlaylist, new Random());
        }
        currentIndex = 0;
    }

    public synchronized void add_Playlist(String url) {
        playlist.add(url);
        if (mode == (byte) ModeList.random.ordinal()) {
            randomPlaylist.clear();
            randomPlaylist.addAll(playlist);
            Collections.shuffle(randomPlaylist, new Random());
        }
    }

    public ArrayList<String> get_Playlist() {
        return (mode == (byte) ModeList.random.ordinal()) ? new ArrayList<>(randomPlaylist) : new ArrayList<>(playlist);
    }

    public ArrayList<String> getRawPlaylist() { return new ArrayList<>(playlist); }
    public synchronized void setRawPlaylist(List<String> list) {
        this.playlist.clear();
        if (list != null) {
            this.playlist.addAll(list);
        }
        if (mode == (byte) ModeList.random.ordinal()) {
            randomPlaylist.clear();
            randomPlaylist.addAll(this.playlist);
            Collections.shuffle(randomPlaylist, new Random());
        }
    }

    public synchronized void remove_Playlist(int id) {
        if (id < 0 || id >= playlist.size()) return;
        playlist.remove(id);
        if (mode == (byte) ModeList.random.ordinal()) {
            randomPlaylist.clear();
            randomPlaylist.addAll(playlist);
            Collections.shuffle(randomPlaylist, new Random());
        }
        if (currentIndex >= getCurrentPlaylistSize()) {
            currentIndex = Math.max(0, getCurrentPlaylistSize() - 1);
        }
    }

    private int getCurrentPlaylistSize() { return (mode == (byte) ModeList.random.ordinal()) ? randomPlaylist.size() : playlist.size(); }

    // ---------- 播放控制 ----------
    public synchronized void play() {
        // 如果是暂停状态，恢复
        if (isPaused && sharedPlayer != null) {
            isPaused = false;
            sharedPlayer.play();
            startAutoNextChecker();
            return;
        }

        // 新播放
        List<Player> onlineListeners = getOnlineListeners();
        if (onlineListeners.isEmpty()) {
            return;
        }
        if (getCurrentPlaylistSize() == 0) {
            for (Player p : onlineListeners) {
                p.sendMessage("[NBSPlayer] 播放列表为空！");
            }
            return;
        }
        isPaused = false;
        playIndex(currentIndex);
    }

    public synchronized void pause() {
        if (sharedPlayer != null) {
            sharedPlayer.pause();
        }
        isPaused = true;
        stopAutoNextChecker();
    }

    public synchronized void stop() {
        stopAutoNextChecker();
        if (sharedPlayer != null) {
            sharedPlayer.stop();
            sharedPlayer = null;
        }
        currentUrl = null;
        isPaused = false;
        currentIndex = 0;
    }

    public synchronized void next() {
        if (isSwitching) return;
        if (getCurrentPlaylistSize() == 0) {
            for (Player p : getOnlineListeners()) {
                p.sendMessage("[NBSPlayer] 播放列表为空");
            }
            return;
        }
        isSwitching = true;
        stopAutoNextChecker();
        if (sharedPlayer != null) {
            sharedPlayer.stop();
            sharedPlayer = null;
        }
        currentIndex = (currentIndex + 1) % getCurrentPlaylistSize();
        isPaused = false;
        playIndex(currentIndex);
        isSwitching = false;
    }

    public synchronized void back() {
        if (isSwitching) return;
        if (getCurrentPlaylistSize() == 0) {
            for (Player p : getOnlineListeners()) {
                p.sendMessage("[NBSPlayer] 播放列表为空");
            }
            return;
        }
        isSwitching = true;
        stopAutoNextChecker();
        if (sharedPlayer != null) {
            sharedPlayer.stop();
            sharedPlayer = null;
        }
        currentIndex = (currentIndex - 1 + getCurrentPlaylistSize()) % getCurrentPlaylistSize();
        isPaused = false;
        playIndex(currentIndex);
        isSwitching = false;
    }

    private synchronized void playIndex(int index) {
        ArrayList<String> list = get_Playlist();
        if (index < 0 || index >= list.size()) return;
        String url = list.get(index);

        List<Player> onlineListeners = getOnlineListeners();
        if (onlineListeners.isEmpty()) return;

        // 对所有监听者发送消息
        for (Player p : onlineListeners) {
            p.sendMessage("[NBSPlay] 正在下载文件...");
        }

        currentUrl = url;

        // 异步下载 + 解码，然后创建共享 NBSPlay
        NetworkDownloader nd = new NetworkDownloader();
        nd.downloadFileAsync(url).thenAccept(file -> {
            // 在全局区域执行播放启动，保证与多玩家同步
            FoliaScheduler.runGlobal(() -> {
                synchronized (MusicPlayer.this) {
                    if (file == null) {
                        for (Player p : getOnlineListeners()) {
                            p.sendMessage("[NBSPlay] 文件下载失败！");
                        }
                        return;
                    }
                    try {
                        Decoder decoder = new Decoder(new FileInputStream(file));
                        // 所有监听者共享同一个 NBSPlay
                        List<Player> currentOnline = getOnlineListeners();
                        sharedPlayer = new NBSPlay(currentOnline, decoder);
                        // 当歌曲正常播放结束时的自动切歌回调
                        sharedPlayer.setOnCompletion(() -> {
                            FoliaScheduler.runGlobal(() -> {
                                synchronized (MusicPlayer.this) {
                                    if (mode != (byte) ModeList.single.ordinal() && !isPaused && currentUrl != null) {
                                        next();
                                    }
                                }
                            });
                        });

                        for (Player p : currentOnline) {
                            p.sendMessage("[NBSPlay] 开始播放");
                        }
                        sharedPlayer.play();

                        // 启动检测器兜底
                        startAutoNextChecker();

                    } catch (Exception e) {
                        for (Player p : getOnlineListeners()) {
                            p.sendMessage("[NBSPlay] 播放失败！");
                        }
                    }
                }
            });
        }).exceptionally(ex -> {
            System.err.println("下载失败：" + ex.getMessage());
            return null;
        });
    }

    private synchronized void stopAutoNextChecker() {
        if (autoNextCheckerTask != null) {
            autoNextCheckerTask.cancel();
            autoNextCheckerTask = null;
        }
    }

    /** 启动自动切歌检测器（使用 Folia 兼容的全局定时任务） */
    private synchronized void startAutoNextChecker() {
        stopAutoNextChecker();
        if (mode == (byte) ModeList.single.ordinal()) return;

        // 延迟 20 ticks（1秒），然后每 2 ticks 轮询检查一次
        autoNextCheckerTask = FoliaScheduler.runGlobalTimer(task -> {
            synchronized (MusicPlayer.this) {
                if (isSwitching) return;
                // 检查共享播放器是否已结束
                if ((sharedPlayer == null || !sharedPlayer.isPlaying())
                        && !isPaused && currentUrl != null) {
                    task.cancel();
                    autoNextCheckerTask = null;
                    next();
                }
            }
        }, 20L, 2L);
    }

    public boolean isPlaying() {
        return sharedPlayer != null && sharedPlayer.isPlaying();
    }

    public ModeList getPlayMode() {
        return ModeList.values()[mode];
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}

enum ModeList {
  single,
  random,
  sequence,
}
