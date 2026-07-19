package com.huzi.huaicodemother.core.saver;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.huzi.huaicodemother.exception.BusinessException;
import com.huzi.huaicodemother.exception.ErrorCode;
import com.huzi.huaicodemother.model.enums.CodeGenTypeEnum;

import java.io.File;
import java.nio.charset.StandardCharsets;

/**
 * 抽象代码文件保存器 - 模板方法模式..在这个抽象父类中，定义了生成和保存代码的核心骨架流程。它把那些所有子类都通用的步骤（如校验、建目录、写文件）固化下来，而把那些因业务而异的步骤（如获取类型、具体保存逻辑）延迟到子类去实现。提高代码的可维护性
 *
 * @author huzi
 */
public abstract class CodeFileSaverTemplate<T> {

    // 文件保存根目录
    protected static final String FILE_SAVE_ROOT_DIR = System.getProperty("user.dir") + "/tmp/code_output";

    /**
     * 模板方法：保存代码的标准流程
     *
     * @param result 代码结果对象
     * @return 保存的目录
     */
    public final File saveCode(T result) {
        // 1. 验证输入
        validateInput(result);
        // 2. 构建唯一目录
        String baseDirPath = buildUniqueDir();
        // 3. 保存文件（具体实现由子类提供）
        saveFiles(result, baseDirPath);
        // 4. 返回目录文件对象
        return new File(baseDirPath);
    }

    /**
     * 验证输入参数（可由子类覆盖）  protected 的巧妙之处：它允许子类在必要时也能调用这个校验方法，甚至在某些特殊场景下，子类可以重写（Override）这个方法来放宽或收紧校验规则。
     *
     * @param result 代码结果对象
     */
    protected void validateInput(T result) {
        if (result == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "代码结果对象不能为空");
        }
    }

    /**
     * 构建唯一目录路径..final 关键字的作用是禁止子类重写，这保证了核心流程（如防爬虫的雪花算法命名、UTF-8编码等）在任何子类中都不会被意外篡改。
     *
     * @return 目录路径
     */
    protected final String buildUniqueDir() {
        String codeType = getCodeType().getValue();
        String uniqueDirName = StrUtil.format("{}_{}", codeType, IdUtil.getSnowflakeNextIdStr());
        String dirPath = FILE_SAVE_ROOT_DIR + File.separator + uniqueDirName;
        FileUtil.mkdir(dirPath);
        return dirPath;
    }

    /**
     * 写入单个文件的工具方法。使用了 File.separator 作为路径分隔符，而不是硬编码的 / 或 \。使用 File.separator 确保了这段代码无论是在开发者的 Windows 电脑上，还是在部署的 Linux 服务器上，都能正确解析文件路径，避免了跨平台运行时的 FileNotFoundException
     *
     * @param dirPath  目录路径
     * @param filename 文件名
     * @param content  文件内容
     */
    protected final void writeToFile(String dirPath, String filename, String content) {
        if (StrUtil.isNotBlank(content)) {
            String filePath = dirPath + File.separator + filename;
            FileUtil.writeString(content, filePath, StandardCharsets.UTF_8);//在写入文件时，显式指定了字符集为 UTF-8
        }
    }

    /**
     * 获取代码类型（由子类实现）.protected abstract。父类不关心它们具体怎么做，只强制要求子类必须提供这两种能力的具体实现。
     *
     * @return 代码生成类型
     */
    protected abstract CodeGenTypeEnum getCodeType();

    /**
     * 保存文件的具体实现（由子类实现）
     *
     * @param result      代码结果对象
     * @param baseDirPath 基础目录路径
     */
    protected abstract void saveFiles(T result, String baseDirPath);
}
