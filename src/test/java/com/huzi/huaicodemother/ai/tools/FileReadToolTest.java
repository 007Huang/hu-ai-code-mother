package com.huzi.huaicodemother.ai.tools;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class FileReadToolTest {

    @TempDir
    Path tempDir;

    private final FileReadTool fileReadTool = new FileReadTool();

    @Test
    void returnsNonBlankMessageForEmptyOrWhitespaceOnlyFile() throws Exception {
        Path emptyFile = Files.createFile(tempDir.resolve("empty.txt"));
        Path whitespaceFile = Files.writeString(tempDir.resolve("whitespace.txt"), " \n\t");

        String emptyResult = fileReadTool.readFile(emptyFile.toString(), 1L);
        String whitespaceResult = fileReadTool.readFile(whitespaceFile.toString(), 1L);

        assertFalse(emptyResult.isBlank());
        assertFalse(whitespaceResult.isBlank());
        assertEquals("文件为空或仅包含空白字符: " + emptyFile, emptyResult);
        assertEquals("文件为空或仅包含空白字符: " + whitespaceFile, whitespaceResult);
    }

    @Test
    void returnsNonBlankFileContentUnchanged() throws Exception {
        String content = "  hello\nworld  ";
        Path file = Files.writeString(tempDir.resolve("content.txt"), content);

        assertEquals(content, fileReadTool.readFile(file.toString(), 1L));
    }
}
