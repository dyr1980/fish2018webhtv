# Tasks: autosize-screen-adaptation

## 1. 依赖与 Manifest

- [x] 1.1 在 `app/build.gradle` 增加 `mobileImplementation libs.androidautosize`（catalog 中 `androidautosize = 1.2.1` 已存在，无需改 toml）
- [x] 1.2 在 `app/src/mobile/AndroidManifest.xml` 的 `<application>` 内新增 meta-data：`design_width_in_dp=390`、`design_height_in_dp=844`

## 2. Setting 缩放映射改造（design D3/D5）

- [x] 2.1 在 `Setting.java` 新增 `getUiScaleDesignWidth()`：返回 `Math.round(390 / factor)`，跟随系统返回 390；390 以常量 `UI_DESIGN_WIDTH_DP` 声明
- [x] 2.2 移除 `Setting.wrapUiScale(Context)` 与仅被其使用的 `pxToDp`；`getUiScaleFactor` 保留供 2.1 使用；确认 `getUiScale/putUiScale/getUiScaleIndex/putUiScaleIndex/UI_SCALE_OPTIONS` 及设置界面调用零改动
- [x] 2.3 全局搜索确认 `wrapUiScale` 无残余引用

## 3. mobile BaseActivity 接入 hook（design D1/D2）

- [x] 3.1 删除 `app/src/mobile/.../BaseActivity.java` 的 `attachBaseContext` 覆写（`Setting.wrapUiScale` 调用点）
- [x] 3.2 增加 `getResources()` 覆写：`AutoSizeCompat.autoConvertDensity(super.getResources(), Setting.getUiScaleDesignWidth(), isPortrait)`，其中 `isPortrait` 取自 `super.getResources().getConfiguration().orientation`；整体 try/catch 兜底返回原始 resources（与 leanback `hackResources` 同构）
- [x] 3.3 确认 `onConfigurationChanged` 场景下旋转后布局正确（静态核查：hook 每次调用按当前 orientation 选基准轴，AutoSize 经 ComponentCallbacks 跟进屏幕尺寸；leanback 的 onConfigurationChanged 仅做 TV 特有 hideSystemUI，mobile 无需补充代码；运行时表现由 5.2 验证）

## 4. 编译与静态核查

- [x] 4.1 双 flavor 编译通过：`leanbackArm64_v8aDebug` 与 `mobileArm64_v8aDebug`
- [x] 4.2 核查 `ResUtil.dp2px/sp2px` 使用的 metrics 来源；核查散落的 `* density` 调用点（`CustomWallView`、`SafeScrollEditText`、对话框、`HomeWebController`），与适配后 Activity density 不一致的改为使用 Activity context（结果：散落调用点均已用 Activity/View context 无需改；`ResUtil` 无参 `getDisplayMetrics()` 改为优先取 `App.activity()`，见 design D8）
- [x] 4.3 确认 `App.java` 无需改动（design D6：不引入全局 `AutoSizeConfig`）；覆盖面核查修正：`CrashActivity` 实际继承 BaseActivity，与 `PlaybackActivity` 一样参与适配，手机端全部页面无遗漏（design D7 / spec「手机端页面全量覆盖」已同步更新）

## 5. 手机端回归验证（spec: 手机端 390dp 基准 / UI 缩放映射）

- [ ] 5.1 逐页回归 9 个 mobile Activity（Home/Search/File/Folder/History/Keep/Live/Scan/Video）：竖屏布局与设计稿一致、无越界/裁切（真机 HLK-AL00 冒烟已过：Home/Search/History/Live/设置页 ✓ 布局无越界裁切；File/Folder/Keep/Scan/Video 待人工回归）
- [ ] 5.2 横竖屏旋转验证（重点 VideoActivity 全屏播放、LiveActivity）：旋转前后控件物理尺寸一致，无 2 倍放大（真机冒烟：HomeActivity 横屏 ✓ 短边不变量生效、控件物理尺寸与竖屏一致；Video/Live 全屏旋转待人工回归）
- [x] 5.3 界面大小档位验证（健哥反馈"标准太小"后改为五档：跟随系统=不适配原生渲染 / 超级大1.2 / 大1.1 / 标准1.0 / 小0.9，见 design D3 修订）：真机验证五档文案渲染 ✓、标准档切换即时生效 ✓、冷启动持久化 ✓、跟随系统=系统原生大小 ✓；超级大/大/小三档的视觉确认由健哥试用
- [ ] 5.4 系统字体缩放组合验证（design D4 行为变化）：系统大字体 + 自定义缩放档下文字随系统放大，记录截图供健哥验收拍板
- [ ] 5.5 WebView 场景验证（`HomeWebController` 等）：加载页面后返回原生页面无 density 错乱；如出现，按 AutoSize 官方 FAQ 方案在 WebView 创建后重新触发转换
- [ ] 5.6 对话框/播放器控制条/弹幕设置等第三方与自定义 View 抽查

## 6. 电视端零回归确认（spec: 电视端适配行为保持不变）

- [ ] 6.1 leanback 抽查 Home/Vod/Video/Setting 页面，确认渲染与变更前一致（960×540 基准未受影响）
- [x] 6.2 确认 leanback BaseActivity、manifest 在本变更中零 diff（git status 确认改动仅涉及 build.gradle、Setting.java、ResUtil.java、mobile manifest、mobile BaseActivity；ResUtil 属共享代码但对 TV 换算像素值不变，见 design D8）
