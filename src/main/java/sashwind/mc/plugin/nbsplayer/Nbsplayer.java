package sashwind.mc.plugin.nbsplayer;

import org.bukkit.plugin.java.JavaPlugin;

public final class Nbsplayer extends JavaPlugin {
    static Nbsplayer instance = null;

    @Override
    public void onEnable() {
        if (instance == null) {
            instance = this;
        } else {
            throw new IllegalStateException("该例为单例，禁止重复创建");
        }

        // 确保数据文件夹存在
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        // 从配置文件加载播放器
        CommandExec.loadPlayers();

        CommandExec executor = new CommandExec();

        if (getCommand("nbsplay") != null) {
            getCommand("nbsplay").setExecutor(executor);
            getCommand("nbsplay").setTabCompleter(executor);
        }

        getLogger().info("Note Block Studio 播放器完成... (Folia Supported)");
    }

    @Override
    public void onDisable() {
        // 停止所有播放及任务
        CommandExec.stopAll();
        // 关闭前保存所有播放器数据
        CommandExec.savePlayers();
        getLogger().info("Note Block Studio 播放器关闭");
    }

    public static Nbsplayer getInstance() {
        return instance;
    }
}
