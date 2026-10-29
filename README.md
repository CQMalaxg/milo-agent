# milo-agent

一个 Local-first 的**个人生活助理 Agent**，用自然语言记账、写日记、记随笔。

核心交互不是填表单，而是说一句话：

> "午饭 32，AA 下来我付了 18"

然后确认一下，就记好了。

---

## 定位

milo-agent 是 **L2 层级的对话式助理**：自然语言为输入主入口，结构化数据为落地终点。

它的判断标准不是"能否聊天"，而是**一句话能否准确变成一条结构化记录**。记账、日记、随笔都是短输入、意图明确、有清晰落点的场景，正好命中这个能力区间。

不做的事：

- 不做跨 App 的自主操作（L3 层级，Android 系统权限受限，投入产出比差）
- 不做社交、不做多人协作，它只服务一个人
- 不做默认云同步

---

## 核心设计原则

这几条是硬约束，写代码时优先于任何便利性考虑。

### 1. Local-first，数据不出设备

Room 是唯一真相源。云同步不在 MVP 范围内，做也是做成可插拔模块，默认关闭。

### 2. AI 输出永不直接落库

```
用户输入 → AI 抽取 → 【确认卡片，可编辑】 → 用户确认 → 入库
```

涉及金额必须确认。AI 猜错的账目，会让用户对整个产品的信任直接崩掉，而信任崩掉是不可逆的。

确认卡片底部有「AI 说错了」入口，点击后把原文 + 错误结果存入 `CorrectionLog`。这批数据是未来做端侧微调的语料。

### 3. 允许模型拒答

`request_clarification` 是一等公民 Tool。遇到"记一笔"这种信息不全的输入，宁可反问也不要瞎猜。

### 4. BYOK（Bring Your Own Key）

用户自己填 `Base URL + Model + API Key`，App 直连厂商，**不经过任何中间服务器**。

这既是隐私架构，也让项目不需要后端。各家 API 大多兼容 OpenAI 协议，一套 Ktor 调用覆盖多家。App 内预置厂商预设（DeepSeek / 通义 / 智谱 / OpenAI / 自定义），但**不写死任何一家**。

### 5. KMP-ready

`domain` / `ai` / `network` / `common` 层**不得 import 任何 `android.*`**。

现在只做 Android，但未来上 iOS/桌面时只需重写 UI 层，核心逻辑与 Agent 引擎直接复用。这条约束的成本增量接近零，收益是跨端时的整个核心层。

> 注：`data` / `database` 层因为要用 `Context` 建库、用 Room，目前仍带 Android 依赖。等跨端时把它们拆成 `commonMain`（接口）+ 各平台实现即可，接口本身不变。

---

## 技术栈

所有选型都必须是 KMP 兼容的 —— 这直接排除了 Hilt/Dagger。

| 用途 | 选型 | 为什么 |
|---|---|---|
| UI | Kotlin + Jetpack Compose + Material3 | 入口在系统层：小组件、通知栏、分享面板、快捷方式，跨平台框架都要写两遍 |
| DI | **手动 DI（P0）** | P0 模块少，不值得上框架；P2 模块变多后再评估 Koin。注意 Hilt 绑定 Dagger/JVM，会锁死 KMP 之路 |
| 网络 | **Ktor Client + OkHttp engine**（P2 引入） | KMP 接口，Android 上复用 OkHttp 连接池 |
| JSON | kotlinx-serialization（P2 引入） | 与 Ktor 天然配合 |
| DB | Room 2.8.5 | 未来接 FTS5 / sqlite-vec 做检索 |
| 异步 | Coroutines + Flow | UI 只读 Flow，写操作一律走 UseCase |
| 导航 | Navigation Compose（P1 引入） | P0 单页，暂未引入 |
| 后台 | WorkManager（P2 引入） | 离线队列补偿、每日提醒 |
| 依赖版本 | Gradle Version Catalog (`libs.versions.toml`) | |

版本以创建工程时的最新稳定版为准，不做刻意的版本保守。当前锁定：

