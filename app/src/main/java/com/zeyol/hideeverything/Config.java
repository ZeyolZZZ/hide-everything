package com.zeyol.hideeverything;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** 配置由 KSU WebUI 写到 /data/adb/hide_everything/config.json（每行一个包名，简单可靠）。 */
public final class Config {
    public static final String PATH = "/data/adb/hide_everything/config.json";
    private static volatile Set<String> targets = Collections.emptySet();
    private static volatile long loadedAt = 0;

    public static Set<String> targets() {
        if (System.currentTimeMillis() - loadedAt > 5000) reload();
        return targets;
    }

    public static boolean isTarget(String pkg) {
        return pkg != null && targets().contains(pkg);
    }

    private static synchronized void reload() {
        loadedAt = System.currentTimeMillis();
        Set<String> s = new HashSet<>();
        try (FileInputStream in = new FileInputStream(new File(PATH))) {
            byte[] buf = new byte[(int) new File(PATH).length()];
            int n = in.read(buf);
            if (n > 0) {
                String text = new String(buf, 0, n, StandardCharsets.UTF_8);
                // 支持 JSON 数组或纯文本：抓出所有 xxx.yyy 形式的包名
                for (String line : text.split("[^A-Za-z0-9_.]+")) {
                    if (line.indexOf('.') > 0 && line.length() > 3) s.add(line);
                }
            }
        } catch (Throwable ignored) { }
        targets = s;
    }
}
