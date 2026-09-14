package com.huzi.huaicodemother.core.builder;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Vue 项目构建器。
 */
@Slf4j
@Component
public class VueProjectBuilder {

    private static final String PACKAGE_FILE = "package.json";
    private static final String LOCK_FILE = "package-lock.json";
    private static final String NODE_MODULES_DIR = "node_modules";
    private static final String DEPENDENCY_FINGERPRINT_FILE = ".hu-ai-dependencies.sha256";
    private static final int INSTALL_TIMEOUT_SECONDS = 300;
    private static final int BUILD_TIMEOUT_SECONDS = 180;

    /**
     * 构建 Vue 项目。
     *
     * @param projectPath 项目根目录路径
     * @return 是否构建成功
     */
    public boolean buildProject(String projectPath) {
        File projectDir = new File(projectPath);
        if (!projectDir.isDirectory()) {
            log.error("项目目录不存在: {}", projectPath);
            return false;
        }

        File packageJson = new File(projectDir, PACKAGE_FILE);
        if (!packageJson.isFile()) {
            log.error("package.json 文件不存在: {}", packageJson.getAbsolutePath());
            return false;
        }

        log.info("开始构建 Vue 项目: {}", projectPath);
        String dependencyFingerprint;
        try {
            dependencyFingerprint = calculateDependencyFingerprint(projectDir.toPath());
        } catch (Exception e) {
            log.error("计算依赖指纹失败: {}", e.getMessage(), e);
            return false;
        }

        if (!dependenciesAreReady(projectDir.toPath(), dependencyFingerprint)) {
            if (!installDependencies(projectDir, dependencyFingerprint)) {
                return false;
            }
        } else {
            log.info("依赖声明未变化，跳过 npm install");
        }

        Path indexFile = projectDir.toPath().resolve("dist").resolve("index.html");
        if (!markExistingBuildOutput(indexFile)) {
            return false;
        }
        if (!executeCommand(projectDir, npmCommand("run", "build"), BUILD_TIMEOUT_SECONDS)) {
            log.error("npm run build 执行失败");
            return false;
        }

        if (!isFreshBuildOutput(indexFile)) {
            log.error("构建完成但未生成新的 dist/index.html: {}", indexFile.toAbsolutePath());
            return false;
        }

        log.info("Vue 项目构建成功，入口文件: {}", indexFile.toAbsolutePath());
        return true;
    }

    private boolean installDependencies(File projectDir, String dependencyFingerprint) {
        Path marker = dependencyMarker(projectDir.toPath());
        try {
            Files.deleteIfExists(marker);
        } catch (IOException e) {
            log.warn("删除旧依赖指纹失败，将继续安装: {}", e.getMessage());
        }

        log.info("依赖声明发生变化，执行 npm install...");
        if (!executeCommand(projectDir, npmCommand("install"), INSTALL_TIMEOUT_SECONDS)) {
            log.error("npm install 执行失败");
            return false;
        }

        try {
            // npm install 可能创建或更新 package-lock.json，使用安装完成后的最终声明计算指纹。
            String installedFingerprint = calculateDependencyFingerprint(projectDir.toPath());
            Files.createDirectories(marker.getParent());
            Files.writeString(marker, installedFingerprint, StandardCharsets.UTF_8);
        } catch (Exception e) {
            // 指纹只用于后续跳过安装，写入失败不应否定本次已成功安装的依赖。
            log.warn("写入依赖指纹失败，下次构建将重新安装依赖: {}", e.getMessage());
        }
        return true;
    }

    boolean dependenciesAreReady(Path projectDir, String expectedFingerprint) {
        Path nodeModules = projectDir.resolve(NODE_MODULES_DIR);
        Path marker = dependencyMarker(projectDir);
        Path viteExecutable = nodeModules.resolve(".bin").resolve(isWindows() ? "vite.cmd" : "vite");
        if (!Files.isDirectory(nodeModules)
                || !Files.isRegularFile(marker)
                || !Files.isRegularFile(viteExecutable)) {
            return false;
        }
        try {
            return expectedFingerprint.equals(Files.readString(marker, StandardCharsets.UTF_8).trim());
        } catch (IOException e) {
            log.warn("读取依赖指纹失败，将重新安装依赖: {}", e.getMessage());
            return false;
        }
    }

    String calculateDependencyFingerprint(Path projectDir) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        updateDigest(digest, projectDir.resolve(PACKAGE_FILE));
        Path lockFile = projectDir.resolve(LOCK_FILE);
        if (Files.isRegularFile(lockFile)) {
            updateDigest(digest, lockFile);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private void updateDigest(MessageDigest digest, Path file) throws IOException {
        digest.update(file.getFileName().toString().getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
        digest.update(Files.readAllBytes(file));
        digest.update((byte) 0);
    }

    private Path dependencyMarker(Path projectDir) {
        return projectDir.resolve(NODE_MODULES_DIR).resolve(DEPENDENCY_FINGERPRINT_FILE);
    }

    private boolean markExistingBuildOutput(Path indexFile) {
        if (!Files.isRegularFile(indexFile)) {
            return true;
        }
        try {
            Files.setLastModifiedTime(indexFile, FileTime.fromMillis(0));
            return true;
        } catch (IOException e) {
            log.error("标记旧构建产物失败: {}", e.getMessage(), e);
            return false;
        }
    }

    boolean isFreshBuildOutput(Path indexFile) {
        if (!Files.isRegularFile(indexFile)) {
            return false;
        }
        try {
            return Files.getLastModifiedTime(indexFile).toMillis() > 0;
        } catch (IOException e) {
            log.warn("读取构建产物时间失败: {}", e.getMessage());
            return false;
        }
    }

    private List<String> npmCommand(String... arguments) {
        String executable = isWindows() ? "npm.cmd" : "npm";
        java.util.ArrayList<String> command = new java.util.ArrayList<>();
        command.add(executable);
        command.addAll(List.of(arguments));
        return command;
    }

    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("windows");
    }

    /**
     * 执行 npm 命令。protected 便于使用无外部进程的单元测试覆盖构建流程。
     */
    protected boolean executeCommand(File workingDir, List<String> command, int timeoutSeconds) {
        Process process = null;
        try {
            log.info("在目录 {} 中执行命令: {}", workingDir.getAbsolutePath(), String.join(" ", command));
            process = new ProcessBuilder(command)
                    .directory(workingDir)
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                    .start();
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                log.error("命令执行超时（{}秒），强制终止进程树", timeoutSeconds);
                destroyProcessTree(process);
                return false;
            }
            int exitCode = process.exitValue();
            if (exitCode != 0) {
                log.error("命令执行失败，退出码: {}", exitCode);
                return false;
            }
            return true;
        } catch (Exception e) {
            log.error("执行命令失败: {}, 错误信息: {}", String.join(" ", command), e.getMessage(), e);
            if (process != null && process.isAlive()) {
                destroyProcessTree(process);
            }
            return false;
        }
    }

    private void destroyProcessTree(Process process) {
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
    }
}
