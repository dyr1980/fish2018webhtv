# Design: autosize-screen-adaptation

## Context

- **电视端（leanback）已接入 AndroidAutoSize 1.2.1**（JitPack `com.github.JessYanCoding:AndroidAutoSize`，catalog 别名 `libs.androidautosize`）：
  - meta-data 在 `app/src/leanback/AndroidManifest.xml`：`design_width_in_dp=960`、`design_height_in_dp=540`；
  - hook 在 leanback `BaseActivity.getResources()` → `hackResources()` → `AutoSizeCompat.autoConvertDensityOfGlobal(resources)`，try/catch 兜底返回原始 resources；
  - 无任何 `AutoSizeConfig` 全局配置，纯默认行为。
- **手机端（mobile）未接 AutoSize**：mobile `BaseActivity.attachBaseContext` → `Setting.wrapUiScale(newBase)`（`Setting.java:356-385`），按用户"UI 缩放"五档系数（0.9/0.85/0.8/0.75/0.7，另有"跟随系统"直通）改写 `densityDpi`（基于 `DENSITY_DEVICE_STABLE`），强制 `fontScale=1.0`，重算 screenWidthDp/HeightDp/smallestScreenWidthDp。
- 「午夜首映」手机端设计稿画布宽度为 **390px**（`design-preview/redesign/mobile-home.html`），即手机布局是按 390dp 宽的基准写的。
- 全项目**没有任何 dimens.xml 变体**（无 sw600dp/television），布局直接写 dp 值；散落若干手动 px 换算（`ResUtil.dp2px/sp2px`、个别 View 里 `* density`）。
- 仓库配置集中在 `settings.gradle`（`FAIL_ON_PROJECT_REPOS`），jitpack.io 已在列，依赖无需 vendor 到 `third_party/maven`。
- mobile 全部 Activity 为 `fullUser`（可旋转），leanback 全部 `sensorLandscape`。

## Goals / Non-Goals

**Goals:**

- 手机端 Activity 全量接入 AutoSize，按 390dp 短边基准适配，横竖屏行为一致（元素物理尺寸不因旋转跳变）。
- 保留用户可见的"UI 缩放"设置项（五档 + 跟随系统），实现方式从手动密度改写替换为 AutoSize 设计尺寸系数映射。
- 消灭双机制并存：`wrapUiScale` 的密度改写逻辑退役。
- 电视端行为零变化（960×540 基准、现有页面渲染结果不变）。

**Non-Goals:**

- 不重写任何布局 XML、不引入 dimens.xml 变体体系（AutoSize 的意义就是免改布局）。
- 不处理平板/折叠屏专属布局（仍按手机基准等比缩放）。
- 不改造 leanback 的 960×540 基准和 hook 写法。
- 不 vendor AutoSize 到 `third_party/maven`（jitpack 可解析）。

## Decisions

### D1: 手机端 hook 位置 —— `getResources()` + `AutoSizeCompat.autoConvertDensity`，弃用 `attachBaseContext`

与 leanback 保持同构（`getResources()` hook + try/catch 兜底），但 mobile 不用 `autoConvertDensityOfGlobal`，改用带参版本：

```java
AutoSizeCompat.autoConvertDensity(super.getResources(), effectiveDesignWidth(), isPortrait());
```

- **为什么不用 attachBaseContext + wrapUiScale 式的 ConfigurationContext**：AutoSize 的标准工作方式就是改写 Resources 的 DisplayMetrics；沿用 leanback 已验证的 hook 模式，两端心智一致。
- **为什么用带参版本而非 Global**：mobile 需要按用户缩放档位动态调整设计宽度、按横竖屏切换基准轴，带参版本无需反复改全局 `AutoSizeConfig` 状态（leanback 的 Global 调用共享同一份全局配置，动态改会互相污染——虽然两 flavor 不同 APK，但同构代码更安全清晰）。
- `AutoSizeCompat` 在非主线程调用会抛异常，沿用 leanback 的 try/catch 返回原始 resources 兜底。
- mobile `BaseActivity.attachBaseContext` 中的 `Setting.wrapUiScale` 调用删除。

