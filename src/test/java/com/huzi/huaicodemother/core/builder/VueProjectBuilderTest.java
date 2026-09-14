package com.huzi.huaicodemother.core.builder;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VueProjectBuilderTest {

    @TempDir
    Path tempDir;

    @Test
    void returnsFalseWhenProjectOrPackageFileIsMissing() {
        FakeVueProjectBuilder builder = new FakeVueProjectBuilder();

        assertFalse(builder.buildProject(tempDir.resolve("missing").toString()));
        assertFalse(builder.buildProject(tempDir.toString()));
        assertTrue(builder.commands.isEmpty());
    }

    @Test
    void reusesDependenciesUntilPackageFilesChange() throws Exception {
        Path projectDir = createProject();
        FakeVueProjectBuilder builder = new FakeVueProjectBuilder();

        assertTrue(builder.buildProject(projectDir.toString()));
        assertEquals(List.of("install", "run build"), builder.commands);

        builder.commands.clear();
        assertTrue(builder.buildProject(projectDir.toString()));
        assertEquals(List.of("run build"), builder.commands);

        Files.writeString(projectDir.resolve("package.json"), "{\"dependencies\":{\"vue\":\"next\"}}", StandardCharsets.UTF_8);
        builder.commands.clear();
        assertTrue(builder.buildProject(projectDir.toString()));
        assertEquals(List.of("install", "run build"), builder.commands);
    }

    @Test
    void failedInstallDoesNotCreateReusableFingerprint() throws Exception {
        Path projectDir = createProject();
        FakeVueProjectBuilder builder = new FakeVueProjectBuilder();
        builder.installSucceeds = false;

        assertFalse(builder.buildProject(projectDir.toString()));

        builder.installSucceeds = true;
        builder.commands.clear();
        assertTrue(builder.buildProject(projectDir.toString()));
        assertEquals(List.of("install", "run build"), builder.commands);
    }

    @Test
    void missingViteExecutableForcesDependencyInstall() throws Exception {
        Path projectDir = createProject();
        FakeVueProjectBuilder builder = new FakeVueProjectBuilder();
        assertTrue(builder.buildProject(projectDir.toString()));

        Files.delete(projectDir.resolve("node_modules/.bin/vite"));
        Files.delete(projectDir.resolve("node_modules/.bin/vite.cmd"));
        builder.commands.clear();
        assertTrue(builder.buildProject(projectDir.toString()));

        assertEquals(List.of("install", "run build"), builder.commands);
    }

    @Test
    void failedBuildIsNotHiddenByOldDist() throws Exception {
        Path projectDir = createProject();
        Path oldIndex = projectDir.resolve("dist/index.html");
        Files.createDirectories(oldIndex.getParent());
        Files.writeString(oldIndex, "old", StandardCharsets.UTF_8);
        FakeVueProjectBuilder builder = new FakeVueProjectBuilder();
        builder.buildSucceeds = false;

        assertFalse(builder.buildProject(projectDir.toString()));
    }

    @Test
    void successfulNoOpBuildIsNotHiddenByOldIndex() throws Exception {
        Path projectDir = createProject();
        Path oldIndex = projectDir.resolve("dist/index.html");
        Files.createDirectories(oldIndex.getParent());
        Files.writeString(oldIndex, "old", StandardCharsets.UTF_8);
        FakeVueProjectBuilder builder = new FakeVueProjectBuilder();
        builder.createBuildOutput = false;

        assertFalse(builder.buildProject(projectDir.toString()));
    }

    @Test
    void successfulCommandWithoutIndexFileFails() throws Exception {
        Path projectDir = createProject();
        FakeVueProjectBuilder builder = new FakeVueProjectBuilder();
        builder.createBuildOutput = false;

        assertFalse(builder.buildProject(projectDir.toString()));
    }

    @Test
    void fingerprintIncludesOptionalLockFile() throws Exception {
        Path projectDir = createProject();
        FakeVueProjectBuilder builder = new FakeVueProjectBuilder();
        String withoutLock = builder.calculateDependencyFingerprint(projectDir);

        Files.writeString(projectDir.resolve("package-lock.json"), "{\"lockfileVersion\":3}", StandardCharsets.UTF_8);
        String withLock = builder.calculateDependencyFingerprint(projectDir);

        assertNotEquals(withoutLock, withLock);
    }

    private Path createProject() throws Exception {
        Files.writeString(tempDir.resolve("package.json"), "{\"scripts\":{\"build\":\"vite build\"}}", StandardCharsets.UTF_8);
        return tempDir;
    }

    private static class FakeVueProjectBuilder extends VueProjectBuilder {

        private final List<String> commands = new ArrayList<>();
        private boolean installSucceeds = true;
        private boolean buildSucceeds = true;
        private boolean createBuildOutput = true;

        @Override
        protected boolean executeCommand(File workingDir, List<String> command, int timeoutSeconds) {
            String operation = String.join(" ", command.subList(1, command.size()));
            commands.add(operation);
            if ("install".equals(operation)) {
                if (installSucceeds) {
                    try {
                        Path binDir = workingDir.toPath().resolve("node_modules/.bin");
                        Files.createDirectories(binDir);
                        Files.writeString(binDir.resolve("vite"), "", StandardCharsets.UTF_8);
                        Files.writeString(binDir.resolve("vite.cmd"), "", StandardCharsets.UTF_8);
                    } catch (Exception e) {
                        return false;
                    }
                }
                return installSucceeds;
            }
            if (!buildSucceeds || !createBuildOutput) {
                return buildSucceeds;
            }
            try {
                Path index = workingDir.toPath().resolve("dist/index.html");
                Files.createDirectories(index.getParent());
                Files.writeString(index, "new", StandardCharsets.UTF_8);
                return true;
            } catch (Exception e) {
                return false;
            }
        }
    }
}
