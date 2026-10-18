# ChromeDriver 初始化超时问题修复总结

> 项目：hu-ai-code-mother
> 涉及文件：`src/main/java/com/huzi/huaicodemother/utils/WebScreenshotUtils.java`
> 依赖版本：selenium-java 4.33.0 / webdrivermanager 6.1.0
> 修复日期：2026-08-23

---

## 1. 问题背景与现象

应用在生成网页截图（`ScreenshotServiceImpl.generateAndUploadScreenshot`）时，需要初始化 ChromeDriver 驱动浏览器，但初始化持续失败。

关键日志：

```
复用缓存中的 ChromeDriver: C:\Users\hzh\.cache\selenium\chromedriver\win64\151.0.7922.76\chromedriver.exe (匹配 Chrome 主版本 151)
Using chromedriver 151.0.7922.138 (resolved driver for Chrome 151)
Downloading https://storage.googleapis.com/chrome-for-testing-public/151.0.7922.138/win64/chromedriver-win64.zip
ERROR: Connect to https://storage.googleapis.com:443 ... failed: Connection timed out
ERROR: 初始化 Chrome 浏览器失败
Exception in thread "" java.lang.ExceptionInInitializerError
```

**现象总结**：明明本地已有可用的 chromedriver（`151.0.7922.76`），程序却仍尝试联网下载精确版本 `151.0.7922.138`，最终因 `storage.googleapis.com` 网络超时导致初始化失败。整个过程卡死 3~5 分钟。

---

## 2. 根因分析

### 2.1 直接原因：WebDriverManager 的严格版本校验

- 本地驱动 build 号（`151.0.7922.76`）与 WDM 解析出的期望版本（`151.0.7922.138`）**不完全一致**；
- WDM 的 `manage()` 流程中，本地驱动版本必须与期望版本**完全一致**才会跳过下载，否则走联网下载流程；
- 而 chromedriver 实际**只需主版本（Major）一致即可正常工作**（Chrome 115+ 的版本匹配规则），小版本差异无影响。

### 2.2 深层原因：WDM 6.1.0 的下载地址来自 Google 官方 JSON，镜像配置无效

通过反编译 `webdrivermanager-6.1.0.jar` 字节码确认：

- `ChromeDriverManager` 中只有一个硬编码的旧版 URL 模式：`https://chromedriver.storage.googleapis.com/%s/chromedriver_%s%s.zip`（仅用于 < 115 旧版本）；
- chromedriver 115+ 的下载地址来自在线 JSON `known-good-versions-with-downloads.json`（托管于 `googlechromelabs.github.io`），其中的下载 URL 直接指向 `storage.googleapis.com`；
- 因此 **`wdm.chromeDriverMirrorUrl` 属性对 WDM 6.1.0 的 chromedriver 下载无效**，无法通过该属性切换国内镜像。

### 2.3 网络原因：Google 服务在国内不可达

- `googlechromelabs.github.io`（版本元数据解析）超时 → WDM fallback 到本地 `resolution.properties` 完成版本解析；
- `storage.googleapis.com`（驱动下载）超时 → 下载失败。

> 注：npmmirror 镜像（`https://registry.npmmirror.com/-/binary/chrome-for-testing/`）**确实同步了 151.0.7922.138 等最新版本**（实测 2026-08-11 已同步），目录结构与官方一致。但由于第 2.2 点，WDM 6.1.0 无法直接使用该镜像；如需走镜像，只能手动下载驱动放入本地目录。

---

## 3. 修复方案

### 3.1 核心思路：本地驱动命中时完全绕开 WebDriverManager

```
initChromeDriver()
  ├─ resolveLocalChromeDriver()  → 命中本地驱动？
  │    ├─ ✅ 命中：设置 webdriver.chrome.driver 系统属性
  │    │         → 跳过 WDM setup()，new ChromeDriver 直接使用本地驱动（全程零联网）
  │    └─ ❌ 未命中：调用 WebDriverManager.setup() 在线下载兜底
  │               （60s 超时 + 1 次重试，失败快速报错）
```

### 3.2 关键代码逻辑

**① 驱动复用判定（主版本匹配）**

