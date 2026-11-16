# Miuix 界面风格规范（提炼自 KernelSU-Style-UI-Kit）

> 这份规范不是引用现成文档——`chenaizhang/KernelSU-Style-UI-Kit` 只有上手说明，**没有设计文档**。
> 下面的每一条都来自对它的 Miuix 分支代码实测（文件与行号），再落到本工程。

## 1. 页面骨架

每个页面都是同一个配方（`ui/screen/home/HomeMiuix.kt:88`、`settings/SettingsMiuix.kt:66`）：

```kotlin
Scaffold(
    topBar = { TopAppBar(title = ..., scrollBehavior = scrollBehavior) },
    popupHost = { },
    contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout)
        .only(WindowInsetsSides.Horizontal),
) { innerPadding ->
    LazyColumn(
        modifier = Modifier.fillMaxHeight()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .padding(horizontal = 12.dp),
        contentPadding = innerPadding,
        overscrollEffect = null,
    ) { ... }
}
```

- **横向留白固定 12dp**，由列表统一提供，卡片自己不再加外边距。
- 顶部栏用可折叠的 `TopAppBar` + `MiuixScrollBehavior()`，滚动时标题栏收拢。
- 卡片之间的间隔是 **12dp**，用 `Modifier.padding(top = 12.dp)` 或 `Arrangement.spacedBy(12.dp)`，两种写法等价。
- 内容窗外区域交给 scaffold 的 `contentPadding`，不要自己算状态栏高度。

## 2. 卡片与分组

- 卡片使用默认色 `CardDefaults.defaultColors()`（即 `surfaceContainer`），**不自定义圆角**。
- 同一类设置项放进**同一张卡**：一张卡可以连续放多个 preference（`SettingsMiuix.kt:95-140`），
  卡内**不画分割线**，靠行间距和按压反馈区分。
- 需要强调的区域才换容器色，例如状态卡使用浅绿 `#DFFAE4` / 浅红 `#F8E2E2`（`HomeMiuix.kt:152-153`）。
- 可点击卡片的按压反馈用 `pressFeedbackType = PressFeedbackType.Tilt` + `showIndication = true`。

## 3. 列表行：一律用 preference

| 场景 | 组件 |
|---|---|
| 开关 | `SwitchPreference(title, summary, checked, onCheckedChange)` |
| 数值 | `SliderPreference(title, value, onValueChange, valueRange, steps, valueText)` |
| 枚举选择 | `OverlayDropdownPreference(items, selectedIndex, title, onSelectedIndexChange)` |
| 进入/执行 | `ArrowPreference(title, summary, onClick)` |

行的左侧图标统一放在 `startAction`：

```kotlin
startAction = {
    Icon(
        MiuixIcons.Alarm,
        modifier = Modifier.padding(end = 12.dp),
        tint = colorScheme.onBackground,
        contentDescription = null,
    )
}
```

图标 24dp、**与文字之间留 12dp**（KSU 的 settings 用 6dp，home 用 12dp，本工程统一取 12dp）。

## 4. 字号层级

来自 `theme/TextStyles.kt`：`headline1` 32sp、`headline2` 24sp、`title*` 17/16/14sp、`main` 17sp、`body2` 14sp、`caption` 11sp。

- 卡片主标题：`22sp` + `FontWeight.SemiBold`（状态卡，`HomeMiuix.kt:196`）。
- 次级说明：`15sp` / 14sp，颜色 `onSurfaceVariantSummary`。
- 一般正文走 `MiuixTheme.textStyles.*`，不要到处硬编码 sp。
- **唯一例外**是计时器的「英雄数字」：62–76sp，属于产品核心信息，规范允许单独定制。

## 5. 对话框

- 用 `WindowDialog`（同 `OverlayDialog` 参数，但渲染在独立窗口，不受 scaffold 弹层宿主影响）。
- 内容底部是 `Row { TextButton(取消, weight 1f); Spacer(20.dp); TextButton(确认, weight 1f) }`，顶部 12dp。
- 破坏性操作把确认放右侧，取消放左侧。

## 6. 窗口与系统栏

- `enableEdgeToEdge`，状态栏/导航栏都透明，`window.isNavigationBarContrastEnforced = false`。
- 状态栏图标亮暗跟随主题（`SystemBarStyle.auto(...){ dark }`）。
- 底部导航用 `NavigationBar` + `NavigationBarItem`，每项自动 `weight(1f)`。

## 7. 本项目对规范的两处取舍

1. **图标库**：KSU 用的是 Material Icons（`androidx.compose.material.icons`）。你的要求是「只要 MIUIX」，
   所以本工程全部改用 `miuix-icons`（`MiuixIcons.Alarm/Weeks/Settings/...`），视觉上更接近系统应用。
2. **模糊背景**：KSU 支持 `miuix-blur` 的 `layerBackdrop` + `BlurredBar`（需 API 33 + 额外依赖）。
   本工程暂不引入，顶部栏用不透明 `surface`；后续要加只需接 `miuix-blur`。

## 8. 0.9.4 上不可用的 API

KSU 用了 `overScrollVertical()`、`scrollEndHaptic()`、`Modifier.pressFeedbackType`，这些在 **Miuix 0.9.4**
（本工程锁定的版本）里没有导出；我们改用标准 `LazyColumn` + `overscrollEffect = null` 达到同样的手感，
等升级到对应版本再补。