| 组件 | 版本 |
|---|---|
| Android Gradle Plugin | 9.5.0 |
| Kotlin | 2.2.10 |
| Gradle | 9.6.0 |
| Compose BOM | 2026.02.01 |
| Room | 2.8.5 |
| KSP | 2.2.10-2.0.2 |
| compileSdk / minSdk / targetSdk | 37 / 26 / 37 |

---

## 工程结构

```
milo-agent/
├── gradle/libs.versions.toml        # Version Catalog
├── app/                             # Android 壳：Application、Activity、DI 组装
├── core/
│   ├── common/                      # MoneyCents（金额一律以「分」存储）
│   ├── designsystem/                # MiloagentTheme，供 app 与各 feature 的 Preview 复用
│   ├── domain/                      # 实体 + Repository 接口 + UseCase
│   ├── database/                    # Room Entity / DAO / Database + 建库工厂
│   ├── data/                        # Repository 实现 + 仓库组装工厂
│   ├── ai/                          # AiProvider 契约（P0 仅接口，P2 接云端实现）
│   └── network/                     # Ktor Client（P2 引入）
└── feature/
    ├── ledger/                      # 记账页（P0 已实现，含两个 Preview）
    ├── capture/                     # 统一输入入口（P2）
    ├── journal/                     # 日记（P1+）
    ├── note/                        # 随笔（P1+）
    ├── chat/                        # Agent 对话（P3）
    └── settings/                    # AI 配置 + 使用记录 + 数据导出（P2）
```

依赖方向严格单向：`app → feature → domain ← data → database`，app 不直接依赖 Room（建库由 `core:data` 的工厂完成）。

---

## 数据模型

记账与日记**分表**（查询模式差异太大），日后用 `TimelineItem` 视图在时间线上串联。

```kotlin
// 金额一律用 Long 存「分」，全链路不出现 Double
value class MoneyCents(val value: Long)

data class Transaction(
    val id: String,
    val amountCents: Long,
    val currency: String,
    val type: TxType,              // EXPENSE / INCOME
    val categoryId: String?,
    val note: String?,
    val occurredAt: Long,          // epoch millis（UTC）
    val createdAt: Long,
    val source: CaptureSource,     // MANUAL / TEXT_AI / VOICE / IMAGE
)

data class JournalEntry(
    val id: String,
    val content: String,
    val mood: Mood?,
    val occurredAt: Long,
    val createdAt: Long,
)

data class Note(
    val id: String,
    val title: String?,
    val content: String,
    val createdAt: Long,
    val updatedAt: Long,
)
```

时间统一用 `Long`（epoch millis，UTC）而非 `Instant`：避免引入尚不稳定的时间 API，也让 domain 层保持纯 Kotlin。展示层才做时区与格式化。

每条记录都保留 `source` 与原始输入原文 —— 这是复盘 AI 抽取准确率的依据，也是纠错样本的来源。

---

## AI 交互契约

`core:ai` 中已有完整契约（`AiProvider` / `CompletionRequest` / `CompletionResult` / `RecordDraft`），P0 只定义不实现。

### Tool 定义

```
log_expense(amount, category, note?, occurredAt?)
log_income(amount, source?, note?, occurredAt?)
write_journal(content, mood?)
save_note(title?, content)
query_records(query, from?, to?)
request_clarification(question)
```

### 三个决定成败的 Prompt 细节

**① System Prompt 注入当前时间**

模型算"昨天""上周三"经常错 —— 因为它的"今天"是训练时的。每次请求都注入：

```
当前时间：2026-10-29 星期四 14:30 (UTC+8)
用户时区：Asia/Shanghai
```

这样模型直接输出时间，省掉本地相对时间解析器。

**② 用 Function Calling，不裸解析 JSON**

让厂商侧保证 schema，比自己正则捞 JSON 稳得多。同时 `parallel_tool_calls=false` —— "午饭 32 加地铁 5 块"应被拆成两次调用，但不能乱序。

**③ 模型分级**

```kotlin
CAPTURE_MODEL   // 意图路由 + 抽取：最便宜的小模型，每条约 200 token
SUMMARY_MODEL   // 周月总结、随笔润色：强模型
```

