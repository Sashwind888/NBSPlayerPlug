package sashwind.mc.plugin.nbsplayer;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

public class CommandExec implements CommandExecutor, TabCompleter {

  // 存储普通播放模式（个人播放），使用线程安全并发 Map
  private static final Map<UUID, MusicPlay> musicPlayMap = new ConcurrentHashMap<>();
  // 存储所有播放器（MusicPlayer 实例），使用线程安全并发 List
  private static final List<MusicPlayer> musicPlayerList = new CopyOnWriteArrayList<>();

  // ---------- 持久化接口（供 Nbsplayer 调用） ----------
  /** 从配置文件加载所有播放器 */
  public static synchronized void loadPlayers() {
    List<MusicPlayer> loaded = PlayerDataManager.loadAll();
    musicPlayerList.clear();
    musicPlayerList.addAll(loaded);
  }

  /** 保存所有播放器到配置文件 */
  public static synchronized void savePlayers() {
    PlayerDataManager.saveAll(new ArrayList<>(musicPlayerList));
  }

  /** 停止所有播放 */
  public static synchronized void stopAll() {
    for (MusicPlay mp : musicPlayMap.values()) {
      if (mp != null) {
        try {
          mp.stop();
        } catch (Exception ignored) {}
      }
    }
    musicPlayMap.clear();

    for (MusicPlayer mp : musicPlayerList) {
      if (mp != null) {
        try {
          mp.stop();
        } catch (Exception ignored) {}
      }
    }
  }

  // ---------- 工具方法 ----------

  /** 按名称或索引查找播放器 */
  private MusicPlayer findMusicPlayer(String nameOrId) {
    try {
      int id = Integer.parseInt(nameOrId);
      if (id >= 0 && id < musicPlayerList.size()) {
        return musicPlayerList.get(id);
      }
    } catch (NumberFormatException ignored) {}
    for (MusicPlayer mp : musicPlayerList) {
      if (mp.getName() != null && mp.getName().equalsIgnoreCase(nameOrId)) {
        return mp;
      }
    }
    return null;
  }

  private Player getPlayerFromSender(CommandSender sender) {
    if (sender instanceof Player) return (Player) sender;
    return null;
  }

  /**
   * 检查发送者是否对指定播放器有操作权限。
   * 权限层级：nbsplay.player.control 权限节点 > 创建者 > 被添加的控制者
   */
  private boolean canControl(CommandSender sender, MusicPlayer mp) {
    if (sender.hasPermission("nbsplay.player.control")) return true;
    Player p = getPlayerFromSender(sender);
    if (p != null && mp.canManage(p)) return true;
    return false;
  }

