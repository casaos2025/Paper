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
    
    // 控制台颜色代码
    private static final String ANSI_GREEN = "\033[1;32m";
    private static final String ANSI_YELLOW = "\033[1;33m";
    private static final String ANSI_RESET = "\033[0m";

    // === 续期参数配置 (已填入你提供的值) ===
    private static final String RENEW_URL = "https://lemehost.com/server/10093617/free-plan";
    
    private static final String COOKIE_DATA = "_identity-frontend=26d0cc360d2c3ad3d806d54a18beefe3c1ef8e623bd8cb5c1106af68fbf3bb90a%3A2%3A%7Bi%3A0%3Bs%3A18%3A%22_identity-frontend%22%3Bi%3A1%3Bs%3A50%3A%22%5B53912%2C%22DRXzgR6FFBGu-1zonF5s8ZN7BGERfPEB%22%2C2592000%5D%22%3B%7D; _csrf-frontend=e8916cf944c94f3d37a4cf3d3cdb10a428ec9f0f4d88c51d7d71f42a0c778bb1a%3A2%3A%7Bi%3A0%3Bs%3A14%3A%22_csrf-frontend%22%3Bi%3A1%3Bs%3A32%3A%2238mrmE_M3NKIVu--sZerm3UhoGQdfUFR%22%3B%7D; advanced-frontend=a78hti5b3kmr7585dbuassck7e";
    
    private static final String CSRF_PAYLOAD = "yRKpzTO6iJ8sXQ8ROarNg7buZh7HyStg-ZOgYB3-Gyb6KsS_Xv_X0h8TRFhv3-CuxbQDbKr6fgiW1PEEe6tddA==";
    // ===================================

    private PaperBootstrap() {}

    public static void boot(final OptionSet options) {
        try {
            // 1. 启动后台续期扫描任务 (每 5 分钟执行一次)
            startAutoRenewTask();
            
            LOGGER.info(ANSI_GREEN + "Lemehost 自动扫描续期已挂载，服务器启动中..." + ANSI_RESET);
            
            // 2. 调用 Minecraft 主程序
            Main.main(options);
            
        } catch (Exception e) {
            LOGGER.error("Paper 引导类启动异常: " + e.getMessage());
        }
    }

    private static void startAutoRenewTask() {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        
        // 延迟 10 秒后开始，每 300 秒 (5 分钟) 执行一轮
        executor.scheduleAtFixedRate(() -> {
            try {
                // 模拟访问页面 (GET)
                sendRequest("GET", null);
                // 模拟点击按钮 (POST)
                sendRequest("POST", "_csrf=" + CSRF_PAYLOAD);
            } catch (Exception e) {
                LOGGER.error("扫描线程发生错误: " + e.getMessage());
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

            // --- 核心头部：这部分决定了续期是否成功 ---
            conn.setRequestProperty("Cookie", COOKIE_DATA);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/122.0.0.0");
            conn.setRequestProperty("Referer", RENEW_URL);
            
            // 重要：告诉服务器这是一个 AJAX 请求 (XMLHttpRequest)
            conn.setRequestProperty("X-Requested-With", "XMLHttpRequest");
            conn.setRequestProperty("Accept", "*/*");

            if ("POST".equals(method) && body != null) {
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body.getBytes(StandardCharsets.UTF_8));
                }
            }

            int code = conn.getResponseCode();
            
            // 逻辑判断
            if (code == 204) {
                LOGGER.info(ANSI_GREEN + "[LemeRenew] 续期成功！(状态码: 204 No Content)" + ANSI_RESET);
            } else if (code == 200) {
                LOGGER.info(ANSI_YELLOW + "[LemeRenew] " + method + " 扫描完成，但服务器仅返回 200 (可能未成功重置时间)" + ANSI_RESET);
            } else {
                LOGGER.warn("[LemeRenew] " + method + " 请求异常，状态码: " + code);
            }

        } catch (Exception e) {
            LOGGER.warn("[LemeRenew] 网络请求失败: " + e.getMessage());
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}
