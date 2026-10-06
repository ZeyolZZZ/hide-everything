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
                // 支持 JSON 数组或纯文本。必须保留 "allow:" 前缀，否则白名单会失效。
                for (String tok : text.split("[^A-Za-z0-9_.:]+")) {
                    if (tok.isEmpty()) continue;
                    String body = tok.startsWith("allow:") ? tok.substring(6) : tok;
                    if (body.indexOf('.') > 0 && body.length() > 3) s.add(tok);
                }
            }
        } catch (Throwable ignored) { }
        targets = s;
    }
}