  @Override
  public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
               @NotNull String label, @NotNull String[] args) {
    if (!command.getName().equalsIgnoreCase("nbsplay") && !command.getName().equalsIgnoreCase("nbs")) {
      return false;
    }

    if (args.length == 0) {
      sendHelp(sender);
      return true;
    }

    String subCmd = args[0].toLowerCase();

    // ---------- 普通播放模式 ----------
    if (subCmd.equals("play")) {
      if (args.length == 2) {
          Player p = getPlayerFromSender(sender);
          if (p == null) {
              sender.sendMessage("§c仅玩家可用此命令");
              return true;
          }
          MusicPlay mp = musicPlayMap.get(p.getUniqueId());
          if (mp != null && mp.isPlay()) {
              mp.stop();
          }
          mp = new MusicPlay(args[1], p);
          musicPlayMap.put(p.getUniqueId(), mp);
          return true;
      } else if (args.length == 3) {
          if (!sender.hasPermission("nbsplay.play.other")) {
              sender.sendMessage("§c你没有权限给其他玩家播放");
              return true;
          }
          List<Entity> targets = Bukkit.selectEntities(sender, args[1]);
          if (targets.isEmpty()) {
              sender.sendMessage("§c未选中有效玩家");
              return true;
          }
          String url = args[2];
          int count = 0;
          for (Entity e : targets) {
              if (e instanceof Player) {
                  Player target = (Player) e;
                  MusicPlay mp = musicPlayMap.get(target.getUniqueId());
                  if (mp != null && mp.isPlay()) {
                      mp.stop();
                  }
                  mp = new MusicPlay(url, target);
                  musicPlayMap.put(target.getUniqueId(), mp);
                  count++;
              }
          }
          sender.sendMessage("§a正在为 " + count + " 位玩家播放");
          return true;
      }
    }

    if (subCmd.equals("stop")) {
      if (args.length == 1) {
        Player p = getPlayerFromSender(sender);
        if (p == null) {
          sender.sendMessage("§c仅玩家可用");
          return true;
        }
        MusicPlay mp = musicPlayMap.remove(p.getUniqueId());
        if (mp != null && mp.isPlay()) mp.stop();
        return true;
      } else if (args.length == 2) {
        if (!sender.hasPermission("nbsplay.stop.other")) {
          sender.sendMessage("§c你没有权限停止其他玩家的播放");
          return true;
        }
        List<Entity> targets = Bukkit.selectEntities(sender, args[1]);
        if (targets.isEmpty()) {
          sender.sendMessage("§c未选中有效玩家");
          return true;
        }
        int count = 0;
        for (Entity e : targets) {
          if (e instanceof Player) {
            Player target = (Player) e;
            MusicPlay mp = musicPlayMap.remove(target.getUniqueId());
            if (mp != null && mp.isPlay()) {
              mp.stop();
              count++;
            }
          }
        }
        sender.sendMessage("§a已停止 " + count + " 位玩家的播放");
        return true;
      }
    }

    // ---------- player 子命令 ----------
    if (subCmd.equals("player")) {
      if (args.length < 2) {
        sender.sendMessage("§c用法: /nbsplay player <create|list|remove|setperm|play|pause|stop|next|back>");
        return true;
      }
      String playerSub = args[1].toLowerCase();

      // ---- create: 需要 nbsplay.player.control 权限 ----
      if (playerSub.equals("create")) {
        if (!sender.hasPermission("nbsplay.player.control")) {
          sender.sendMessage("§c你没有权限创建播放器（需要 nbsplay.player.control）");
          return true;
        }
        String name = (args.length >= 3) ? args[2] : null;
        Player creator = getPlayerFromSender(sender);
        MusicPlayer mp = new MusicPlayer(name, creator);
        musicPlayerList.add(mp);
        savePlayers();
        int id = musicPlayerList.size() - 1;
        sender.sendMessage("§a已创建播放器 " + (name != null ? name : "未命名") + "，ID: " + id +
            (creator != null ? "（你已成为控制者）" : ""));
        return true;
      }

      // ---- list: 有权限看全部，否则只看自己是控制者/创建者的 ----
      if (playerSub.equals("list")) {
        if (musicPlayerList.isEmpty()) {
          sender.sendMessage("§e暂无播放器");
          return true;
        }
        boolean isAdmin = sender.hasPermission("nbsplay.player.control");
        Player viewer = getPlayerFromSender(sender);
        sender.sendMessage("§6---- 播放器列表 ----");
        int shown = 0;
        for (int i = 0; i < musicPlayerList.size(); i++) {
          MusicPlayer mp = musicPlayerList.get(i);
          if (!isAdmin && viewer != null && !mp.canManage(viewer)) {
            continue;
          }
          String name = mp.getName() != null ? mp.getName() : "未命名";
          String mode = mp.getPlayMode().name();
          int listeners = mp.getListenerCount();
          int controllers = mp.getControllerCount();
          String ownerTag = (viewer != null && mp.isCreator(viewer)) ? " [创建者]" : "";
          sender.sendMessage("§e" + i + " §7- §f" + name +
              " §7(模式: §f" + mode + "§7, 监听: §f" + listeners +
              "§7, 控制: §f" + controllers + "§7)" + ownerTag);
          shown++;
        }
        if (shown == 0) {
          sender.sendMessage("§e你没有任何播放器的控制权");
        }
        return true;
      }

      // ---- remove: 需要 nbsplay.player.control 权限 ----
      if (playerSub.equals("remove")) {
        if (!sender.hasPermission("nbsplay.player.control")) {
          sender.sendMessage("§c你没有权限删除播放器（需要 nbsplay.player.control）");
          return true;
        }
        if (args.length < 3) {
          sender.sendMessage("§c用法: /nbsplay player remove <name/id>");
          return true;
        }
        MusicPlayer mp = findMusicPlayer(args[2]);
        if (mp == null) {
          sender.sendMessage("§c未找到指定播放器");
          return true;
        }
        mp.stop();
        musicPlayerList.remove(mp);
        savePlayers();
        sender.sendMessage("§a已删除播放器");
        return true;
      }

      // ---- setperm: 需要 nbsplay.player.control 权限 或 是创建者 ----
      if (playerSub.equals("setperm")) {
        if (args.length < 6) {
          sender.sendMessage("§c用法: /nbsplay player setperm <name/id> <control|listen> <selector> <add|del>");
          return true;
        }
        MusicPlayer mp = findMusicPlayer(args[2]);
        if (mp == null) {
          sender.sendMessage("§c未找到指定播放器: " + args[2]);
          return true;
        }
        if (!canControl(sender, mp)) {
          sender.sendMessage("§c你没有此播放器的管理权限（需要 nbsplay.player.control 或是创建者）");
          return true;
        }

        String permType = args[3].toLowerCase();
        String selector = args[4];
        String action = args[5].toLowerCase();

        if (!permType.equals("control") && !permType.equals("listen")) {
          sender.sendMessage("§c权限类型必须是 control 或 listen");
          return true;
        }
        if (!action.equals("add") && !action.equals("del")) {
          sender.sendMessage("§c操作必须是 add 或 del");
          return true;
        }

        List<Entity> targets = Bukkit.selectEntities(sender, selector);
        if (targets.isEmpty()) {
          sender.sendMessage("§c未选中有效玩家");
          return true;
        }

        int count = 0;
        for (Entity e : targets) {
          if (e instanceof Player) {
            Player target = (Player) e;
            if (permType.equals("control")) {
              if (action.equals("add")) {
                mp.addController(target);
              } else {
                mp.removeController(target);
              }
            } else {
              if (action.equals("add")) {
                mp.addListener(target);
              } else {
                mp.removeListener(target);
              }
            }
            count++;
          }
        }
        sender.sendMessage("§a已为 " + count + " 位玩家" + (action.equals("add") ? "添加" : "移除") +
            (permType.equals("control") ? "控制" : "监听") + "权限");
        savePlayers();
        return true;
      }

      // ---- 播放控制：play, pause, stop, next, back ----
      if (playerSub.equals("play") || playerSub.equals("pause") || playerSub.equals("stop") ||
          playerSub.equals("next") || playerSub.equals("back")) {
        if (args.length < 3) {
          sender.sendMessage("§c请指定播放器名称或ID，例如: /nbsplay player " + playerSub + " <name/id>");
          return true;
        }
        MusicPlayer mp = findMusicPlayer(args[2]);
        if (mp == null) {
          sender.sendMessage("§c未找到指定播放器");
          return true;
        }
        if (!canControl(sender, mp)) {
          sender.sendMessage("§c你没有此播放器的操作权限");
          return true;
        }
        switch (playerSub) {
          case "play":
            mp.play();
            break;
          case "pause":
            mp.pause();
            break;
          case "stop":
            mp.stop();
            break;
          case "next":
            mp.next();
            break;
          case "back":
            mp.back();
            break;
        }
        return true;
      }
    }

    // ---------- playlist 子命令 ----------
    if (subCmd.equals("playlist")) {
      if (args.length < 2) {
        sender.sendMessage("§c用法: /nbsplay playlist <add|remove|list|setmode> <name/id> [参数]");
        return true;
      }
      String plSub = args[1].toLowerCase();
      if (args.length < 3) {
        sender.sendMessage("§c请指定播放器名称或ID");
        return true;
      }
      MusicPlayer mp = findMusicPlayer(args[2]);
      if (mp == null) {
        sender.sendMessage("§c未找到指定播放器");
        return true;
      }
      if (!canControl(sender, mp)) {
        sender.sendMessage("§c你没有此播放器的操作权限");
        return true;
      }

      switch (plSub) {
        case "add":
          if (args.length < 4) {
            sender.sendMessage("§c用法: /nbsplay playlist add <name/id> <url>");
            return true;
          }
          mp.add_Playlist(args[3]);
          savePlayers();
          sender.sendMessage("§a已添加歌曲到播放列表");
          break;
        case "remove":
          if (args.length < 4) {
            sender.sendMessage("§c用法: /nbsplay playlist remove <name/id> <id>");
            return true;
          }
          try {
            int id = Integer.parseInt(args[3]);
            mp.remove_Playlist(id);
            savePlayers();
            sender.sendMessage("§a已移除歌曲");
          } catch (NumberFormatException e) {
            sender.sendMessage("§c歌曲ID必须是数字");
          }
          break;
        case "list":
          ArrayList<String> list = mp.get_Playlist();
          if (list.isEmpty()) {
            sender.sendMessage("§e播放列表为空");
          } else {
            sender.sendMessage("§6---- 播放列表 (" + mp.getName() + ") ----");
            for (int i = 0; i < list.size(); i++) {
              sender.sendMessage("§e" + i + " §7- §f" + list.get(i));
            }
          }
          break;
        case "setmode":
          if (args.length < 4) {
            sender.sendMessage("§c用法: /nbsplay playlist setmode <name/id> <single|random|sequence>");
            return true;
          }
          String modeStr = args[3].toLowerCase();
          ModeList mode;
          switch (modeStr) {
            case "single":
              mode = ModeList.single;
              break;
            case "random":
              mode = ModeList.random;
              break;
            case "sequence":
              mode = ModeList.sequence;
              break;
            default:
              sender.sendMessage("§c无效模式，可选: single, random, sequence");
              return true;
          }
          mp.setPlayMode(mode);
          savePlayers();
          sender.sendMessage("§a已切换播放模式为 " + mode.name());
          break;
        default:
          sender.sendMessage("§c未知的播放列表子命令");
          break;
      }
      return true;
    }

    sendHelp(sender);
    return true;
  }

  private void sendHelp(CommandSender sender) {
    sender.sendMessage(
        "§6---- NBSPlayer 帮助 ----\n" +
            "§e普通播放模式:\n" +
            "  §f/nbsplay play <url> §7- 给自己播放\n" +
            "  §f/nbsplay stop §7- 停止自己播放\n" +
            "  §f/nbsplay play <selector> <url> §7- 给目标播放（需权限）\n" +
            "  §f/nbsplay stop <selector> §7- 停止目标播放（需权限）\n" +
            "§e播放器模式（需权限 §fnbsplay.player.control§e 或是播放器创建者/控制者）:\n" +
            "  §f/nbsplay player create [name] §7- 创建播放器（需权限）\n" +
            "  §f/nbsplay player list §7- 列出播放器（管理员看全部，否则只看自己管理的）\n" +
            "  §f/nbsplay player remove <name/id> §7- 删除播放器（需权限）\n" +
            "  §f/nbsplay player setperm <name/id> <control|listen> <selector> <add|del> §7- 设置权限\n" +
            "  §f/nbsplay player <play|pause|stop|next|back> <name/id> §7- 控制播放\n" +
            "  §f/nbsplay playlist add <name/id> <url> §7- 添加歌曲\n" +
            "  §f/nbsplay playlist remove <name/id> <id> §7- 移除歌曲\n" +
            "  §f/nbsplay playlist list <name/id> §7- 查看列表\n" +
            "  §f/nbsplay playlist setmode <name/id> <single|random|sequence> §7- 设置模式"
    );
  }

  // ---------- Tab 补全 ----------
  @Override
  public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                    @NotNull String alias, @NotNull String[] args) {
    List<String> suggestions = new ArrayList<>();

    if (args.length == 1) {
      suggestions.add("play");
      suggestions.add("stop");
      suggestions.add("player");
      suggestions.add("playlist");
      return filter(suggestions, args[0]);
    }

    String sub = args[0].toLowerCase();
    if (sub.equals("play")) {
      if (args.length == 2) {
        for (Player p : Bukkit.getOnlinePlayers()) {
          suggestions.add(p.getName());
        }
        suggestions.add("@a");
        suggestions.add("@p");
        suggestions.add("@r");
        return filter(suggestions, args[1]);
      }
      return Collections.emptyList();
    }
    if (sub.equals("stop")) {
      if (args.length == 2) {
        for (Player p : Bukkit.getOnlinePlayers()) {
          suggestions.add(p.getName());
        }
        suggestions.add("@a");
        suggestions.add("@p");
        suggestions.add("@r");
        return filter(suggestions, args[1]);
      }
      return Collections.emptyList();
    }
    if (sub.equals("player")) {
      if (args.length == 2) {
        suggestions.add("create");
        suggestions.add("list");
        suggestions.add("remove");
        suggestions.add("setperm");
        suggestions.add("play");
        suggestions.add("pause");
        suggestions.add("stop");
        suggestions.add("next");
        suggestions.add("back");
        return filter(suggestions, args[1]);
      }
      String playerSub = args[1].toLowerCase();
      if (playerSub.equals("remove") || playerSub.equals("setperm") ||
          playerSub.equals("play") || playerSub.equals("pause") ||
          playerSub.equals("stop") || playerSub.equals("next") || playerSub.equals("back")) {
        if (args.length == 3) {
          for (int i = 0; i < musicPlayerList.size(); i++) {
            MusicPlayer mp = musicPlayerList.get(i);
            suggestions.add(String.valueOf(i));
            if (mp.getName() != null) suggestions.add(mp.getName());
          }
          return filter(suggestions, args[2]);
        }
        if (playerSub.equals("setperm") && args.length >= 3) {
          if (args.length == 4) {
            suggestions.add("control");
            suggestions.add("listen");
            return filter(suggestions, args[3]);
          }
          if (args.length == 5) {
            for (Player p : Bukkit.getOnlinePlayers()) {
              suggestions.add(p.getName());
            }
            suggestions.add("@a");
            suggestions.add("@p");
            suggestions.add("@r");
            return filter(suggestions, args[4]);
          }
          if (args.length == 6) {
            suggestions.add("add");
            suggestions.add("del");
            return filter(suggestions, args[5]);
          }
        }
      }
      if (playerSub.equals("create") && args.length == 3) {
        suggestions.add("我的播放器");
        return Collections.emptyList();
      }
    }
    if (sub.equals("playlist")) {
      if (args.length == 2) {
        suggestions.add("add");
        suggestions.add("remove");
        suggestions.add("list");
        suggestions.add("setmode");
        return filter(suggestions, args[1]);
      }
      if (args.length == 3) {
        for (int i = 0; i < musicPlayerList.size(); i++) {
          MusicPlayer mp = musicPlayerList.get(i);
          suggestions.add(String.valueOf(i));
          if (mp.getName() != null) suggestions.add(mp.getName());
        }
        return filter(suggestions, args[2]);
      }
      String plSub = args[1].toLowerCase();
      if ((plSub.equals("add") || plSub.equals("remove") || plSub.equals("setmode")) && args.length == 4) {
        if (plSub.equals("setmode")) {
          suggestions.add("single");
          suggestions.add("random");
          suggestions.add("sequence");
          return filter(suggestions, args[3]);
        }
        return Collections.emptyList();
      }
    }
    return Collections.emptyList();
  }

  private List<String> filter(List<String> list, String prefix) {
    if (prefix == null || prefix.isEmpty()) return list;
    String lower = prefix.toLowerCase();
    return list.stream().filter(s -> s.toLowerCase().startsWith(lower)).collect(Collectors.toList());
  }
}
