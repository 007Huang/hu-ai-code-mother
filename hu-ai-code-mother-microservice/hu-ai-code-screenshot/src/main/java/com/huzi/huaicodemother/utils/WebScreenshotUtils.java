package com.huzi.huaicodemother.utils;

import cn.hutool.core.img.ImgUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.huzi.huaicodemother.exception.BusinessException;
import com.huzi.huaicodemother.exception.ErrorCode;
import io.github.bonigarcia.wdm.WebDriverManager;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class WebScreenshotUtils {

    private static final WebDriver webDriver;
    //全局金泰初始化，避免重复初始化驱动程序
    static {
        final int DEFAULT_WIDTH = 1600;
        final int DEFAULT_HEIGHT = 900;
        webDriver = initChromeDriver(DEFAULT_WIDTH, DEFAULT_HEIGHT);

        // 注册 JVM 关闭钩子
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("JVM 正在关闭，准备清理 Chrome 进程...");
            if (webDriver != null) {
                try {
                    webDriver.quit(); // 这样才能真正在应用停止时杀死 Chrome 进程
                    log.info("Chrome 进程清理完毕");
                } catch (Exception e) {
                    log.error("清理 Chrome 进程失败", e);
                }
            }
        }));

    }

    @PreDestroy
    public void destroy() {

        webDriver.quit();
    }

    /**
     * 生成网页截图
     *
     * @param webUrl 网页URL
     * @return 压缩后的截图文件路径，失败返回null
     */
    public static String saveWebPageScreenshot(String webUrl) {
        if (StrUtil.isBlank(webUrl)) {
            log.error("网页URL不能为空");
            return null;
        }
        try {
            // 创建临时目录
            String rootPath = System.getProperty("user.dir") + File.separator + "tmp" + File.separator + "screenshots"
                    + File.separator + UUID.randomUUID().toString().substring(0, 8);
            FileUtil.mkdir(rootPath);
            // 图片后缀
            final String IMAGE_SUFFIX = ".png";
            // 原始截图文件路径
            String imageSavePath = rootPath + File.separator + RandomUtil.randomNumbers(5) + IMAGE_SUFFIX;
            // 访问网页
            webDriver.get(webUrl);
            // 等待页面加载完成
            waitForPageLoad(webDriver);
            // 截图
            byte[] screenshotBytes = ((TakesScreenshot) webDriver).getScreenshotAs(OutputType.BYTES);
            // 保存原始图片
            saveImage(screenshotBytes, imageSavePath);
            log.info("原始截图保存成功: {}", imageSavePath);
            // 压缩图片
            final String COMPRESSION_SUFFIX = "_compressed.jpg";
            String compressedImagePath = rootPath + File.separator + RandomUtil.randomNumbers(5) + COMPRESSION_SUFFIX;
            compressImage(imageSavePath, compressedImagePath);
            log.info("压缩图片保存成功: {}", compressedImagePath);
            // 删除原始图片，只保留压缩图片
            FileUtil.del(imageSavePath);
            return compressedImagePath;
        } catch (Exception e) {
            log.error("网页截图失败: {}", webUrl, e);
            return null;
        }
    }

    /**
     * 初始化 Chrome 浏览器驱动
     */
    private static WebDriver initChromeDriver(int width, int height) {
        try {
            // 优先复用本地缓存驱动（主版本匹配即可，不联网）；未命中才尝试在线下载
            boolean useLocalDriver = resolveLocalChromeDriver();
            if (!useLocalDriver) {
                // 无本地驱动：尝试由 WebDriverManager 自动下载（网络可达时）
                // 注意：WDM 6.x 对 chromedriver 115+ 的下载地址来自 Google 官方 JSON，无法走国内镜像，
                // 若下载失败，请手动下载 chromedriver 放到 ~/.cache/selenium/chromedriver 或项目 driver 目录
                System.setProperty("wdm.timeout", "60");
                System.setProperty("wdm.retryCount", "1");
                WebDriverManager.chromedriver().setup();
            }
            // 配置 Chrome 选项
            ChromeOptions options = new ChromeOptions();
//            // 无头模式
//            options.addArguments("--headless");
            // 无头模式（新版 Chrome 兼容写法）
            options.addArguments("--headless=new");
            // 禁用GPU（在某些环境下避免问题）
            options.addArguments("--disable-gpu");
            // 禁用沙盒模式（Docker环境需要）
            options.addArguments("--no-sandbox");
            // 禁用开发者shm使用
            options.addArguments("--disable-dev-shm-usage");
            // 设置窗口大小
            options.addArguments(String.format("--window-size=%d,%d", width, height));
            // 禁用扩展
            options.addArguments("--disable-extensions");
            // 设置用户代理
            options.addArguments("--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");
            // 创建驱动
            WebDriver driver = new ChromeDriver(options);
            // 设置页面加载超时
            driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(30));
            // 设置隐式等待
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
            return driver;
        } catch (Exception e) {
            log.error("初始化 Chrome 浏览器失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "初始化 Chrome 浏览器失败");
        }
    }

    /**
     * 解析本地 ChromeDriver，避免联网下载：
     * 1. 已通过 webdriver.chrome.driver 指定且文件存在 → 直接使用；
     * 2. 扫描 WebDriverManager 缓存目录（~/.cache/selenium/chromedriver）及项目 driver 目录，
     *    复用与本机 Chrome 主版本一致的驱动（小版本差异不影响使用）；
     * 3. 均未命中 → 返回 false，由调用方尝试在线下载。
     *
     * @return true 表示已找到本地驱动（已设置 webdriver.chrome.driver），false 表示未命中
     */
    private static boolean resolveLocalChromeDriver() {
        // 1. 已配置且存在，直接使用
        String configured = System.getProperty("webdriver.chrome.driver");
        if (StrUtil.isNotBlank(configured) && FileUtil.exist(configured)) {
            log.info("使用已配置的 ChromeDriver: {}", configured);
            return true;
        }
        // 2. 从候选目录中查找主版本匹配的驱动
        String chromeMajor = detectChromeMajorVersion();
        List<File> searchRoots = new ArrayList<>();
        // WebDriverManager 默认缓存目录：~/.cache/selenium/chromedriver/{platform}/{version}/chromedriver.exe
        searchRoots.add(new File(System.getProperty("user.home") + File.separator + ".cache" + File.separator
                + "selenium" + File.separator + "chromedriver"));
        // 项目目录下的 driver 目录（可手动放置 chromedriver.exe，结构同缓存目录）
        searchRoots.add(new File(System.getProperty("user.dir") + File.separator + "driver"));
        for (File cacheDir : searchRoots) {
            if (!cacheDir.isDirectory()) {
                continue;
            }
            File[] platformDirs = cacheDir.listFiles(File::isDirectory);
            if (platformDirs == null) {
                continue;
            }
            for (File platformDir : platformDirs) {
                File[] versionDirs = platformDir.listFiles(File::isDirectory);
                if (versionDirs == null) {
                    continue;
                }
                for (File versionDir : versionDirs) {
                    // 主版本一致即可使用（如 Chrome 151.x 可用 chromedriver 151.0.7922.76）
                    if (chromeMajor != null && versionDir.getName().startsWith(chromeMajor + ".")) {
                        File driverFile = new File(versionDir, "chromedriver.exe");
                        if (driverFile.isFile()) {
                            System.setProperty("webdriver.chrome.driver", driverFile.getAbsolutePath());
                            log.info("复用本地 ChromeDriver: {} (匹配 Chrome 主版本 {})",
                                    driverFile.getAbsolutePath(), chromeMajor);
                            return true;
                        }
                    }
                }
            }
        }
        // 3. 未命中本地驱动
        log.warn("未找到主版本匹配的本地 ChromeDriver（Chrome 主版本: {}），将尝试在线下载", chromeMajor);
        return false;
    }

    /**
     * 读取本机 Chrome 主版本号（Windows 注册表，如 151）
     */
    private static String detectChromeMajorVersion() {
        String[] regPaths = {
                "HKEY_CURRENT_USER\\Software\\Google\\Chrome\\BLBeacon",
                "HKEY_LOCAL_MACHINE\\SOFTWARE\\Google\\Chrome\\BLBeacon"
        };
        for (String regPath : regPaths) {
            try {
                Process process = new ProcessBuilder("reg", "query", regPath, "/v", "version")
                        .redirectErrorStream(true).start();
                String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                Matcher matcher = Pattern.compile("(\\d+)\\.\\d+\\.\\d+\\.\\d+").matcher(output);
                if (matcher.find()) {
                    return matcher.group(1);
                }
            } catch (Exception e) {
                log.warn("读取 Chrome 版本失败: {} - {}", regPath, e.getMessage());
            }
        }
        return null;
    }

    /**
     * 保存图片到文件
     */
    private static void saveImage(byte[] imageBytes, String imagePath) {
        try {
            FileUtil.writeBytes(imageBytes, imagePath);
        } catch (Exception e) {
            log.error("保存图片失败: {}", imagePath, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "保存图片失败");
        }
    }

    /**
     * 压缩图片
     */
    private static void compressImage(String originalImagePath, String compressedImagePath) {
        // 压缩图片质量（0.1 = 10% 质量）
        final float COMPRESSION_QUALITY = 0.3f;
        try {
            ImgUtil.compress(
                    FileUtil.file(originalImagePath),
                    FileUtil.file(compressedImagePath),
                    COMPRESSION_QUALITY
            );
        } catch (Exception e) {
            log.error("压缩图片失败: {} -> {}", originalImagePath, compressedImagePath, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "压缩图片失败");
        }
    }

    /**
     * 等待页面加载完成
     */
    private static void waitForPageLoad(WebDriver driver) {
        try {
            // 创建等待页面加载对象
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            // 等待 document.readyState 为complete
            wait.until(webDriver ->
                    ((JavascriptExecutor) webDriver).executeScript("return document.readyState")
                            .equals("complete")
            );
            // 额外等待一段时间，确保动态内容加载完成
            Thread.sleep(2000);
            log.info("页面加载完成");
        } catch (Exception e) {
            log.error("等待页面加载时出现异常，继续执行截图", e);
        }
    }


}