### D2: 设计基准 —— 短边 390dp，横竖屏切换基准轴

- 竖屏：`baseOnWidth=true`，设计宽 390（对齐「午夜首映」设计稿画布）。
- 横屏（如 VideoActivity 全屏播放、用户旋转）：`baseOnWidth=false`，设计值仍为 390（此时 390 作为设计高）。
- 效果：始终以**屏幕短边 = 390dp** 为不变量，旋转前后控件物理尺寸一致。
- mobile manifest 补 meta-data `design_width_in_dp=390`、`design_height_in_dp=844` 作为库初始化的兜底声明（实际转换走带参调用）。
- 备选方案（否决）：固定 `baseOnWidth=true` —— 横屏时以 ~800dp 实际宽对 390 设计宽，UI 放大 2 倍，不可用。

### D3: "界面大小"设置映射 —— 有效设计宽 = 390 / factor，跟随系统 = 不适配（健哥验收后修订）

初版沿用旧的六档"紧凑系"档位，健哥试用后反馈"标准太小"，修订为五档（存储值见括号）：

| 档位 | 存储值 | factor | 有效设计宽 | 说明 |
|---|---|---|---|---|
| 跟随系统 | 0 | — | 不适配 | `getUiScaleDesignWidth()` 返回 0，hook 走 `AutoSizeCompat.cancelAdapt` 还原系统原生渲染 |
| 超级大 | 7 | 1.2 | 325 | |
| 大 | 6 | 1.1 | 355 | |
| 标准 | 1 | 1.0 | 390 | 设计稿基准（比旧"标准"0.9 大一档） |
| 小 | 2 | 0.9 | 433 | 即旧"标准"的实际大小 |

旧存储值迁移：1（旧标准 0.9）→ 新标准 1.0（更大，正合反馈）；2（旧紧凑 0.8）→ 小 0.9（最接近档位）；3/4/5（更小/微紧凑/更紧凑）不在新集合中，回落到跟随系统。设计宽调大 → UI 整体变小。设置变更后走现有 `recreate()` 生效。"跟随系统 = 完全不适配"意味着默认状态下手机端按设备原生 density 渲染，仅当用户选择具体档位时才走 390 基准适配——这是健哥拍板的行为（`cancelAdapt` 防止同一 Resources 对象残留上次会话的转换密度）。

### D4: fontScale 策略 —— 采用 AutoSize 默认（跟随系统字体缩放），移除强制 fontScale=1

原 `wrapUiScale` 在自定义档位下强制 `fontScale=1.0`。AutoSize 默认按初始 scaledDensity/density 比例保留系统字体缩放。决定**不设置** `setExcludeFontScale`，即所有档位都尊重系统字体大小：

- 理由：无障碍友好；避免引入全局 `AutoSizeConfig` 改动波及 leanback；"跟随系统"档位在旧实现下本来就尊重系统字体缩放，新实现让六档行为一致。
- 这是一处**有意的行为变化**（自定义档位下系统大字体用户会看到更大的字），在 Risks 中标注、验收时确认。

### D5: `Setting.wrapUiScale` 退役方式 —— 删除方法体引用，保留档位读写 API

`wrapUiScale(Context)` 及私有辅助 `getUiScaleFactor/pxToDp` 中仅被它使用的部分移除或改造为 D3 的设计宽换算；`getUiScale/putUiScale/getUiScaleIndex/putUiScaleIndex/UI_SCALE_OPTIONS` 等设置项 API 原样保留（设置界面还在用）。

### D6: 不引入 App 级 `AutoSizeConfig` 初始化

AutoSize 经 InitProvider 自动初始化，两端现状即可工作；D4 决定不改全局配置后，`App.java` 无需任何改动。若后续需要（如日志、excludeFontScale 回退），再加最小配置。

### D7: 页面覆盖 —— 全页面适配，以"是否继承 BaseActivity"为机制边界

