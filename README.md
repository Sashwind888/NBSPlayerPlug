# NBSPlayerPlugin
Note Block Studio 音频文件播放插件

Bukkit 1.18+

> [!TIP]
> 可以通过在客户端添加[资源包](https://github.com/OpenNBS/NoteBlockStudio/raw/refs/heads/main/datafiles/Data/extranotes.zip)来支持扩展音高

## 命令

### 普通播放模式:
```
/nbsplay play <url> - 给自己播放
/nbsplay stop - 停止自己播放
/nbsplay play <selector> <url> - 给目标播放（需权限）
/nbsplay stop <selector> - 停止目标播放（需权限）
```

### 播放器模式（需权限 nbsplay.player.control 或是播放器创建者/控制者）:

```
/nbsplay player create [name] - 创建播放器（需权限）
/nbsplay player list - 列出播放器（管理员看全部，否则只看自己管理的）
/nbsplay player remove <name/id> - 删除播放器（需权限）
/nbsplay player setperm <name/id> <control|listen> <selector> <add|del> - 设置权限
/nbsplay player <play|pause|stop|next|back> <name/id> - 控制播放
/nbsplay playlist add <name/id> <url> - 添加歌曲
/nbsplay playlist remove <name/id> <id> - 移除歌曲
/nbsplay playlist list <name/id> - 查看列表
/nbsplay playlist setmode <name/id> <single|random|sequence> - 设置模式"
```

## 使用

1. 自行构建或从release下载
2. 放入plugins文件夹
3. 开始享用喵

## 开发与构建

需要 **`java17`或`java21`**

### Windows
```
./gradlew.bat jar
```

### Linux/MacOS

```
./gradlew jar
```
