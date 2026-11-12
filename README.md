# Gboard 双拼提示与无刻键帽

独立的 LSPosed 模块，作用域仅为 `com.google.android.inputmethod.latin`。无需修改、重签名或重新安装 Gboard，也不依赖 Fcitx5 Android 的源码、服务或 JNI。

## 功能

- **双拼提示**：复声母 `zh/ch/sh` 显示在键顶，韵母显示在键底，例如顶部 `zh`、底部 `ui`。单字母韵母 `a/e/o/i/u/v` 不重复标注，但保留 `uo/ui` 等映射。支持小鹤、自然码、微软、智能 ABC、紫光和拼音加加，固定跟随 Gboard 当前方案，无独立选择器；旧版本保存的手动方案会清除且不再生效。
- **键帽内排版**：根据实际键帽容器、背景边距、标签内边距和变换定位提示，排除额外触摸区域。键顶、键底提示采用默认字体和设置的 sp 字号，宽度不足时仅水平压缩，字高保持不变，并限制绘制范围。中央不绘制模块文字：匹配的字母直接放行 Gboard 的原生绘制路径，保留原字体、字号、颜色、大小写和按压状态。原生字母和分号保持 Gboard 原有字号，不再缩小。
- **动态韵母提示**：优先读取 Gboard 内部原串并独立逐键解析，不再以智能纠错后的分段为准。当前字母能与前一个孤立声母组成方案内的有效音节时，消耗为韵母并回到等待声母状态；不能组成时，当前字母成为新的孤立声母。空白、自动分隔标记不影响配对，微软方案的分号韵母及零声母音节单独处理。同键同时映射 ue/üe 时只显示 ue，单独映射的 üe 保留。原串不可读取时，以预编辑文本运行同一解析器。内部状态优先于编辑框通知；清空缓存后立即复位，旧异步快照不能覆盖复位。不改动提交文字。
- **隐藏复声母**：默认关闭。开启后，普通提示及“每字初始时只显示声母”等所有状态均隐藏 zh/ch/sh，不影响匹配韵母；关闭后恢复。
- **每字初始时只显示声母**：默认关闭。开启后，初始或等待声母状态下只显示键底 zh/ch/sh 提示，同时隐藏原生中央 U/I/V；进入韵母状态后按原有开关显示。
- **根据声母显示单韵母**：默认关闭，配合“根据声母显示可用韵母”。开启后，匹配键的原生中央字母保持显示，复韵母仍在键底，其余中央大字母隐藏；无刻模式下仍隐藏数字、符号角标；不论是否开启无刻模式均生效。结束韵母阶段、关闭开关或切换键盘后恢复原字母。
- **零声母音节**：末段只有 a/e/o 时，以该字母作为韵母开头筛选：底部仅显示同前缀、长度大于两个字母的韵母，不再提示 ai/an/ao 等两字母韵母。开启单韵母显示后，中央按实际可接续字母过滤：a → i/n/o，e → i/n/r，o → u；完成音节后恢复初始提示。
- **隐藏 Gboard 数字提示**：默认关闭，独立于双拼提示和无刻模式。在各种输入方案中隐藏按键右上角数字角标，保留主键文字、数字页和长按行为；关闭后恢复。
- **分词键改为行内大写**：默认关闭。在中文解码流程中通过 PRESS + DECODE 撇号动作识别原生分词键，改为一次性大写键，不依赖按键位置或资源 ID，按当前主解码器确认中文模式，并同时拦截 IME 分发和中文解码处理入口；点击后仅将下一字母转换为大写，以原生 DECODE 动作保留在预编辑串中，继续输入后可按回车提交英文；再次点击可取消。离开输入界面、切换语言或关闭选项后取消临时状态。符号页普通撇号、非中文方案和密码框不拦截。适配失败时保留原始按键并记录日志。
- **无刻模式**：单个开关，默认关闭。隐藏字母键原有文字及数字、符号角标，保留双拼提示；双拼提示由自己的开关独立控制。仅作用于实际启用双拼的字母键盘；英文、全拼、其他输入方案、数字页和符号页保留原样。关闭时恢复原有透明度，无障碍描述不变。
- **模块设置页**：使用原生 Material 3 组件，各功能标题使用 16sp 常规字重，重启按钮显式使用 400 常规字重；说明文字保留原有字号；跟随系统深浅色模式。Android 12 及以上在动态配色可用时使用系统 Monet，较旧系统使用 Material 3 默认配色。

- **隐藏模块启动器图标**：默认关闭，仅切换启动器别名；隐藏后可从 LSPosed 打开设置。
- **隐藏 Gboard 启动器图标**：默认关闭，副标题“需系统启动器支持”。通过 Gboard 进程仅禁用其 MAIN/LAUNCHER 入口，输入法服务与设置入口不变；关闭后恢复模块隐藏前的组件状态，不覆盖 Gboard 本来的隐藏设置。Gboard 尚未运行时，下一次唤起键盘后应用。
- **重启 Gboard**：通过模块注入的接收器请求 Gboard 自身退出，重新唤起键盘后加载模块。首次启用或覆盖更新后，如果尚未连接接收器，需先手动强行停止 Gboard 一次。

## 下载

