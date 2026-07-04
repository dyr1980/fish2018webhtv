# screen-adaptation 增量规格

## ADDED Requirements

### Requirement: 手机端 Activity 按 390dp 短边基准适配

手机端（mobile flavor）所有继承 `BaseActivity` 的 Activity SHALL 通过 AndroidAutoSize 将屏幕短边换算为 390dp 设计基准进行密度转换，使同一布局在不同分辨率、不同屏幕比例的手机上按设计稿等比渲染。

#### Scenario: 竖屏渲染

- **WHEN** 手机端任一 BaseActivity 子类在竖屏下渲染布局
- **THEN** Resources 的密度按"实际屏幕宽度 = 390dp（除以缩放系数后的有效设计宽）"转换，布局中 390dp 宽的元素铺满屏幕宽度

#### Scenario: 横屏渲染

- **WHEN** 同一 Activity 旋转为横屏（如全屏播放）
- **THEN** 密度转换基准切换为高度轴、设计值仍为 390dp（短边不变量），控件物理尺寸与竖屏一致，不出现约 2 倍放大

#### Scenario: 非主线程访问 Resources

- **WHEN** 任意代码在非主线程调用 Activity 的 `getResources()`
- **THEN** 转换失败时 MUST 兜底返回未转换的原始 Resources，不抛出异常导致崩溃

### Requirement: 界面大小设置映射到适配基准

用户"界面大小"设置 SHALL 提供五档：跟随系统 / 超级大(1.2) / 大(1.1) / 标准(1.0) / 小(0.9)。具体档位通过调整有效设计宽度（390 / 档位系数）实现；"跟随系统"SHALL 完全跳过密度转换，渲染与系统原生一致。设置项的存储键 MUST 保持 `ui_scale` 不变。

#### Scenario: 用户切换缩放档位

- **WHEN** 用户在设置中把界面大小从"标准"改为"超级大"（系数 1.2）
- **THEN** Activity 重建后按有效设计宽 325dp（= 390 / 1.2）转换密度，UI 整体放大约 20%

#### Scenario: 跟随系统档位

- **WHEN** 界面大小为"跟随系统"
- **THEN** 不做密度转换（含 `cancelAdapt` 还原已转换的 Resources），页面渲染与系统原生完全一致，系统字体缩放被尊重

#### Scenario: 升级用户设置迁移

- **WHEN** 已设置过旧档位的用户升级到新版本
- **THEN** 旧"标准"(1)映射为新"标准"(1.0，更大)，旧"紧凑"(2)映射为"小"(0.9)，旧"更小/微紧凑/更紧凑"(3/4/5)回落为"跟随系统"，不崩溃、无需重装

### Requirement: 电视端适配行为保持不变

电视端（leanback flavor）SHALL 维持现有 AndroidAutoSize 接入方式：manifest 声明 960×540dp 设计基准、`BaseActivity.getResources()` 走 `AutoSizeCompat.autoConvertDensityOfGlobal`；本变更 MUST NOT 引入影响 leanback 渲染结果的全局 AutoSize 配置。

#### Scenario: 电视端回归

- **WHEN** 变更落地后在电视端打开 Home/Vod/Video 等页面
- **THEN** 页面渲染结果与变更前逐像素一致（960×540 基准未变、无新增全局配置副作用）

### Requirement: 手机端页面全量覆盖

手机端所有页面 SHALL 参与 AutoSize 适配，不允许存在漏网页面：mobile 源集的全部 Activity 与 `main` 源集中双端共享的 Activity（`PlaybackActivity`、`CrashActivity`）MUST 直接或间接继承各 flavor 的 `BaseActivity`。

#### Scenario: 播放页经由 PlaybackActivity 适配

- **WHEN** 打开 `LiveActivity` 或 `VideoActivity`（继承 `main` 的 `PlaybackActivity`，后者继承 BaseActivity）
- **THEN** 页面同样按 390dp 短边基准完成密度转换

#### Scenario: 崩溃页适配

- **WHEN** 应用崩溃后展示 `CrashActivity`（继承 BaseActivity）
- **THEN** 该页面同样经过密度转换，文字与控件按设计基准渲染

### Requirement: 像素换算工具与适配密度一致

`ResUtil.dp2px/sp2px` 等无 Context 参数的像素换算 SHALL 使用前台 Activity 的适配后 metrics（`App.activity()` 为空时回退 Application context），使代码计算的 px 与布局中的 dp 保持同一密度基准。

#### Scenario: 前台页面内换算

- **WHEN** 任一适配页面在前台时调用 `ResUtil.dp2px(16)`
- **THEN** 返回值基于该 Activity 适配后的 density 计算，与布局中 16dp 元素的实际像素一致

### Requirement: 手动密度改写机制退役

`Setting.wrapUiScale(Context)` 的 densityDpi 手动改写路径 SHALL 从手机端 `BaseActivity.attachBaseContext` 中移除，全项目 MUST NOT 同时存在两套密度操纵机制。

#### Scenario: 单一适配机制

- **WHEN** 检视手机端密度相关代码
- **THEN** 仅存在 AutoSize 一条密度转换路径，`wrapUiScale` 不再被任何调用方引用
