<div align="center">

<img src="docs/icon.png" width="96" alt="墨阅 MoYue" />

# 墨阅 · MoYue Reader

**纯本地、无广告的 Android 小说阅读器 · 联网仅用于检查更新**

[![Release](https://img.shields.io/github/v/release/vswk001/novel-reader?style=flat-square&label=%E5%8F%91%E5%B8%83)](../../releases)
[![Platform](https://img.shields.io/badge/platform-Android-3DDC84?style=flat-square&logo=android)](https://developer.android.com)
[![MinSdk](https://img.shields.io/badge/minSDK-24%20%7C%20Android%207.0+-blue?style=flat-square)](https://developer.android.com/about/versions/nougat)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?style=flat-square&logo=kotlin)](https://kotlinlang.org)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow?style=flat-square)](LICENSE)

把手机里的小说读舒服，是它唯一的事 · 中文 / English 双语界面

</div>

---

## ✨ 功能特性

### 📚 导入与书架

- **格式**：`txt` / `log` / `md` / `epub`，多选或整个文件夹递归扫描
- **三种入口**：应用内选择器 · 文件管理器「打开方式」· 系统分享到墨阅
- **编码自动识别**：UTF-8 / UTF-16 / GBK / GB18030（BOM 优先 + 严格校验）
- **章节目录自动识别**：中文章节 / 卷 / 楔子序章番外 / Chapter N / 数字编号，多模式合并；识别不满意可**一键重切**（进度不丢）
- **EPUB**：解析 OPF/spine 阅读顺序与 NCX/nav 目录，插图内联显示
- 列表 / 网格视图、三种排序、置顶、书内搜索、渐变封面自动生成
- **回收站**：删除先进回收站，7 天内可恢复，过期自动清理

### 📖 阅读器

- StaticLayout 精准分页，字号 / 行距 / 边距 / 字体实时重排，进度按字符偏移存储永不丢失
- 阅读模式：左右翻页 / 上下滚动（LazyColumn 懒排版，大书不卡）
- 翻页动画：滑动 / 覆盖 / 淡入 / 无 / **仿真 3D 书页翻转**
- **划线高亮与笔记**：长按选词、拖拽手柄精调、四色划线、笔记、列表管理、导出分享
- **书签**：一键收藏（带摘录）、跳转、去重
- **书内全文搜索**：命中直接跳转
- 目录抽屉：当前章高亮、正倒序、拖动进度条实时预览章节标题
- 页脚：章节 · 页码 · 时间 · 电量

### 🔊 听书与自动滚动

- **TTS 朗读**：整本连读自动翻页换章、语速可调、音频焦点自动压低音乐、引擎失败自动引导安装
- **睡眠定时**：15/30/60 分钟或本章读完即停
- **自动滚动**：速度可调、触底自动进下一章、任意触摸停止

### 🔐 隐私空间

- 书架下拉过半屏松开进入，与普通书架**完全隔离**（书架 / 导入 / 排序 / 统计）
- 数字密码（4-8 位）或图案密码，PBKDF2 加盐哈希独立存储
- **指纹 / 面部快捷解锁**（可选开关，需系统生物识别）
- 忘记密码：仅清除隐私空间数据，普通书架保留

### 📊 统计与备份

- 阅读统计：今日 / 累计时长、单书排行、**每日目标打卡 + 连续天数 🔥**
- 本地 JSON 备份 / 恢复：书架 + 进度 + 书签 + 划线笔记 + 设置
- 可选 FLAG_SECURE 防截屏（截屏黑屏、最近任务无预览）

### 🌍 其它

- 中 / 英双语，跟随系统语言，Android 13+ 支持按应用单独设置语言
- 5 套阅读主题 + 跟随系统深色、亮度手势（左半屏上下拖动）、音量键翻页、屏幕常亮
- 全程离线阅读（联网仅用于「检查更新」，地址可在设置中自定义），无广告、无遥测

## 📥 下载

前往 [**Releases**](../../releases) 下载最新签名版 `MoYue-vX.Y.Z-release.apk`。

| 要求 | 说明 |
|---|---|
| 系统 | Android 7.0（API 24）及以上 |
| 升级 | 直接安装覆盖升级；数据与进度保留 |
| 降级 | 不支持，需卸载（可先导出备份 JSON，重装后恢复） |

## ⌨️ 手势速查

| 手势 | 翻页模式 | 滚动模式 |
|---|---|---|
| 点击左/右（或上/下）区域 | 上/下一页 | — |
| 点击中间区域 | 呼出菜单 | 呼出菜单 |
| 左半屏上下拖动 | 调节亮度 | 调节亮度 |
| 长按正文 | 选词划线 | 选词划线 |
| 书架下拉过半屏松开 | 进入隐私空间 | — |

## 🛠 从源码构建

```bash
# 环境要求：JDK 17 · Android SDK (platform 36) · Gradle 8.14+
git clone <this-repo>
cd novel-reader
./gradlew :app:assembleDebug          # 调试包
./gradlew :app:testDebugUnitTest      # 单元测试
./gradlew :app:assembleRelease        # 发布包（配置 keystore.properties 后自动签名）
```

**检查更新源**（构建期注入，不写死在代码中）：

```bash
# 发布构建时通过 Gradle 参数或环境变量指定更新源（GitHub Releases /latest 兼容 JSON）
./gradlew :app:assembleRelease -PupdateFeedUrl=https://api.github.com/repos/YOUR/REPO/releases/latest
# 或 export UPDATE_FEED_URL=...
```

签名：在项目根目录放置 `keystore.properties`（已 gitignore）：

```properties
storeFile=moyue-release.keystore
storePassword=***
keyAlias=***
keyPassword=***
```

## 🏗 架构

单 Activity · MVVM · Jetpack Compose + Material 3 · ServiceLocator（`di/Graph`）

```
app/src/main/java/com/moyue/reader/
├── data/
│   ├── db/         Room v4：books · chapters · bookmarks · read_stats · highlights · book_images
│   ├── importer/   CharsetDetector · TextNormalizer · ChapterParser · EpubParser · BookImporter
│   ├── repo/       BookRepository · SettingsRepository(DataStore) · BackupManager
│   └── backup/     JSON 备份/恢复
├── reader/         PaginationEngine · PageSpans(划线/图片Span) · ReaderViewModel/Screen/Menu
├── bookshelf/      书架 + 隐私空间（下拉手势）
├── privacy/        PBKDF2 密码 · 图案锁 · BiometricPrompt
├── settings/ stats/ ui/ di/
```

**隐私与网络**：不采集任何数据；唯一联网行为是用户主动点击「检查更新」（默认源为 GitHub Releases API，可自定义）。

**关键设计**：分页引擎与页面渲染共用同一套 StaticLayout 参数与 Span 构建（`PageSpans`），图片行高测量与显示完全一致；阅读进度 / 划线 / 书签统一以字符偏移存储，字号行距变化与章节重切后自动映射。

## 🧪 测试

50 个 JVM 单元测试：编码检测 · 文本规范化 · 章节解析（含居中标题回归）· EPUB 解析 · 密码哈希。

```bash
./gradlew :app:testDebugUnitTest
```

## 🗓 版本历史

| 版本 | 亮点 |
|---|---|
| [v1.9.x](../../releases/tag/v1.9.2) | 划线笔记 · EPUB 内联图片 · 进入空白修复 |
| [v1.8.0](../../releases/tag/v1.8.0) | 滚动模式懒加载 · 听书定时 · 指纹解锁 · 回收站 · 阅读打卡 · 打开方式导入 |
| [v1.7.0](../../releases/tag/v1.7.0) | 中英双语国际化 |
| [v1.6.x](../../releases/tag/v1.6.5) | 全新应用图标 · 章节拖动预览等 |
| [v1.5.x](../../releases/tag/v1.5.0) | EPUB · TTS 听书 · 自动滚动 · 忘记密码 · 亮度手势 |
| 更早 | 隐私空间 · 多翻页动画 · Material 3 改版 · 章节引擎重构 |

完整变更见 [Releases](../../releases)。

## 📄 许可

[MIT](LICENSE) · 设计文档见 [docs/设计方案.md](docs/设计方案.md)

<div align="center">

**如果墨阅对你有用，欢迎点一个 ⭐ Star**

</div>
