package io.papermc.paper;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import joptsimple.OptionSet;
import net.minecraft.server.Main;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PaperBootstrap {
    
    private static final Logger LOGGER = LoggerFactory.getLogger("LemeRenew");
    private static final String ANSI_GREEN = "\033[1;32m";
    private static final String ANSI_RESET = "\033[0m";

    // === 续期参数配置 ===
    private static final String RENEW_URL = "https://lemehost.com/server/10093617/free-plan";
    
    // 请确保以下值是你最新抓取的
    private static final String COOKIE_DATA = "_identity-frontend=26d0cc360d2c3ad3d806d54a18beefe3c1ef8e623bd8cb5c1106af68fbf3bb90a%3A2%3A%7Bi%3A0%3Bs%3A18%3A%22_identity-frontend%22%3Bi%3A1%3Bs%3A50%3A%22%5B53912%2C%22DRXzgR6FFBGu-1zonF5s8ZN7BGERfPEB%22%2C2592000%5D%22%3B%7D; _csrf-frontend=e8916cf944c94f3d37a4cf3d3cdb10a428ec9f0f4d88c51d7d71f42a0c778bb1a%3A2%3A%7Bi%3A0%3Bs%3A14%3A%22_csrf-frontend%22%3Bi%3A1%3Bs%3A32%3A%2238mrmE_M3NKIVu--sZerm3UhoGQdfUFR%22%3B%7D; advanced-frontend=a78hti5b3kmr7585dbuassck7e";
    private static final String CSRF_PAYLOAD = "yRKpzTO6iJ8sXQ8ROarNg7buZh7HyStg-ZOgYB3-Gyb6KsS_Xv_X0h8TRFhv3-CuxbQDbKr6fgiW1PEEe6tddA==";
    // ==================

    private PaperBootstrap() {}

    public static void boot(final OptionSet options) {
        try {
            // 1. 启动自动续期任务
            // 设定：服务器启动10秒后开始第一次续期，之后每5分钟(300秒)循环一次
            startAutoRenewTask();
            
            LOGGER.info(ANSI_GREEN + "Minecraft 服务器启动中，自动续期已挂载 (30分钟生命周期/5分钟扫描频率)..." + ANSI_RESET);
            
            // 2. 运行服务端主程序
            Main.main(options);
            
        } catch (Exception e) {
            LOGGER.error("Paper 引导类启动失败: " + e.getMessage());
        }
    }

    private static void startAutoRenewTask() {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        
        executor.scheduleAtFixedRate(() -> {
            try {
                // 每次任务包含一次 GET 页面访问和一次 POST 续期请求
                sendRequest("GET", null);
                sendRequest("POST", "_csrf=" + CSRF_PAYLOAD);
            } catch (Exception e) {
                LOGGER.error("自动续期执行过程中出现异常: " + e.getMessage());
            }
        }, 10, 300, TimeUnit.SECONDS); 
    }

    private static void sendRequest(String method, String body) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(RENEW_URL);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod(method);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);

            // 模拟浏览器报头，确保绕过简单的脚本检测
            conn.setRequestProperty("Cookie", COOKIE_DATA);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/122.0.0.0");
            conn.setRequestProperty("Referer", RENEW_URL);
            conn.setRequestProperty("Accept", "*/*");

            if ("POST".equals(method) && body != null) {
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body.getBytes(StandardCharsets.UTF_8));
                }
            }

            int code = conn.getResponseCode();
            // 在控制台输出扫描日志，方便你观察
            LOGGER.info("[" + method + "] 扫描续期请求已发出，服务器返回状态码: " + code);

        } catch (Exception e) {
            LOGGER.warn("网络请求失败 (请检查服务器是否离线): " + e.getMessage());
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}