从 [GitHub Releases](https://github.com/rikumi/gboard-shuangpin-hints/releases) 下载模块 APK。当前为实验性版本，真实设备兼容性仍需反馈；详见下方验证范围。

## 安装

1. 安装模块 APK，在 LSPosed 中启用，作用域选择 **Gboard**。
2. 强制停止 Gboard，再打开任意文本输入框。
3. 在 Gboard 的中文语言设置中选择双拼方案。
4. 打开“Gboard 双拼提示”配置提示或无刻模式。之后修改选项会通知运行中的 Gboard；也可收起并重新打开键盘。

需要 Android 6.0 / API 23 或以上，以及支持传统 Xposed API 82 的 LSPosed 版本。模块没有联网权限，不保存输入内容。日志仅包含适配结果和异常类型。

## 验证范围

| Gboard 样本 | APK 签名 | 静态适配检查 | 实机 LSPosed |
| --- | --- | --- | --- |
| 9.9.15.333092878-beta-arm64-v8a | Google 原始签名 | 通过 | 待验证 |
| 15.5.8.766552071-lite_release-arm64-v8a | Google 原始签名及轮换签名 | 通过 | 待验证 |
| 16.6.3.855170483-release-arm64-v8a | Google 原始签名及轮换签名 | 通过 | 待验证 |

不设置 Gboard 版本号白名单。以上是实际下载并分析的样本，不代表所有版本、分发变体或设备都已验证。静态检查核对按键元数据、绑定方法、拼音处理器回调、输入法服务继承关系和六种方案值；详细结果在 [compatibility/apk-analysis.json](compatibility/apk-analysis.json)。源码也处理较新 Gboard 使用具名 `SoftKeyDef` 和列表标签的情况，但该分支尚未用新版 APK 验证。

0.1.16 构建通过 86 项回归测试，覆盖键帽像素排版、独立水平压缩与字号、原生中央文字放行、默认字体与无字体资产、内部拼音状态与分隔符、非双拼键盘恢复、独立有效音节配对、带混淆定义 ID 的原生分词键识别、图标替换、行内大写动作及恢复、初始 U/I/V 隐藏、各开关的默认值和配置读取、无刻交互、跨输入方案数字角标隐藏和恢复、模块及 Gboard 启动器图标隐藏和原状态恢复、重启请求和原生 Material 3 Monet 日夜配色。报告在 `compatibility/test-results`。回归用例还覆盖主解码器识别、辅助 HMM 回调误覆盖、外层入口缺失时的中文解码兜底，以及一次性大写和回车原生提交路径。三份真实 Gboard APK 另核对了内部预编辑更新回调、缓存或读取方法、清空接口、通用输入法激活回调、行内大写事件分发与不可变动作构造，结果在 `compatibility/apk-analysis.json`。

在当前开发环境中没有连接 Android 设备，因此尚未进行真实 LSPosed 注入、主题外观、单手/悬浮键盘的实机验收。无法识别的接口会在 LSPosed 日志中输出以 `GboardHints:` 开头的信息。首次实际读取内部串时，额外输出 `internal read; cache=…; nonempty=…; separated=…; pending=…`，用于核对运行时读取路径；不输出输入字符或原文。不可识别的方案不覆盖原有键帽。

## 构建与检查

标准 Android 工程使用 JDK 21、Android SDK 35 和 Gradle 9.3.1：

```sh
export JAVA_HOME=/path/to/jdk21
export ANDROID_HOME=/path/to/android-sdk
./gradlew assembleDebug testDebugUnitTest
```

辅助构建入口可初始化调试签名、校验 Xposed 编译 API，并输出统一命名的签名 APK：

```sh
python3 tools/build.py
```

APK 输出至 `build/gboard-shuangpin-hints-0.1.16-debug.apk`。设置页使用 Material Components 1.12.0；构建会下载相应资源和依赖。Xposed API 只参与编译，不会打包进模块。

回归测试使用 Robolectric Android 9 和 Android 12；像素测试使用原生图形运行时，验证提示不会落入额外触摸区域、长提示仅水平压缩并保留设定字高、复声母和韵母分行、原生字母和分号字号不变，以及数字角标隐藏。界面测试核对开关默认值、配置传递及 Monet 日夜主题的系统色映射。

对自行取得的 Gboard APK 重复静态检查：

```sh
python3 tools/analyze_apks.py /path/to/gboard9.apk /path/to/gboard15.apk /path/to/gboard16.apk --output compatibility/local-analysis.json
```

## 参考和许可

LGPL-2.1-or-later。项目源码发布在 [rikumi/gboard-shuangpin-hints](https://github.com/rikumi/gboard-shuangpin-hints)。双拼映射和有效音节数据来自 [fcitx/libime](https://github.com/fcitx/libime)，保留其版权声明。生成后的 Java 表已经包含在源码中；构建模块不需要 libime。原始数据及其散列在 `tools/reference`，可运行 `python3 tools/generate_scheme_data.py tools/reference` 重新生成。

公开模块 [KeyFlux](https://github.com/NawafCode/KeyFlux) 和 [Gboard patches](https://github.com/jasonwu1994/Gboard-patches) 用于了解现有适配方式，未复制其实现代码。构建中使用的 Gradle Wrapper 和 Xposed 编译 API 各自遵循其上游许可；Xposed API 不打包进模块 APK。
