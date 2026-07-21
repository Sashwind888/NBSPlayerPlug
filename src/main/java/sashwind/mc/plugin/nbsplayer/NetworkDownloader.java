package sashwind.mc.plugin.nbsplayer;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

import static sashwind.mc.plugin.nbsplayer.Nbsplayer.getInstance;

public class NetworkDownloader {

    private final File cacheDir;
    private final Logger logger;

    public NetworkDownloader() {
        this.cacheDir = new File(getInstance().getDataFolder(), "cache");
        if (!this.cacheDir.exists()) {
            this.cacheDir.mkdirs();
        }
        this.logger = getInstance().getLogger();
    }

    /**
     * 异步下载文件到缓存目录（基于内容哈希命名，无后缀）
     * @param fileUrl  下载地址
     * @return CompletableFuture<File> 缓存的文件对象（纯哈希文件名）
     */
    public CompletableFuture<File> downloadFileAsync(String fileUrl) {
    return CompletableFuture.supplyAsync(() -> {
        try {
            return downloadFile(fileUrl);
        } catch (Exception e) {
            logger.log(Level.WARNING, "文件下载失败: " + e.getMessage(), e);
            return null;
        }
    });
}

    /**
     * 纯内存下载：读取全部内容，计算 SHA-256 哈希，用哈希作为文件名（无后缀）
     */
    private File downloadFile(String fileUrl) throws Exception {
        URL url = URI.create(fileUrl).toURL();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(30000);
        connection.setRequestProperty("User-Agent", "Mozilla/5.0");

        int responseCode = connection.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new IOException("下载失败，HTTP状态码: " + responseCode);
        }

        // 边读取边计算哈希，同时暂存到内存
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int bytesRead;

        try (InputStream in = connection.getInputStream()) {
            while ((bytesRead = in.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
                baos.write(buffer, 0, bytesRead);
            }
        }

        // 计算十六进制哈希字符串
        byte[] hashBytes = digest.digest();
        StringBuilder hexString = new StringBuilder();
        for (byte b : hashBytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        String hash = hexString.toString();

        String cachedFileName = hash;
        Path cachedPath = Paths.get(cacheDir.getAbsolutePath(), cachedFileName);
        File cachedFile = cachedPath.toFile();

        // 检查缓存是否已存在，否则写文件
        if (cachedFile.exists()) {
            logger.info("[NetworkDownloader] 缓存命中: " + cachedFileName);
            return cachedFile;
        } else {
            try (FileOutputStream fos = new FileOutputStream(cachedFile)) {
                baos.writeTo(fos);
            }
            logger.info("[NetworkDownloader] 新文件已缓存: " + cachedFileName);
            return cachedFile;
        }
    }
}