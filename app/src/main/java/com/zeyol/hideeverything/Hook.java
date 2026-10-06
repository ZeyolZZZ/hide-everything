package com.zeyol.hideeverything;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * 目标应用进程内，让 PackageManager 只返回「系统基础组件 + 自身 + 白名单」。
 * 覆盖：包列表枚举 / Intent 解析 / 单包查询 / Binder 直连 / Settings(ADB)。
 */
public class Hook implements IXposedHookLoadPackage {
    private static final String TAG = "HideEverything";
    private static final String PM = "android.app.ApplicationPackageManager";
    private static final String PMP = "android.content.pm.IPackageManager$Stub$Proxy";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) {
        if (!Config.isTarget(lp.packageName)) return;
        final String self = lp.packageName;
        XposedBridge.log(TAG + ": 已注入 " + self);

        // ---------- A. 包列表枚举：过滤 List ----------
        String[] lists = {
            "getInstalledPackages", "getInstalledApplications",
            "getInstalledPackagesAsUser", "getInstalledApplicationsAsUser",
            "getInstalledModules", "getPackagesHoldingPermissions", "getSharedLibraries",
        };
        for (String m : lists) hookList(lp, PM, m, self);
        for (String m : lists) hookList(lp, PMP, m, self);

        // ---------- B. Intent 解析：过滤 List<ResolveInfo> ----------
        String[] intents = {
            "queryIntentActivities", "queryIntentActivitiesAsUser",
            "queryIntentServices", "queryIntentServicesAsUser",
            "queryBroadcastReceivers", "queryIntentContentProviders",
            "queryIntentActivityOptions",
        };
        for (String m : intents) hookResolve(lp, PM, m, self);
        for (String m : intents) hookResolve(lp, PMP, m, self);

        // ---------- C. 单包查询：未放行则抛 NameNotFoundException ----------
        String[] singles = {
            "getPackageInfo", "getPackageInfoAsUser",
            "getApplicationInfo", "getApplicationInfoAsUser",
            "getInstallSourceInfo", "getInstallSourceInfoAsUser",
        };
        for (String m : singles) hookSingle(lp, PM, m, self);
        for (String m : singles) hookSingle(lp, PMP, m, self);

        // ---------- D. getPackagesForUid ----------
        hookPackagesForUid(lp, PM, self);
        hookPackagesForUid(lp, PMP, self);

        // ---------- E. 按应用欺骗 ADB_ENABLED ----------
        hookAdbSetting(lp);
    }

    /* ---------------- 通用工具 ---------------- */

    private boolean allowed(Object obj, String self) {
        if (obj instanceof PackageInfo) return Rules.allowPkgInfo((PackageInfo) obj, self);
        if (obj instanceof ApplicationInfo) return Rules.allowInfo((ApplicationInfo) obj, self);
        if (obj instanceof ResolveInfo) {
            ResolveInfo ri = (ResolveInfo) obj;
            String pkg = ri.activityInfo != null ? ri.activityInfo.packageName
                    : ri.serviceInfo != null ? ri.serviceInfo.packageName
                    : ri.providerInfo != null ? ri.providerInfo.packageName
                    : ri.filter != null ? null : null;
            return pkg == null || Rules.allow(pkg, self, true);
        }
        return true;
    }

    private void hookList(XC_LoadPackage.LoadPackageParam lp, String cls, String method, String self) {
        try {
            XposedHelpers.findAndHookMethod(cls, lp.classLoader, method, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (Rules.bypassing()) return;
                    Object r = p.getResult();
                    if (!(r instanceof List)) return;
                    List<?> in = (List<?>) r;
                    List<Object> out = new ArrayList<>(in.size());
                    for (Object o : in) if (allowed(o, self)) out.add(o);
                    if (out.size() != in.size()) p.setResult(out);
                }
            });
        } catch (Throwable ignored) { }
    }

    private void hookResolve(XC_LoadPackage.LoadPackageParam lp, String cls, String method, String self) {
        try {
            XposedHelpers.findAndHookMethod(cls, lp.classLoader, method, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (Rules.bypassing()) return;
                    Object r = p.getResult();
                    if (!(r instanceof List)) return;
                    List<?> in = (List<?>) r;
                    List<Object> out = new ArrayList<>(in.size());
                    for (Object o : in) if (allowed(o, self)) out.add(o);
                    if (out.size() != in.size()) p.setResult(new java.util.ArrayList<>(out));
                }
            });
        } catch (Throwable ignored) { }
    }

    private void hookSingle(XC_LoadPackage.LoadPackageParam lp, String cls, String method, String self) {
        try {
            for (Method m : XposedHelpers.findClass(cls, lp.classLoader).getDeclaredMethods()) {
                if (!m.getName().equals(method)) continue;
                Class<?>[] pt = m.getParameterTypes();
                if (pt.length < 1 || pt[0] != String.class) continue;
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam p) throws Throwable {
                        String pkg = (String) p.args[0];
                        if (pkg == null || Rules.allow(pkg, self, false)) return;
                        throw new PackageManager.NameNotFoundException(pkg);
                    }
                });
            }
        } catch (Throwable ignored) { }
    }

    private void hookPackagesForUid(XC_LoadPackage.LoadPackageParam lp, String cls, String self) {
        try {
            XposedHelpers.findAndHookMethod(cls, lp.classLoader, "getPackagesForUid", int.class,
                new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam p) {
                        Object r = p.getResult();
                        if (!(r instanceof String[])) return;
                        List<String> out = new ArrayList<>();
                        for (String s : (String[]) r) if (Rules.allow(s, self, false)) out.add(s);
                        p.setResult(out.toArray(new String[0]));
                    }
                });
        } catch (Throwable ignored) { }
    }

    /** 目标应用读 Settings.Global.ADB_ENABLED 时返回 0；系统与设置页不受影响。 */
    private void hookAdbSetting(XC_LoadPackage.LoadPackageParam lp) {
        try {
            XposedHelpers.findAndHookMethod("android.provider.Settings$Global", lp.classLoader,
                "getInt", android.content.ContentResolver.class, String.class, int.class,
                new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam p) {
                        if ("adb_enabled".equals(p.args[1])) p.setResult(0);
                    }
                });
        } catch (Throwable ignored) { }
        try {
            XposedHelpers.findAndHookMethod("android.provider.Settings$Global", lp.classLoader,
                "getString", android.content.ContentResolver.class, String.class,
                new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam p) {
                        if ("adb_enabled".equals(p.args[1])) p.setResult("0");
                    }
                });
        } catch (Throwable ignored) { }
    }
}