抽取是高频小任务，用小模型成本可忽略；总结是低频大任务，才值得用贵的。

### 离线暂存队列

纯云端方案的最大体验风险是断网即废。

```
输入 → PendingCapture(status=QUEUED) → 尝试云端解析
                                     ↓ 失败
                               status=RETRY → WorkManager 联网后补偿
```

用户永不丢失输入，联网后自动补解析。

### 最小化上传 + 可审计

- 只送当前这条输入，**绝不批量上传历史日记**
- 检索/总结类请求先在**本地**检索出片段再上传（本地 RAG，天然减少外传量）
- 设置页有「AI 使用记录」：某时、送给谁、多大、做了什么

---

## 路线图

| 阶段 | 内容 | 状态 |
|---|---|---|
| **P0** | 多模块骨架 + Room + 记账手工录入 + Preview | ✅ 已完成 |
| **P1** | 小组件 / 通知栏快捷记账 + 月度统计图表 | 待开始 |
| **P2** | `AiProvider` 云端实现 + BYOK 设置页 + 自然语言记账 + 确认卡 + 离线队列 | 待开始 |
| **P3** | 对话式查询 + 本地 FTS / 向量检索 + AI 使用记录页 | 待开始 |
| **P4** | 语音输入 + 月度/年度自动总结 | 待开始 |
| **P5** | 小票 OCR（多模态抽取金额） | 待开始 |

> P0 完成后自己先日用两周再往上加。数据模型的对错，只有在连续记了 30 笔账之后才会暴露。

### 后续演进路径

- **端侧化**：框架已按 `AiProvider` 接口抽象好，未来可插入 `OnDeviceProvider`（MediaPipe / ExecuTorch）与 `RuleProvider` 降级实现，按网络/电量/用户设置运行时切换
- **跨端**：核心层已是纯 Kotlin，上 iOS/桌面只需重写 UI
- **同步**：可插拔。如要做，走 E2E 加密（密钥留本地），冲突消解用追加日志 + LWW，不上 CRDT —— 单人单写场景冲突极少，CRDT 的复杂度不值

---

## 开发约定

1. `domain` / `ai` / `network` / `common` 层禁止 import `android.*`
2. 金额全链路用 `Long`（分），禁止 `Double`
3. 时间戳用 `Long`（epoch millis，UTC）存储，展示层才转本地时区
4. AI 输出必须先过确认卡，不得静默落库
5. 新功能先有手工录入路径，AI 路径是增强而非替代 —— 手工路径既是兜底，也是冷启动期的语料来源
6. Composable 分两层：无状态的 `XxxScreen`（可 Preview）+ 持有状态的 `XxxRoute`。直接把 ViewModel/Repository 写进 Screen 会让 Preview 变成空白页

---

## 环境要求

- Android Studio 2026.2.2（自带 JDK 21，**无需单独配置 JDK**）
- Android SDK Platform 37 / Build-Tools 36.0.0
- 模拟器镜像选 **x86_64**（ARM 镜像在 Windows 上慢到不可用）

### AGP 9 的两个坑

1. **Kotlin 已由 AGP 内置**，不要再应用 `org.jetbrains.kotlin.android` 插件，否则报
   `Cannot add extension with name 'kotlin'`。Compose 模块仍需要 `org.jetbrains.kotlin.plugin.compose`。
2. **KSP 与内置 Kotlin 冲突**（KSP 仍通过 `kotlin.sourceSets` 注入生成代码），需在 `gradle.properties` 加
   `android.disallowKotlinSourceSets=false`。

### 构建

命令行构建需自行提供代理与缓存位置（不要把代理写进项目文件）：

```powershell
$env:GRADLE_USER_HOME = "D:\Android\.gradle"
$env:GRADLE_OPTS = "-Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=7897 -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=7897"
.\gradlew.bat ":app:assembleDebug"
```

Windows 上 PowerShell 会把 `:app:assembleDebug` 的冒号解析成作用域符，**任务名必须加引号**。
