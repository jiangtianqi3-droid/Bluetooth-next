# 听迹

听迹是一款基于 Kotlin + Jetpack Compose 开发的 Android 蓝牙耳机使用记录工具。  
它会在你选择的蓝牙设备连接时开始计时，在断开时结束计时，并把每次使用记录保存到本地数据库中。

这个仓库对应当前的“功能扩展版”工程，重点放在多设备监控、时间轴可视化、日历统计、睡眠时段排除和播放来源识别。

## 核心功能

- 从系统已配对蓝牙设备中多选目标设备
- 监听 `BluetoothA2dp` / `BluetoothHeadset` 连接状态并自动计时
- 仅在目标设备连接时显示前台通知，断开后自动移除
- 记录每次使用的开始时间、结束时间、时长、备注和播放来源
- 首页展示今日条带、当前连接状态、电量、今日/本周累计时长
- 日历支持月视图与周视图，并可展开查看某一天的详细时间轴
- 支持每日额度、睡眠时间不计入、自动分段
- 支持通知使用权读取当前播放音频的应用名称与标题
- 支持诊断日志导出与异常记录清理

## 技术栈

- Kotlin
- Jetpack Compose
- Room
- DataStore
- Foreground Service
- NotificationListenerService

## 运行要求

- Android 8.0+（`minSdk = 26`）
- `compileSdk = 35`
- Java 17
- Android Studio / Gradle 环境可正常构建 Android 项目

## 首次使用

1. 安装应用并授予蓝牙连接权限。
2. Android 13+ 允许通知权限。
3. 在设备选择页勾选要监控的已配对蓝牙耳机。
4. 如需显示当前播放音频来源，到设置页开启“通知使用权”。
5. 如需开机恢复监控，到设置页打开“开机自启动”。
6. 按需设置每日额度与睡眠时间段。

## 构建

Windows 下可直接使用 Gradle Wrapper：

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat assembleRelease
```

生成产物默认位于：

- `app/build/outputs/apk/debug/app-debug.apk`
- `app/build/outputs/apk/release/app-release.apk`

## 项目结构

```text
app/src/main/java/com/example/bluetoothusage/
├─ bluetooth/    蓝牙连接广播、状态检查
├─ data/         Room 实体、DAO、数据库
├─ repository/   DataStore 设置与使用记录仓库
├─ service/      监控前台服务、通知监听服务、开机恢复接收器
├─ ui/           Compose 页面与可视化组件
├─ viewmodel/    页面状态与业务编排
├─ BluetoothUsageApp.kt
└─ MainActivity.kt
```

## 权限与系统能力

Manifest 中已声明以下关键权限：

- `BLUETOOTH`
- `BLUETOOTH_ADMIN`
- `BLUETOOTH_CONNECT`
- `POST_NOTIFICATIONS`
- `FOREGROUND_SERVICE`
- `FOREGROUND_SERVICE_CONNECTED_DEVICE`
- `RECEIVE_BOOT_COMPLETED`

额外说明：

- 当前播放音频来源依赖 `NotificationListenerService`，需要用户手动授权。
- 开机恢复监控依赖 `BOOT_COMPLETED`、蓝牙状态广播和定时心跳保活。
- Android 14+ 前台服务类型使用 `connectedDevice`。

## 数据说明

使用记录保存在本地 Room 数据库中，核心字段包括：

- 设备名
- 设备地址
- 开始时间
- 结束时间
- 持续时长
- 备注
- 音频应用包名 / 名称
- 媒体标题快照

统计逻辑会按日期窗口与睡眠窗口交集计算，避免跨天记录直接整段计入某一天。

## 维护与诊断

设置页内置两类维护能力：

- 导出诊断日志：导出当前设备、会话、统计与历史记录摘要
- 清理异常记录：删除非目标设备记录、超短噪声记录和近似重复记录

## 当前构建说明

- `release` 构建当前仍使用 `debug` 签名配置，便于本地安装和测试。
- 如果要发布正式版本，建议替换为独立的 release keystore，并补充混淆、版本管理和发布流程。

## 仓库说明

这个仓库当前更适合作为个人使用和持续迭代的 Android 工程，而不是已经完全产品化的发布项目。  
如果你准备继续扩展，优先建议从以下方向入手：

- 真机适配与耗电优化
- 更稳健的国产系统后台保活策略
- 正式发布签名与版本管理
- 可视化与交互性能优化
