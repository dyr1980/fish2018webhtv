# Proposal: autosize-screen-adaptation

## Why

项目双端目前的分辨率适配是两套割裂的机制：电视端（leanback flavor）已接入 AndroidAutoSize（设计稿 960×540dp，仅靠 manifest meta-data + BaseActivity 的 `getResources()` hook，无全局配置），而手机端（mobile flavor）完全没有用 AutoSize，靠自研的 `Setting.wrapUiScale` 手动改写 `densityDpi` 来做"UI 缩放"。这导致：手机端在不同分辨率/屏幕比例的设备上布局还原度不可控（尤其是刚落地的「午夜首映」新 UI，设计稿是固定画布尺寸），且两套密度操纵机制并存、互相看不见，后续维护和排查成本高。

## What Changes

- 手机端（mobile flavor）接入 AndroidAutoSize：添加 `mobileImplementation` 依赖、mobile manifest 的 `design_width_in_dp`/`design_height_in_dp` meta-data、mobile BaseActivity 的 `getResources()` → `AutoSizeCompat` hook。
- **BREAKING（内部行为）**：移除/改造手机端 `Setting.wrapUiScale` 的手动密度改写方案，避免与 AutoSize 双重操纵密度；用户可见的"UI 缩放"设置改为映射到 AutoSize 的设计尺寸系数上，功能保留、实现替换。
- 新增全局 `AutoSizeConfig` 初始化（在双端共享的 `App` 中），统一配置排除系统字体缩放（fontScale）等策略，收敛此前"零配置"的隐式行为。
- 电视端（leanback flavor）保持现有 960×540dp 基准不变，仅纳入统一的全局配置与豁免规则（如 CrashActivity 等共享页面的处理）。
- 对不参与适配的页面/场景（如 WebView 相关、系统对话框）建立豁免机制（`CancelAdapt` 或等效手段）。

## Capabilities

### New Capabilities

- `screen-adaptation`: 双端（手机/电视）基于 AndroidAutoSize 的分辨率适配能力 —— 覆盖设计稿基准尺寸、Activity 级密度转换、用户 UI 缩放设置与适配系统的协同、页面豁免规则。

### Modified Capabilities

（无 —— `openspec/specs/` 目前为空，没有已存在的能力规格。）

## Impact

- **Gradle/依赖**：`gradle/libs.versions.toml` 已有 `androidautosize = 1.2.1`（JitPack `com.github.JessYanCoding:AndroidAutoSize`），只需在 `app/build.gradle` 增加 `mobileImplementation`；仓库配置（settings.gradle 的 jitpack.io）无需变动。
- **Manifest**：`app/src/mobile/AndroidManifest.xml` 新增 design 尺寸 meta-data；leanback manifest 不动。
- **代码**：
  - `app/src/mobile/java/com/fongmi/android/tv/ui/base/BaseActivity.java`（改 `attachBaseContext`/`getResources()`）
  - `app/src/main/java/com/fongmi/android/tv/setting/Setting.java`（`wrapUiScale` 改造，约 356–385 行）
  - `app/src/main/java/com/fongmi/android/tv/App.java`（新增 AutoSizeConfig 初始化）
  - `app/src/leanback/java/com/fongmi/android/tv/ui/base/BaseActivity.java`（视需要对齐统一 hook 写法）
- **回归面**：手机端全部 Activity 的布局尺寸表现（HomeActivity、VideoActivity、LiveActivity 等 9 个），UI 缩放设置项的行为；电视端理论无变化但需回归验证 960×540 基准未被全局配置影响。
- **无网络/无新增外部系统**；不涉及 API、数据结构变更。
