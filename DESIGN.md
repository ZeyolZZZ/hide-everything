# 隐藏模块设计（RMX5080 / SukiSU + Zygisk Next）

目标：目标应用只能看到「系统基础组件 + 自身」，且看不到 NoActive/MT2/ADB 痕迹。
层面划分：**路径痕迹走内核(SUSFS SUS_PATH)；应用列表必须走用户态(App 进程内钩子)**。

## 一、为什么应用列表不能在内核做

  App → PackageManager.getInstalledPackages()
      → Binder → system_server(PackageManagerService) → 返回列表
内核只看到 binder 事务，看不到清单内容。内核只能过滤 /data/app 的 getdents，
而 App 从不这样枚举（且 SELinux 已挡）。→ 必须在目标 App 进程内挂钩。

## 二、Java 层钩子清单（按覆盖度排序）

### A. 包列表枚举（必钩）
- ApplicationPackageManager.getInstalledPackages(int)            → ParceledListSlice
- ApplicationPackageManager.getInstalledPackagesAsUser(int,int)
- ApplicationPackageManager.getInstalledApplications(int)
- ApplicationPackageManager.getInstalledApplicationsAsUser(int,int)
- ApplicationPackageManager.getInstalledModules(int)             (API 29+)
- ApplicationPackageManager.getPackagesForUid(int)               → String[]
- ApplicationPackageManager.getPackagesHoldingPermissions(String[],int)
- ApplicationPackageManager.getSharedLibraries(int)

### B. Intent 解析枚举（必钩，最常被用作旁路）
- queryIntentActivities(Intent,int) / queryIntentActivitiesAsUser
- queryIntentServices / queryIntentServicesAsUser
- queryBroadcastReceivers / queryIntentContentProviders
- resolveActivity / resolveService
- queryIntentActivityOptions

### C. 单包查询（必须对未放行包抛 NameNotFoundException）
- getPackageInfo(String,int) / getPackageInfoAsUser
- getApplicationInfo(String,int) / getApplicationInfoAsUser
- getLaunchIntentForPackage / getLeanbackLaunchIntentForPackage
- getInstallerPackageName / getInstallSourceInfo
- getApplicationEnabledSetting / isPackageSuspended

### D. 隐藏的直连 Binder（HMA 类工具常漏）
- android.content.pm.IPackageManager$Stub$Proxy 上的同名方法
  (getInstalledPackages / getInstalledApplications / queryIntentActivities ...)
  说明：走 ApplicationPackageManager 一般够用，但 App 可反射拿 IPackageManager 直连，
        所以要在这层再兜一次。

### E. 其它可枚举应用的入口（旁路封堵）
- LauncherApps.getActivityList(String,UserHandle)
- ActivityManager.getRunningAppProcesses() / getRunningServices() / getRunningTasks()
- UsageStatsManager.queryUsageStats() / queryEvents()
- PackageManager.getPreferredActivities()
- MediaStore / ContentResolver 查询 (按 package 列)

## 三、原生/内核层旁路（SUS_PATH 与 SIGSYS 之外的部分）

- /proc/<pid>/cmdline、/proc/<pid>/comm   → 进程枚举会暴露其它 App。内核无法按调用者动态过滤，
                                            只能靠「目标应用看不到 pid」类策略，风险高，暂不做。
- /proc/net/unix                          → abstract socket 名含包名，可被读取。需用户态处理后置空。
- /data/system/NoActive                   → ✅ 已用 SUSFS SUS_PATH 隐藏（内核层）
- /sdcard/MT2 等                           → ✅ 同上
- Settings.Global.ADB_ENABLED             → 用户态按应用返回 0（系统与设置页不受影响）

## 四、放行规则

默认：目标应用能看到
  - 所有「系统基础组件」：无 LAUNCHER 图标 且 (FLAG_SYSTEM|FLAG_UPDATED_SYSTEM_APP) 的包
  - 自身包名
  - 若某包被显式加入白名单 → 也放行
其余一律从结果中剔除；单包查询则抛 NameNotFoundException。
