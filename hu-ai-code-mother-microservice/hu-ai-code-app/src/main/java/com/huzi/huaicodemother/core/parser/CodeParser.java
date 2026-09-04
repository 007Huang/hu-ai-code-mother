package com.huzi.huaicodemother.core.parser;

/**
 * 代码解析器策略接口.定义接口，通过泛型统一方法的返回值,动态交给子类实现
 * 
 * @author huzi
 */
public interface CodeParser<T> {

    /**
     * 解析代码内容
     * 
     * @param codeContent 原始代码内容
     * @return 解析后的结果对象
     */
    T parseCode(String codeContent);
}
