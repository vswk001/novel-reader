# 墨阅 MoYue · 本地小说阅读器（Android）

纯本地、零网络权限、无广告的小说阅读器。把手机里的 TXT 读舒服，是它唯一的事。

设计文档见 [docs/设计方案.md](docs/设计方案.md)。

## 功能一览

**书架**
- 列表 / 网格双视图，自动生成渐变封面
- 最近阅读 / 书名 / 添加时间排序，置顶，书架内搜索
- 长按管理：书籍详情（编码/章节/大小/阅读时长）、移出书架、删除

**导入**
- 系统文件选择器多选导入（txt / log / md / **epub**）
- **EPUB 支持**：解析 OPF/spine 阅读顺序与 NCX/nav 目录，正文转为统一阅读管线（基础版，忽略排版样式/图片）
- 整个文件夹递归扫描批量导入
- 自动编码识别（UTF-8 / UTF-16 / GBK / GB18030，BOM 优先 + 严格校验）
- 自动章节目录识别（中文章节 / 卷 / 楔子序章番外 / Chapter N / 数字编号，多模式合并，无章节自动分段兜底）
- 导入即拷贝到应用私有目录，源文件删除不影响阅读

**阅读器**
- StaticLayout 精准分页，字号 / 行距 / 边距实时重排，进度按字符偏移存储不丢失
- 阅读模式：左右翻页 / 上下滚动连续阅读（可切换）
- 点击翻页区域可选：左右两侧 / 上下两段 / 关闭；音量键翻页；滑动翻页
- 翻页动画：滑动 / 覆盖 / 淡入 / 无 / 仿真（书本式 3D 翻转）
- **TTS 听书**：系统语音引擎整本连读（自动翻页/换章），语速可调，支持音频焦点（自动压低音乐）
- **自动滚动**：滚动模式匀速上滚，速度可调，触底自动进下一章，任意触摸停止
- **亮度手势**：阅读页左半屏上下拖动调节亮度（左侧 HUD 实时显示百分比）
- 5 套阅读主题（白 / 羊皮纸 / 护眼绿 / 灰 / 黑）+ 跟随系统深色模式
- 黑体 / 宋体切换，字号行距边距自由调节
- 手动亮度（仅作用本应用窗口，并记忆）或跟随系统
- 沉浸式全屏（菜单弹出不再唤出系统导航栏）、屏幕常亮（可选）
- 页脚：章节 · 页码 · 时间 · 电量
- 目录抽屉（当前章高亮、正倒序、拖动进度条跳章）
- 书内全文搜索（大小写不敏感，结果直接跳转）
- 书签：一键收藏当前页（带摘录）、列表跳转、删除

**隐私空间**
- 书架页面下拉过半屏出现「松开进入隐私空间」提示，松开即可进入
- 首次进入引导设置密码：数字密码（4-8 位）或图案密码（3x3 连线，至少 4 点）
- 密码经 PBKDF2 加盐哈希存储，独立于备份数据
- 忘记密码：可只清除隐私空间数据（普通书架保留）后重新设置
- 隐私空间与普通书架完全独立：各自的书架、导入、排序、统计
- 长按书籍可在两个空间之间转移（文件、进度、书签保持不变）
- 隐私空间内可修改密码；防截屏（FLAG_SECURE）为设置中的独立开关

**其它**
- 阅读统计：今日 / 累计时长、单书排行（按空间过滤）
- 本地备份 / 恢复：书架 + 进度 + 书签 + 设置导出 JSON（按「书名+文件大小」匹配合并）
- 41 个 JVM 单元测试覆盖编码检测 / 章节解析 / 文本规范化 / 密码哈希 / EPUB 解析

## 构建

环境要求：JDK 17、Android SDK（platform 36、build-tools）、Gradle 8.14+。

```bash
# 方式一：Gradle Wrapper（自动下载 Gradle）
./gradlew :app:assembleDebug

# 方式二：本机工具链（本仓库所在机器）
export JAVA_HOME=D:/workspace/android-tools/jdk-home
export ANDROID_HOME=D:/workspace/android-tools/sdk
D:/workspace/android-tools/gradle-8.14.3/bin/gradle :app:assembleDebug
```

产物：`app/build/outputs/apk/debug/app-debug.apk`（项目根目录已附 `墨阅-v1.0.0-debug.apk` 可直接安装）。

运行测试：

```bash
./gradlew :app:testDebugUnitTest
```

## UI

- Material 3 + Android 12+ 动态取色（Material You），低版本回退精修靛蓝配色
- 圆角卡片、分组设置页、阅读菜单悬浮圆角面板、封面进度条

## 技术栈

Kotlin 2.0 · Jetpack Compose + Material 3 · Room · DataStore · Navigation Compose ·
kotlinx.serialization · AGP 8.13 · minSdk 24 / targetSdk 36

架构：单 Activity + MVVM + ServiceLocator（见设计文档第 4 节）。
分页引擎与渲染共用同一 StaticLayout 参数，保证"所见即所排"。

## 项目结构

```
app/src/main/java/com/moyue/reader/
├── MoyueApp / MainActivity / di/Graph        # 入口与服务定位器
├── data/
│   ├── db/        Room：books/chapters/bookmarks/read_stats
│   ├── importer/  CharsetDetector · TextNormalizer · ChapterParser · BookImporter
│   ├── repo/      BookRepository · SettingsRepository(DataStore)
│   └── backup/    BackupManager(JSON 导出/导入)
├── reader/        PaginationEngine · ReaderViewModel · ReaderScreen · ReaderMenu
├── bookshelf/     BookshelfViewModel · BookshelfScreen
├── settings/      SettingsScreen
├── stats/         StatsScreen
└── ui/            AppNav · theme · common
```

## 路线图（P2）

EPUB 解析、TTS 朗读、滚动阅读模式、自定义字体导入、WebDAV 备份。
