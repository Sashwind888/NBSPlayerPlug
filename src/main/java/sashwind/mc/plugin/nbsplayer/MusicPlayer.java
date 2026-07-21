package sashwind.mc.plugin.nbsplayer;

import static sashwind.mc.plugin.nbsplayer.Nbsplayer.getInstance;

import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import sashwind.mc.plugin.nbsplayer.nbs.Decoder;

public class MusicPlayer {
    private String name;
    private ArrayList<String> playlist = new ArrayList<>();
    private ArrayList<String> randomPlaylist;
    private byte mode;
    private List<UUID> listenerUuids = new ArrayList<>();      // 收听者 UUID
    private List<UUID> controllerUuids = new ArrayList<>();    // 控制者 UUID
    private UUID creatorUuid;                                   // 创建者 UUID
    private int currentIndex = 0;
    private boolean isPaused = false;
    private boolean isSwitching = false;

    /** v2: 所有监听者共享同一个 NBSPlay 实例，保证同步 */
    private NBSPlay sharedPlayer = null;
    /** 当前正在播放的 URL（用于判断是否需要重新下载） */
    private String currentUrl = null;

    // 构造方法
    public MusicPlayer() {
        this(null, null);
    }

    public MusicPlayer(String name) {
        this(name, null);
    }

    public MusicPlayer(String name, Player creator) {
        this.name = name;
        this.randomPlaylist = new ArrayList<>();
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
    public void addListener(Player p) {
        if (!listenerUuids.contains(p.getUniqueId())) {
            listenerUuids.add(p.getUniqueId());
        }
        // 如果正在播放，将新玩家加入共享播放器
        if (sharedPlayer != null && isPlaying()) {
            sharedPlayer.addPlayer(p);
        }
    }

    public void removeListener(Player p) {
        listenerUuids.remove(p.getUniqueId());
        if (sharedPlayer != null) {
            sharedPlayer.removePlayer(p);
        }
    }

    public List<Player> getOnlineListeners() {
        List<Player> online = new ArrayList<>();
        for (UUID uuid : listenerUuids) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) online.add(p);
        }
        return online;
    }

    public List<UUID> getListenerUuids() { return listenerUuids; }
    public void setListenerUuids(List<UUID> uuids) { this.listenerUuids = new ArrayList<>(uuids); }

    // ---------- 控制者管理 ----------
    public void addController(Player p) {
        if (!controllerUuids.contains(p.getUniqueId())) {
            controllerUuids.add(p.getUniqueId());
        }
    }

    public void removeController(Player p) {
        if (creatorUuid != null && creatorUuid.equals(p.getUniqueId())) return;
        controllerUuids.remove(p.getUniqueId());
    }

    public List<Player> getOnlineControllers() {
        List<Player> online = new ArrayList<>();
        for (UUID uuid : controllerUuids) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) online.add(p);
        }
        return online;
    }

    public List<UUID> getControllerUuids() { return controllerUuids; }
    public void setControllerUuids(List<UUID> uuids) { this.controllerUuids = new ArrayList<>(uuids); }

    public int getControllerCount() { return controllerUuids.size(); }
    public int getListenerCount() { return listenerUuids.size(); }

    public boolean hasControl(Player p) {
        return controllerUuids.contains(p.getUniqueId());
    }

    public boolean isCreator(Player p) {
        return creatorUuid != null && creatorUuid.equals(p.getUniqueId());
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
    public void setPlayMode(ModeList m) {
        mode = (byte) m.ordinal();
        if (m == ModeList.random) {
            randomPlaylist.clear();
            randomPlaylist.addAll(playlist);
            Collections.shuffle(randomPlaylist, new Random());
        }
        currentIndex = 0;
    }

    public void add_Playlist(String url) {
        playlist.add(url);
        if (mode == (byte) ModeList.random.ordinal()) {
            randomPlaylist.clear();
            randomPlaylist.addAll(playlist);
            Collections.shuffle(randomPlaylist, new Random());
        }
    }

    public ArrayList<String> get_Playlist() {
        return (mode == (byte) ModeList.random.ordinal()) ? randomPlaylist : playlist;
    }

    public ArrayList<String> getRawPlaylist() { return playlist; }
    public void setRawPlaylist(List<String> list) {
        this.playlist = new ArrayList<>(list);
        if (mode == (byte) ModeList.random.ordinal()) {
            randomPlaylist.clear();
            randomPlaylist.addAll(playlist);
            Collections.shuffle(randomPlaylist, new Random());
        }
    }

    public void remove_Playlist(int id) {
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

    private int getCurrentPlaylistSize() { return get_Playlist().size(); }

    // ---------- 播放控制 ----------
    public void play() {
        // 如果是暂停状态，恢复
        if (isPaused && sharedPlayer != null) {
            isPaused = false;
            sharedPlayer.play();
            return;
        }

        // 新播放
        List<Player> onlineListeners = getOnlineListeners();
        if (onlineListeners.isEmpty()) {
            System.err.println("没有在线监听玩家，无法播放！");
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

    public void pause() {
        if (sharedPlayer != null) {
            sharedPlayer.pause();
        }
        isPaused = true;
    }

    public void stop() {
        if (sharedPlayer != null) {
            sharedPlayer.stop();
            sharedPlayer = null;
        }
        currentUrl = null;
        isPaused = false;
        currentIndex = 0;
    }

    public void next() {
        if (isSwitching) return;
        if (getCurrentPlaylistSize() == 0) {
            for (Player p : getOnlineListeners()) {
                p.sendMessage("[NBSPlayer] 播放列表为空");
            }
            return;
        }
        isSwitching = true;
        if (sharedPlayer != null) {
            sharedPlayer.stop();
            sharedPlayer = null;
        }
        currentIndex = (currentIndex + 1) % getCurrentPlaylistSize();
        isPaused = false;
        playIndex(currentIndex);
        isSwitching = false;
    }

    public void back() {
        if (isSwitching) return;
        if (getCurrentPlaylistSize() == 0) {
            for (Player p : getOnlineListeners()) {
                p.sendMessage("[NBSPlayer] 播放列表为空");
            }
            return;
        }
        isSwitching = true;
        if (sharedPlayer != null) {
            sharedPlayer.stop();
            sharedPlayer = null;
        }
        currentIndex = (currentIndex - 1 + getCurrentPlaylistSize()) % getCurrentPlaylistSize();
        isPaused = false;
        playIndex(currentIndex);
        isSwitching = false;
    }

    private void playIndex(int index) {
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
            try {
                Decoder decoder = new Decoder(new FileInputStream(file));
                // 所有监听者共享同一个 NBSPlay
                sharedPlayer = new NBSPlay(onlineListeners, decoder);
                for (Player p : onlineListeners) {
                    p.sendMessage("[NBSPlay] 开始播放");
                }
                sharedPlayer.play();

                // 自动播放下一首（顺序/随机模式）
                startAutoNextChecker();

            } catch (Exception ignore) {
                for (Player p : onlineListeners) {
                    p.sendMessage("[NBSPlay] 播放失败！");
                }
            }
        }).exceptionally(ex -> {
            System.err.println("下载失败：" + ex.getMessage());
            return null;
        });
    }

    /** 启动自动切歌检测器 */
    private void startAutoNextChecker() {
        if (mode == (byte) ModeList.single.ordinal()) return;

        new BukkitRunnable() {
            @Override
            public void run() {
                Bukkit.getScheduler().runTaskTimer(getInstance(), new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (isSwitching) return;
                        // 检查共享播放器是否已结束
                        if ((sharedPlayer == null || !sharedPlayer.isPlaying())
                                && !isPaused && currentUrl != null) {
                            next();
                            this.cancel();
                        }
                    }
                }, 0L, 2L);
            }
        }.runTaskLater(getInstance(), 20L);
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
