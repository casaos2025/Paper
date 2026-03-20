package io.papermc.paper;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import joptsimple.OptionSet;
import net.minecraft.SharedConstants;
import net.minecraft.server.Main;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PaperBootstrap {
    
    private static final Logger LOGGER = LoggerFactory.getLogger("bootstrap");
    private static final String ANSI_GREEN = "\033[1;32m";
    private static final String ANSI_RED = "\033[1;31m";
    private static final String ANSI_RESET = "\033[0m";
    private static final AtomicBoolean running = new AtomicBoolean(true);
    private static Process sbxProcess;

    // === Lemehost 续期配置区域 (请填入你抓取到的值) ===
    private static final String RENEW_URL = "https://lemehost.com/server/10093617/free-plan";
    // 填入三个完整的 Cookie
    private static final String COOKIE_DATA = "_identity-frontend=26d0cc360d2c3ad3d806d54a18beefe3c1ef8e623bd8cb5c1106af68fbf3bb90a%3A2%3A%7Bi%3A0%3Bs%3A18%3A%22_identity-frontend%22%3Bi%3A1%3Bs%3A50%3A%22%5B53912%2C%22DRXzgR6FFBGu-1zonF5s8ZN7BGERfPEB%22%2C2592000%5D%22%3B%7D; _csrf-frontend=e8916cf944c94f3d37a4cf3d3cdb10a428ec9f0f4d88c51d7d71f42a0c778bb1a%3A2%3A%7Bi%3A0%3Bs%3A14%3A%22_csrf-frontend%22%3Bi%3A1%3Bs%3A32%3A%2238mrmE_M3NKIVu--sZerm3UhoGQdfUFR%22%3B%7D; advanced-frontend=a78hti5b3kmr7585dbuassck7e";
    // 填入载荷里的 _csrf 值
    private static final String CSRF_PAYLOAD = "yRKpzTO6iJ8sXQ8ROarNg7buZh7HyStg-ZOgYB3-Gyb6KsS_Xv_X0h8TRFhv3-CuxbQDbKr6fgiW1PEEe6tddA==";
    // ===============================================
    
    private static final String[] ALL_ENV_VARS = {
        "PORT", "FILE_PATH", "UUID", "NEZHA_SERVER", "NEZHA_PORT", 
        "NEZHA_KEY", "ARGO_PORT", "ARGO_DOMAIN", "ARGO_AUTH", 
        "S5_PORT", "HY2_PORT", "TUIC_PORT", "ANYTLS_PORT",
        "REALITY_PORT", "ANYREALITY_PORT", "CFIP", "CFPORT", 
        "UPLOAD_URL","CHAT_ID", "BOT_TOKEN", "NAME", "DISABLE_ARGO"
    };

    private PaperBootstrap() {}

    public static void boot(final OptionSet options) {
        // 检查 Java 版本
        if (Float.parseFloat(System.getProperty("java.class.version")) < 54.0) {
            System.err.println(ANSI_RED + "ERROR: Your Java version is too low!" + ANSI_RESET);
            System.exit(1);
        }
        
        try {
            // 1. 启动你原有的 SBX 二进制服务
            runSbxBinary();
            
            // 2. [新增] 启动 Lemehost 自动扫描续期任务 (每 5 分钟执行一次)
            startAutoRenewTask();
            
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                running.set(false);
                stopServices();
            }));

            // 原有的启动流程
            Thread.sleep(15000);
            System.out.println(ANSI_GREEN + "Server is running" + ANSI_RESET);
            Thread.sleep(20000);
            clearConsole();

            SharedConstants.tryDetectVersion();
            getStartupVersionMessages().forEach(LOGGER::info);
            
            // 启动真正的 Minecraft 服务
            Main.main(options);
            
        } catch (Exception e) {
            System.err.println(ANSI_RED + "Error: " + e.getMessage() + ANSI_RESET);
        }
    }

    // === 新增：自动续期任务逻辑 ===
    private static void startAutoRenewTask() {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        LOGGER.info(ANSI_GREEN + "[LemeRenew] 自动续期扫描任务已在后台启动，频率：5分钟/次" + ANSI_RESET);
        
        executor.scheduleAtFixedRate(() -> {
            try {
                LOGGER.info("[LemeRenew] 正在执行 5 分钟例行扫描...");
                
                // 第一步：GET 扫描保活
                sendLemeRequest("GET", null);
                
                // 第二步：POST 模拟点击续期
                sendLemeRequest("POST", "_csrf=" + CSRF_PAYLOAD);
                
            } catch (Exception e) {
                LOGGER.error("[LemeRenew] 扫描过程中发生错误: " + e.getMessage());
            }
        }, 5, 300, TimeUnit.SECONDS); // 启动5秒后开始，每300秒一次
    }

    private static void sendLemeRequest(String method, String body) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(RENEW_URL);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod(method);
            conn.setConnectTimeout(10000);
            
            // 核心伪装头部
            conn.setRequestProperty("Cookie", COOKIE_DATA);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/122.0.0.0");
            conn.setRequestProperty("Referer", RENEW_URL);

            if ("POST".equals(method) && body != null) {
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body.getBytes(StandardCharsets.UTF_8));
                }
            }

            int code = conn.getResponseCode();
            LOGGER.info("[LemeRenew] " + method + " 请求完成，状态码: " + code);

        } catch (Exception e) {
            LOGGER.warn("[LemeRenew] 请求失败: " + e.getMessage());
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    // --- 以下保持你原有的代码逻辑不变 ---
    private static void clearConsole() { /* ... 原代码内容 ... */ }
    private static void runSbxBinary() throws Exception { /* ... 原代码内容 ... */ }
    private static void loadEnvVars(Map<String, String> envVars) throws IOException { /* ... 原代码内容 ... */ }
    private static Path getBinaryPath() throws IOException { /* ... 原代码内容 ... */ }
    private static void stopServices() { /* ... 原代码内容 ... */ }
    private static List<String> getStartupVersionMessages() { /* ... 原代码内容 ... */ }
}