hook 挂在各 flavor 的 `BaseActivity`。实现期核查发现：mobile 的 9 个 Activity（`LiveActivity`/`VideoActivity` 经由 `main` 的 `PlaybackActivity`）与 `main` 的 `CrashActivity` **全部继承 BaseActivity**，因此手机端所有页面无一例外参与适配——这与需求"所有页面都要适配"一致（提案早期草稿误以为 CrashActivity 不继承 BaseActivity 可天然豁免，已修正）。如未来出现不应适配的页面，其手段是不继承 BaseActivity（当前无此类页面）。WebView 场景（`HomeWebController` 等）保留 AutoSize 官方已知问题的对策位：WebView 初始化会重置 density，如回归时发现受影响，在创建 WebView 后触发一次重新转换（任务清单中列为验证项，不预先写死代码）。

### D8: `ResUtil` 无参 `getDisplayMetrics()` 路由到前台 Activity

`ResUtil.dp2px/sp2px`（全项目约 110 处调用）原走 `App.get()` 的 Application context，metrics 不经 AutoSize 转换，与适配后的 Activity density 脱节（手机端 390dp 基准下偏差可达 ±8%）。改造：无参 `getDisplayMetrics()` 优先取 `App.activity()`（App 已通过 ActivityLifecycleCallbacks 追踪前台 Activity），为空时回退 App context。散落的 `* density` 调用点（弹幕对话框、`SafeScrollEditText`、`HomeWebController` 等）核查后确认全部使用 Activity/View context，自动适配，无需改动。leanback 影响中性：960dp 设计基准与标准 TV density 数学重合（1080p: 1920/960=2.0 vs xhdpi 2.0；720p: 1280/960≈1.333 vs tvdpi≈1.331），换算像素值相同。

## Risks / Trade-offs

- [手机端所有页面尺寸整体变化] 接入后手机 UI 按 390dp 基准渲染，与设备原生 density 下的旧观感存在偏差（尤其窄屏/宽屏机型）→ 缓解：390 来自现行设计稿画布，且「午夜首映」布局本就按它标注；逐页面回归 9 个 mobile Activity。
- [fontScale 行为变化（D4）] 自定义缩放档 + 系统大字体的用户，文字会比旧版大 → 缓解：属修正性变化；若验收不接受，回退方案是 `AutoSizeConfig.setExcludeFontScale(true)`（一行）。
- [ResUtil.dp2px 等手动换算与 Activity density 不一致] `ResUtil` 若用 Application context 的 metrics，算出的 px 与适配后的 Activity density 脱节 → 缓解：leanback 已长期在同构模式下运行、问题面已知；实现时核查 `ResUtil` 及散落的 `* density` 调用点（`CustomWallView`、`SafeScrollEditText`、对话框、`HomeWebController`），必要时改为传入 Activity context。
- [WebView 重置 density] AutoSize 已知问题 → 缓解：D7 的验证项，出现再修（官方 FAQ 有标准解法）。
- [第三方 View（Material Dialog、播放器控件、弹幕）在转换后的 density 下渲染] → 缓解：它们读的是 Activity resources，理论上随基准等比缩放；回归时重点看对话框与播放器控制条。
- [横竖屏切换瞬间的 Configuration 竞态] `getResources()` 每次调用都转换，旋转时短暂读到旧 metrics → 缓解：与 leanback 现状一致（其 `onConfigurationChanged` 已有处理），mobile 同样在 `onConfigurationChanged` 后依赖重新布局，实测验证。

## Migration Plan

1. 依赖 + manifest + BaseActivity hook + Setting 映射一次性落地（单 APK 内自洽，不存在灰度中间态）。
2. 回归顺序：mobile 九个 Activity（重点 Home/Video/Live）横竖屏 → UI 缩放六档 → 系统字体缩放组合 → leanback 抽查 Home/Video 确认零变化。
3. 回退策略:revert 该提交即回到 wrapUiScale 方案（设置项存储格式未变，用户无感）。

## Open Questions

- 无阻塞项。D4（fontScale）是唯一预期的用户可感知行为变化，验收时由健哥拍板是否保留。
