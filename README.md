# 健身日程

> 纯本地、离线的健身计划与训练记录 App。
> 不用注册账号，不联网，所有数据只存在你自己的手机上。

「健身日程」帮你把训练计划编排好、排到日程上，练的时候逐组记录，练完用图表回看进步。

<!-- 截图：把 PNG 放进 docs/images/ 后，取消下面注释即可 -->
<!--
| 今日 | 训练日历 | 训练记录 | 统计 |
|---|---|---|---|
| ![今日](docs/images/today.png) | ![日历](docs/images/calendar.png) | ![训练](docs/images/workout.png) | ![统计](docs/images/stats.png) |
-->

## 下载安装

到 [Releases 页面](https://github.com/UnsleepingDawn/Dascle/releases/latest) 下载最新版的 `Dascle-<版本号>.apk`，在手机上点击安装即可。

- **系统要求**：Android 8.0（API 26）及以上。
- 首次安装需要在系统设置里允许「安装未知来源应用」。
- 首次打开会自动导入 60 条内置动作（覆盖胸、背、腿、臀、肩、手臂、腹），开箱可用。

> 也可以装上 [Obtainium](https://github.com/ImranR98/Obtainium)，订阅本仓库后自动接收新版本。

## 功能一览

| 入口 | 作用 |
|---|---|
| **今日** | 当天排了哪些计划、一键开始或继续训练 |
| **计划** | 训练日历 + 计划列表：编排动作、排日程 |
| **统计** | 容量趋势、训练频率、肌群分布、动作重量进步 |
| **我的** | 设置：训练提醒开关与提醒时间 |

- **训练日历**：按月查看每天的计划与实际训练，切月 / 回到本月，点某一天查看明细；可把计划排到某一天或移除这一天的排期。
- **计划编排**：新建 / 重命名 / 删除计划；从动作库按肌群与器械筛选动作；拖拽排序；为每个动作设置目标组数、次数 / 时长、默认重量与组间休息。
- **训练记录**：逐组录入重量与次数（计时动作按秒记录），勾选完成即自动开始组间休息倒计时并在到点时震动；可临时加 / 减组、跳过动作、加练计划外动作、写备注。中途退出保留进度，下次从「今日」继续。
- **训练完成汇总**：完成组数、总容量（kg）与用时。
- **桌面组件**「今日训练」：在桌面直接看到今天练什么、练到哪了。
- **训练提醒**：到点提醒当天的训练安排；当天已经练过或没有排期时不会打扰（通知与「闹钟与提醒」权限未授予时会降级并提示）。
- **漏练提醒**：一段时间没打开 App、期间排了训练却没记录时，打开 App 会先问一句这些没练的计划是**顺延**还是**跳过**（也可以选「健身了，没写上去」只关掉提醒）。

## 隐私

- 没有 `INTERNET` 权限，不带任何网络库，不存在账号与云同步。
- 数据全部保存在应用私有目录的 `fitplan.db` 中（设备上的 `/data/data/com.fitplan.app/databases/`）。
- 卸载应用会一并清空数据。

## 从源码构建

### 环境要求

| 项 | 要求 |
|---|---|
| JDK | 21（Kotlin 编译目标为 17） |
| Android SDK | compileSdk 37.1 / targetSdk 36 / minSdk 26 |
| Git | 必须在 `PATH` 中——构建脚本会用 `git` 注入版本号 |

在仓库根目录创建 `local.properties`（已被 gitignore，不会提交）：

```properties
sdk.dir=D\:\\Install0\\Android2\\Sdk
```

### 常用命令

Windows 用 `.\gradlew.bat`，macOS / Linux 用 `./gradlew`。

```bash
./gradlew spotlessApply              # 自动格式化
./gradlew spotlessCheck              # 检查格式
./gradlew testDebugUnitTest          # 单元测试
./gradlew verifySqlDelightMigration  # 校验数据库迁移
./gradlew assembleDebug              # 产出 app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:installDebug          # 构建并安装到已连接设备
```

debug 与 release 的 `applicationId` 不同（debug 带 `.dev` 后缀），可以共存安装，数据互不相通。
正式发版（签名、打 tag、建 Release）见[发布文档](docs/发布文档.md)。

## 技术栈

Kotlin + Jetpack Compose + Material3 Expressive；Metro 依赖注入、SQLDelight 数据库、Voyager 导航、
Vico 图表、Glance 桌面组件。版本统一锁在 `gradle/*.versions.toml`。

## 项目结构

```
:app                  入口、打包、业务 UI 与 ScreenModel
:presentation-core    与业务无关的共享 Compose 组件与主题
:presentation-widget  Glance 桌面组件与 Vico 图表封装
:domain               领域模型、repository 接口、interactor
:data                 SQLDelight schema、repository 实现、种子导入
:core:common          偏好存储与工具扩展
:core:metro           DI 基建
```

依赖方向单向，下层不依赖上层；`:domain` 不依赖 Android UI。

## 文档

- [使用文档](docs/使用文档.md)：面向使用者，各页面怎么用
- [开发文档](docs/开发文档.md)：面向开发者，架构、模块、数据表、当前实现状态
- [部署文档](docs/部署文档.md)：构建、签名、安装到设备
- [发布文档](docs/发布文档.md)：在 GitHub 上发版的完整流程
