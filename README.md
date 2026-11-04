# milo-agent

milo-agent 是一款 Android 个人生活记录助手。它把记账、随笔和日程放在同一个入口里：用户可以用文字或语音描述要记录的内容，检查草稿后再保存；配置模型服务后，也可以和 Milo 对话或让模型帮助整理输入。

## 当前功能

- **账本**：手工新增支出和收入；查看最近记录、本月收支与结余；按月份和日期浏览历史，并编辑已有账目。
- **自然语言记录**：从首页或账本输入文字，也可以使用 Android 系统语音识别。规则解析可离线识别常见金额、日期、时间和记录意图；设置 AI 模型后，复杂输入和闲聊会交给所选服务处理。
- **确认后保存**：解析结果先显示为可检查、可修改的草稿。账目需要有效金额和时间，随笔与日程也在用户确认后保存。
- **随笔**：新增、编辑、收藏生活记录，并可附加图片。
- **日程**：新增、编辑和删除事项，支持从自然语言提取日期和时间。
- **模型接入**：管理多个 OpenAI Chat Completions 兼容服务配置，填写 Base URL、模型名和 API Key，切换配置并测试连接。
- **对话记录**：首页保存本地对话和待确认草稿，重启后仍可恢复。

## 数据与隐私

账目存放在本地 Room 数据库；随笔、日程、首页对话和模型配置存放在本机 SharedPreferences。应用没有默认云同步。

不配置模型时，手工录入和规则能识别的输入仍可使用。使用模型时，当前输入会由设备直接发送到用户所选的服务商；闲聊请求也会携带最近的对话消息。模型返回结果不会自动写入记录，用户确认后才保存。

语音输入通过 Android 系统语音识别服务完成，是否可用以及识别过程是否需要网络取决于设备上的服务。

## 当前边界

- 日程是应用内的事项记录，目前不会触发系统通知或闹钟。
- 当前没有云同步、跨 App 自动操作、票据 OCR、后台离线重试队列、AI 使用审计页或基于历史账目的 AI 查询与总结。
- 模型配置保存在本机偏好设置中；项目当前没有单独的加密密钥存储实现。
- 当前交付为 Android 应用。虽然部分核心模块保持 Kotlin/Android 依赖隔离，但尚未实现完整的 Kotlin Multiplatform 跨端结构。

## 工程结构

```text
milo-agent/
├── app/                  # Android 应用、首页捕获、日记/日程/设置与 AI HTTP 客户端
├── core/
│   ├── common/           # 通用类型，例如以分表示的 MoneyCents
│   ├── designsystem/     # Compose 主题
│   ├── domain/           # 账目模型、Repository 契约和 UseCase
│   ├── database/         # Room 数据库、Entity 与 DAO
│   ├── data/             # Repository 实现与装配
│   └── ai/               # AI 能力契约
└── feature/
    └── ledger/           # 账本界面
```

首页、随笔、日程、模型设置和实际网络请求目前位于 `app` 模块；记账数据通过 `core` 中的 Room、Repository 和 UseCase 管理。日记和日程由应用层的本地 `LifeStore` 管理。

## 技术栈

- Kotlin、Jetpack Compose、Material 3
- Room、Coroutines、Flow
- Gradle Version Catalog
- 模型请求使用 Android `HttpURLConnection` 调用 OpenAI Chat Completions 兼容接口

金额使用整数分存储，避免浮点金额误差；交易时间以 epoch millis 保存，在界面按设备时区展示。

当前主要版本见 `gradle/libs.versions.toml`：AGP 9.5.0、Kotlin 2.2.10、Compose BOM 2026.02.01、Room 2.8.5、KSP 2.2.10-2.0.2。

## 构建

环境要求：Android Studio 2026.2.2（自带 JDK 21）和 Android SDK Platform 37。仓库的 `local.properties` 需要指向本机 Android SDK。

在 Windows PowerShell 中构建 Debug APK：

```powershell
./gradlew.bat ":app:assembleDebug"
```

运行单元测试：

```powershell
./gradlew.bat "testDebugUnitTest"
```

如需自定义 Gradle 缓存或代理，可在本机 shell 环境中设置 `GRADLE_USER_HOME` 与 `GRADLE_OPTS`，不要将代理或密钥写入项目文件。

## 后续方向

当前优先完善日常记录流程和数据可靠性。小组件、系统提醒、数据导出、AI 使用记录、本地检索、云同步、端侧模型和跨端支持都属于后续方向，尚未作为当前版本能力提供。
