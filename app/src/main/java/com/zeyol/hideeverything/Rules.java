package com.zeyol.hideeverything;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;

/**
 * 放行规则：目标应用只能看到
 *   ① 系统基础组件（system / updated-system 且无启动图标）
 *   ② 自身
 *   ③ 配置里的显式白名单
 */
public final class Rules {
    private static final int FLAG_SYSTEM = 1;
    private static final int FLAG_UPDATED_SYSTEM_APP = 1 << 7;

    public static boolean allow(String pkg, String self, boolean hasLauncherIcon) {
        if (pkg == null) return false;
        if (pkg.equals(self)) return true;                       // 自身
        if (Config.targets().contains("allow:" + pkg)) return true; // 显式白名单
        if (pkg.startsWith("android") || pkg.equals("com.android.systemui")
                || pkg.equals("com.android.settings") || pkg.equals("com.google.android.gms")) {
            // 系统基础组件（再叠加"无启动图标"约束，避免放行用户可见应用）
            return !hasLauncherIcon;
        }
        return false;
    }

    public static boolean allowInfo(ApplicationInfo ai, String self) {
        if (ai == null) return false;
        int f = ai.flags;
        boolean system = (f & FLAG_SYSTEM) != 0 || (f & FLAG_UPDATED_SYSTEM_APP) != 0;
        if (!system && !ai.packageName.equals(self)
                && !Config.targets().contains("allow:" + ai.packageName)) return false;
        return allow(ai.packageName, self, false);
    }

    public static boolean allowPkgInfo(PackageInfo pi, String self) {
        return pi != null && allowInfo(pi.applicationInfo, self);
    }
}
