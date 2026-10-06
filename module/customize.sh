#!/system/bin/sh
SKIPUNZIP=0
ui_print "- 安装 Hide Everything"
mkdir -p /data/adb/hide_everything
[ -f /data/adb/hide_everything/config.json ] || echo '[]' > /data/adb/hide_everything/config.json
ui_print "- 安装 APK（LSPosed 模块）"
pm install -r "$MODPATH/HideEverything.apk" >/dev/null 2>&1 && ui_print "  APK 已安装" || ui_print "  APK 安装失败，请手动装 $MODPATH/HideEverything.apk"
ui_print "- 请在 LSPosed 中启用 Hide Everything 并勾选作用域"