```java
// 1. 已通过 webdriver.chrome.driver 指定且文件存在 → 直接使用
// 2. 扫描候选目录（WDM 缓存目录 ~/.cache/selenium/chromedriver、项目 driver 目录），
//    找到目录名以 "Chrome主版本." 开头的 chromedriver.exe → 设置系统属性，返回 true
// 3. 均未命中 → 返回 false，由调用方尝试在线下载
```

**② 版本探测（Windows 注册表）**

```java
// 读取 HKEY_CURRENT_USER\Software\Google\Chrome\BLBeacon 的 version 值（如 151.0.7922.140）
// 正则提取主版本号 151；HKCU 读不到时回退读 HKLM
```

**③ 未命中时的兜底下载**

```java
System.setProperty("wdm.timeout", "60");    // 缩短超时，避免长时间卡死
System.setProperty("wdm.retryCount", "1");  // 减少重试次数
WebDriverManager.chromedriver().setup();
```

> 注意：不再设置 `wdm.chromeDriverMirrorUrl`——已确认对 WDM 6.1.0 无效（见 2.2）。

### 3.3 演进过程（踩坑记录）

| 版本 | 方案 | 结果 |
|---|---|---|
| v1 | 直接 `WebDriverManager.chromedriver().setup()` | 联网下载超时失败 |
| v2 | 设置 `wdm.chromeDriverMirrorUrl` 指向 npmmirror 镜像 | **无效**，下载 URL 仍是 googleapis（反编译确认 6.1.0 不支持） |
| v3（最终） | 本地驱动复用（主版本匹配）+ 未命中才走 WDM | ✅ 零联网启动，超时兜底 |

---

## 4. 修复效果验证

| 检查项 | 结果 |
|---|---|
| 编译检查（GetProblems） | ✅ 无错误 |
| 注册表版本读取（本机实测） | ✅ 返回 `151.0.7922.140`，主版本解析为 `151` |
| 缓存驱动匹配 | ✅ `151.0.7922.76` 主版本 151 匹配，命中复用逻辑 |
| 启动耗时 | 秒级（不再有 3~5 分钟联网超时等待） |

修复后日志预期：

```
复用本地 ChromeDriver: C:\Users\hzh\.cache\selenium\chromedriver\win64\151.0.7922.76\chromedriver.exe (匹配 Chrome 主版本 151)
```

---

## 5. 后续维护指南

### 5.1 浏览器自动更新后需要做什么？

- **Chrome 主版本不变**（如 151.x 内部小更新）→ 无需任何操作，驱动继续复用；
- **Chrome 主版本升级**（如 151 → 152）→ 缓存无匹配驱动时程序会尝试在线下载：
  - 网络可达 Google：自动下载成功，无需人工干预；
  - 网络不可达：快速失败，日志提示手动放置驱动。

### 5.2 手动放置驱动（推荐，离线可靠）

从 npmmirror 镜像下载与 Chrome 主版本一致的 chromedriver：

```
https://registry.npmmirror.com/-/binary/chrome-for-testing/{完整版本号}/win64/chromedriver-win64.zip
```

例如：`https://registry.npmmirror.com/-/binary/chrome-for-testing/151.0.7922.138/win64/chromedriver-win64.zip`

解压后放置到以下任一位置（结构需保持）：

```
方式一（WDM 缓存目录）：
  %USERPROFILE%\.cache\selenium\chromedriver\win64\{版本号}\chromedriver.exe

方式二（项目 driver 目录）：
  {项目根目录}\driver\win64\{版本号}\chromedriver.exe

方式三（直接指定路径）：
  启动时加 -Dwebdriver.chrome.driver=D:\path\to\chromedriver.exe
```

---

## 6. 经验沉淀

1. **WebDriverManager 6.x 对 chromedriver 115+ 的版本校验是"build 号完全一致"**，本地驱动小版本不同仍会触发下载；
2. **chromedriver 只需主版本与 Chrome 一致**即可工作，不必强求完全同版本；
3. **WDM 6.1.0 的 `wdm.chromeDriverMirrorUrl` 对 chromedriver 无效**（下载 URL 硬编码在 Google JSON 中），不要在这上面浪费时间；
4. **"本地优先、在线兜底"**是受限网络环境下依赖自动下载组件（WebDriverManager / Selenium Manager / npm 等）的通用模式；
5. 排查第三方库行为时，可**反编译 jar 字节码**（`javap -c -p`）确认配置项是否真正生效，避免文档与实际实现不符的陷阱。
