package sashwind.mc.plugin.nbsplayer;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

import static sashwind.mc.plugin.nbsplayer.Nbsplayer.getInstance;

/**
 * 播放器数据持久化管理：保存/加载到 plugins/nbsplayer/players.yml
 */
public class PlayerDataManager {

    private static final String FILE_NAME = "players.yml";

    /**
     * 将所有播放器保存到 YAML 文件
     */
    public static void saveAll(List<MusicPlayer> players) {
        YamlConfiguration yaml = new YamlConfiguration();

        for (int i = 0; i < players.size(); i++) {
            MusicPlayer mp = players.get(i);
            String path = "players." + i + ".";

            yaml.set(path + "name", mp.getName());
            if (mp.getCreatorUuid() != null) {
                yaml.set(path + "creator", mp.getCreatorUuid().toString());
            }
            yaml.set(path + "mode", mp.getPlayMode().name());

            // 播放列表
            List<String> urls = mp.getRawPlaylist();
            yaml.set(path + "playlist", urls.isEmpty() ? new ArrayList<>() : urls);

            // 控制者 UUID
            List<String> controllerStrs = new ArrayList<>();
            for (UUID uuid : mp.getControllerUuids()) {
                controllerStrs.add(uuid.toString());
            }
            yaml.set(path + "controllers", controllerStrs);

            // 收听者 UUID
            List<String> listenerStrs = new ArrayList<>();
            for (UUID uuid : mp.getListenerUuids()) {
                listenerStrs.add(uuid.toString());
            }
            yaml.set(path + "listeners", listenerStrs);
        }

        File file = new File(getInstance().getDataFolder(), FILE_NAME);
        try {
            yaml.save(file);
        } catch (IOException e) {
            getInstance().getLogger().log(Level.WARNING, "保存播放器数据失败", e);
        }
    }

    /**
     * 从 YAML 文件加载所有播放器
     * @return 加载的播放器列表（可能为空）
     */
    public static List<MusicPlayer> loadAll() {
        List<MusicPlayer> players = new ArrayList<>();
        File file = new File(getInstance().getDataFolder(), FILE_NAME);

        if (!file.exists()) {
            return players;
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);

        if (!yaml.contains("players")) {
            return players;
        }

        for (String key : yaml.getConfigurationSection("players").getKeys(false)) {
            String path = "players." + key + ".";

            String name = yaml.getString(path + "name");
            MusicPlayer mp = new MusicPlayer(name);

            // 创建者
            String creatorStr = yaml.getString(path + "creator");
            if (creatorStr != null && !creatorStr.isEmpty()) {
                try {
                    mp.setCreatorUuid(UUID.fromString(creatorStr));
                } catch (IllegalArgumentException ignored) {}
            }

            // 模式
            String modeStr = yaml.getString(path + "mode", "sequence");
            try {
                mp.setPlayMode(ModeList.valueOf(modeStr));
            } catch (IllegalArgumentException e) {
                mp.setPlayMode(ModeList.sequence);
            }

            // 播放列表
            List<String> urls = yaml.getStringList(path + "playlist");
            if (urls != null) {
                mp.setRawPlaylist(urls);
            }

            // 控制者
            List<String> controllerStrs = yaml.getStringList(path + "controllers");
            if (controllerStrs != null) {
                List<UUID> controllerUuids = new ArrayList<>();
                for (String s : controllerStrs) {
                    try {
                        controllerUuids.add(UUID.fromString(s));
                    } catch (IllegalArgumentException ignored) {}
                }
                mp.setControllerUuids(controllerUuids);
            }

            // 收听者
            List<String> listenerStrs = yaml.getStringList(path + "listeners");
            if (listenerStrs != null) {
                List<UUID> listenerUuids = new ArrayList<>();
                for (String s : listenerStrs) {
                    try {
                        listenerUuids.add(UUID.fromString(s));
                    } catch (IllegalArgumentException ignored) {}
                }
                mp.setListenerUuids(listenerUuids);
            }

            players.add(mp);
        }

        getInstance().getLogger().info("已从文件加载 " + players.size() + " 个播放器");
        return players;
    }
}
