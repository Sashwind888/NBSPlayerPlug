package sashwind.mc.plugin.nbsplayer;

import static sashwind.mc.plugin.nbsplayer.Nbsplayer.getInstance;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class MusicPlayer {
    private String name;
    private ArrayList<String> playlist = new ArrayList<>();
    private ArrayList<String> randomPlaylist;
    private byte mode;
    private List<UUID> listenerUuids = new ArrayList<>();      // 收听者 UUID
    private List<UUID> controllerUuids = new ArrayList<>();    // 控制者 UUID
    private UUID creatorUuid;                                   // 创建者 UUID
    private int currentIndex = 0;
    private Map<UUID, MusicPlay> currentMusicPlays = new HashMap<>();
    private boolean isPaused = false;
    private boolean isSwitching = false;

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
            // 创建者自动成为控制者
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
    }

    public void removeListener(Player p) {
        listenerUuids.remove(p.getUniqueId());
        MusicPlay mp = currentMusicPlays.remove(p.getUniqueId());
        if (mp != null) mp.stop();
    }

    /** 获取当前在线的收听者 */
    public List<Player> getOnlineListeners() {
        List<Player> online = new ArrayList<>();
        for (UUID uuid : listenerUuids) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) online.add(p);
        }
        return online;
    }

    /** 获取收听者 UUID 列表（用于持久化） */
    public List<UUID> getListenerUuids() {
        return listenerUuids;
    }

    /** 从 UUID 列表加载收听者（用于从配置文件恢复） */
    public void setListenerUuids(List<UUID> uuids) {
        this.listenerUuids = new ArrayList<>(uuids);
    }

    // ---------- 控制者管理 ----------
    public void addController(Player p) {
        if (!controllerUuids.contains(p.getUniqueId())) {
            controllerUuids.add(p.getUniqueId());
        }
    }

    public void removeController(Player p) {
        // 不允许移除创建者
        if (creatorUuid != null && creatorUuid.equals(p.getUniqueId())) return;
        controllerUuids.remove(p.getUniqueId());
    }

    /** 获取当前在线的控制者 */
    public List<Player> getOnlineControllers() {
        List<Player> online = new ArrayList<>();
        for (UUID uuid : controllerUuids) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) online.add(p);
        }
        return online;
    }

    /** 获取控制者 UUID 列表（用于持久化） */
    public List<UUID> getControllerUuids() {
        return controllerUuids;
    }

    /** 从 UUID 列表加载控制者（用于从配置文件恢复） */
    public void setControllerUuids(List<UUID> uuids) {
        this.controllerUuids = new ArrayList<>(uuids);
    }

    /** 获取控制者/收听者数量（用于显示） */
    public int getControllerCount() { return controllerUuids.size(); }
    public int getListenerCount() { return listenerUuids.size(); }

    public boolean hasControl(Player p) {
        return controllerUuids.contains(p.getUniqueId());
    }

    /** 检查玩家是否是此播放器的创建者 */
    public boolean isCreator(Player p) {
        return creatorUuid != null && creatorUuid.equals(p.getUniqueId());
    }

    /** 检查 UUID 是否是创建者 */
    public boolean isCreator(UUID uuid) {
        return creatorUuid != null && creatorUuid.equals(uuid);
    }

    /** 检查玩家是否有管理权限（创建者 或 在控制者列表中） */
    public boolean canManage(Player p) {
        return isCreator(p) || hasControl(p);
    }

    /** 获取创建者 UUID */
    public UUID getCreatorUuid() {
        return creatorUuid;
    }

    /** 设置创建者 UUID（用于从配置文件恢复） */
    public void setCreatorUuid(UUID uuid) {
        this.creatorUuid = uuid;
    }

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
        if (mode == (byte) ModeList.random.ordinal()) {
            return randomPlaylist;
        } else {
            return playlist;
        }
    }

    /** 获取原始播放列表（用于持久化） */
    public ArrayList<String> getRawPlaylist() {
        return playlist;
    }

    /** 设置原始播放列表（用于从配置文件恢复） */
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

    private int getCurrentPlaylistSize() {
        return get_Playlist().size();
    }

    private ArrayList<String> getCurrentPlaylist() {
        return get_Playlist();
    }

    // ---------- 播放控制 ----------
    public void play() {
        // 如果是暂停状态，恢复现有播放
        if (isPaused) {
            isPaused = false;
            boolean resumed = false;
            for (MusicPlay mp : currentMusicPlays.values()) {
                if (mp.isPaused()) {
                    mp.resume();
                    resumed = true;
                }
            }
            if (resumed) return;
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
        if (currentMusicPlays.isEmpty()) return;
        for (MusicPlay mp : currentMusicPlays.values()) {
            if (mp.isPlay()) mp.pause();
        }
        isPaused = true;
    }

    public void stop() {
        for (MusicPlay mp : currentMusicPlays.values()) {
            mp.stop();
        }
        currentMusicPlays.clear();
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
        for (MusicPlay mp : currentMusicPlays.values()) {
            mp.stop();
        }
        currentMusicPlays.clear();
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
        for (MusicPlay mp : currentMusicPlays.values()) {
            mp.stop();
        }
        currentMusicPlays.clear();
        currentIndex = (currentIndex - 1 + getCurrentPlaylistSize()) % getCurrentPlaylistSize();
        isPaused = false;
        playIndex(currentIndex);
        isSwitching = false;
    }

    private void playIndex(int index) {
        ArrayList<String> list = getCurrentPlaylist();
        if (index < 0 || index >= list.size()) return;
        String url = list.get(index);

        List<Player> onlineListeners = getOnlineListeners();
        for (Player p : onlineListeners) {
            MusicPlay mp = new MusicPlay(url, p);
            currentMusicPlays.put(p.getUniqueId(), mp);
        }

        if (mode == (byte) ModeList.sequence.ordinal() || mode == (byte) ModeList.random.ordinal()) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    Bukkit.getScheduler().runTaskTimer(getInstance(), new BukkitRunnable() {
                        @Override
                        public void run() {
                            if (isSwitching) return;
                            boolean allStopped = true;
                            for (MusicPlay mp : currentMusicPlays.values()) {
                                if (mp != null && mp.isPlay()) {
                                    allStopped = false;
                                    break;
                                }
                            }
                            if (allStopped && !currentMusicPlays.isEmpty() && !isPaused) {
                                next();
                                this.cancel();
                            }
                        }
                    }, 0L, 2L);
                }
            }.runTaskLater(getInstance(), 20L);
        }
    }

    public boolean isPlaying() {
        for (MusicPlay mp : currentMusicPlays.values()) {
            if (mp.isPlay()) return true;
        }
        return false;
    }

    public ModeList getPlayMode() {
        return ModeList.values()[mode];
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}

enum ModeList {
  single,
  random,
  sequence,
}
