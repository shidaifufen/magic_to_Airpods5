# MagicToAirPods5

让 **荣耀 MagicOS** 的控制中心「设备互联中心」支持 **AirPods 5**：显示耳机电量、噪声控制、查找耳机。

> 适用：荣耀 MagicOS 11（实测机型 LDY-AN00 / 荣耀 Magic8 Pro Air / 天玑 9500）
> 依赖：KernelSU/Magisk + Zygisk Next + LSPosed 2.x（API 102）

---

## 功能

- ✅ AirPods 5 在荣耀控制中心「设备互联中心」显示为受支持的 Apple 耳机
- ✅ 左右耳 + 充电盒电量
- ✅ 噪声控制（降噪/通透/关闭）入口
- ✅ 查找耳机入口
- 不修改系统分区、不改电池/充电逻辑，随时可卸载还原

## 原理

荣耀的原生 AirPods 支持由「智慧空间」`com.hihonor.magichome` 实现。其中的
`com.hihonor.magichome.devicemanager.data.AppleAirDevManager` 会用
`isSupportAirPodsDevice(deviceArr, prodIdList)` 判断耳机型号是否在其**云端下发**的
产品白名单 `prodIdList` 里（本地缓存于 `batteryServices.db` 的 `batteryConfig` 表）。

MagicOS 11.0.0.110 的白名单包含 AirPods 1/2/3/4/Pro 1/Pro 2/Pro 2 USB-C/Pro 3/Max，
**不包含 AirPods 5**，所以整条 AirPods 处理链路被跳过，控制中心就没有耳机卡片。

本模块通过 LSPosed hook 把 `AppleAirDevManager` 中返回 boolean 的判定方法
（0 参数 / 单 String 参数）强制返回 `true`，从而绕过型号白名单。

相关日志（过滤 tag `MagicToAirPods5`）：

```
onPackageLoaded com.hihonor.magichome
hooked p ([Ljava.lang.Class;@...)
AirDevManager hooks installed, count=5
force true: o
```

## 安装

1. 安装本仓库 `release/MagicToAirPods5-v1.0.apk`；
2. 打开 LSPosed 管理器 → 模块 → 启用 **MagicToAirPods5**；
3. 作用域只勾选 **荣耀智慧空间**（`com.hihonor.magichome`）；
4. 重启「智慧空间」进程（或重启手机）后生效；
5. 连接 AirPods 5，打开控制中心 → 设备互联中心查看。

## 已适配环境

| 项目 | 版本 |
|---|---|
| 设备 | LDY-AN00（荣耀 Magic8 Pro Air） |
| 系统 | MagicOS 11.0.0.110 (SP7C00E110R103P1) / Android 17 |
| 智慧空间 | 10.60.08.300 |
| LSPosed | 2.2.1 (API 102) + Zygisk Next 1.3.4 |
| Root | KernelSU Next |

不同版本的「智慧空间」中该类的方法名可能不同。本模块会在运行时遍历
`AppleAirDevManager` 的所有 boolean 方法并 hook（不依赖具体混淆名），
若未来版本改类名/包名则需更新 `TARGET_CLS`。

## 风险与免责

- 只 hook 荣耀智慧空间内部的 AirPods 判定方法，**不修改系统文件**，卸载即还原；
- 强制 boolean 判定可能使其他 Apple 型号也被当作"受支持"——实测 AirPods Pro 2/Pro 3 仍正常；
- 本项目与荣耀、Apple 均无关联；使用风险自负。

## 构建

需要：JDK 17、Android SDK（build-tools 36、platform android-36）、
[libxposed api 102](https://central.sonatype.com/artifact/io.github.libxposed/api)（compileOnly）。

```powershell
# 1) 下载依赖并解压 libxposed：
#    https://repo1.maven.org/maven2/io/github/libxposed/api/102.0.0/api-102.0.0.aar
#    解压出 classes.jar 作为编译 classpath
# 2) 执行 build.ps1（见仓库根目录），产物在 release/
```

签名密钥：`keystore/magic2airpods5.keystore`（口令 `android`），
便于二次构建保持同一签名以覆盖安装。
