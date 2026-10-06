package com.zeyol.hideeverything;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 放行规则：目标应用只能看到
 *   ① 系统基础组件 = system/updated-system 且【没有启动图标】
 *   ② 自身
 *   ③ 配置里的 allow: 白名单
 *
 * 判断"有无启动图标"需要查 LAUNCHER intent，而那次查询会经过我们自己的钩子。
 * 用线程局部 BYPASS 让我们自己的查询拿到未过滤的真实结果。
 */
public final class Rules {
    private static final int FLAG_SYSTEM = 1;
    private static final int FLAG_UPDATED_SYSTEM_APP = 1 << 7;

    private static final ThreadLocal<Boolean> BYPASS = ThreadLocal.withInitial(() -> Boolean.FALSE);
    public static boolean bypassing() { return Boolean.TRUE.equals(BYPASS.get()); }

    private static volatile Set<String> launcherPkgs = null;

    /** 一次算好"有启动图标"的包集合（用未过滤的真实查询）。 */
    private static Set<String> launchers(Context ctx) {
        Set<String> cached = launcherPkgs;
        if (cached != null) return cached;
        Set<String> s = new HashSet<>();
        if (ctx != null) {
            BYPASS.set(Boolean.TRUE);
            try {
                Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
                for (ResolveInfo ri : ctx.getPackageManager().queryIntentActivities(i, 0)) {
                    if (ri.activityInfo != null) s.add(ri.activityInfo.packageName);
                }
            } catch (Throwable ignored) {
            } finally {
                BYPASS.set(Boolean.FALSE);
            }
        }
        launcherPkgs = Collections.unmodifiableSet(s);
        return launcherPkgs;
    }

    public static void reset() { launcherPkgs = null; }

    private static boolean isSystem(ApplicationInfo ai) {
        return ai != null && ((ai.flags & FLAG_SYSTEM) != 0 || (ai.flags & FLAG_UPDATED_SYSTEM_APP) != 0);
    }

    public static boolean allow(String pkg, String self, Context ctx) {
        if (pkg == null) return false;
        if (pkg.equals(self)) return true;                                  // 自身
        if (Config.targets().contains("allow:" + pkg)) return true;          // 显式白名单
        if (bypassing()) return true;                                        // 我们自己的探测查询
        return !launchers(ctx).contains(pkg);                                // 无启动图标者视为系统基础组件
    }

    public static boolean allowInfo(ApplicationInfo ai, String self, Context ctx) {
        if (ai == null) return false;
        if (ai.packageName.equals(self)) return true;
        if (Config.targets().contains("allow:" + ai.packageName)) return true;
        if (bypassing()) return true;
        if (!isSystem(ai)) return false;                                     // 非系统应用一律隐藏
        return !launchers(ctx).contains(ai.packageName);                     // 系统且无图标 = 基础组件
    }

    public static boolean allowPkgInfo(PackageInfo pi, String self, Context ctx) {
        return pi != null && allowInfo(pi.applicationInfo, self, ctx);
    }

    /** 兼容旧调用点。 */
    public static boolean allow(String pkg, String self, boolean ignored) {
        return allow(pkg, self, null);
    }
    public static boolean allowInfo(ApplicationInfo ai, String self) {
        return allowInfo(ai, self, null);
    }
    public static boolean allowPkgInfo(PackageInfo pi, String self) {
        return allowPkgInfo(pi, self, null);
    }
}
