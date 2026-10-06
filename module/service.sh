#!/system/bin/sh
MODDIR=${0%/*}
i=0
while [ "$(getprop sys.boot_completed)" != "1" ] && [ $i -lt 120 ]; do sleep 2; i=$((i+1)); done
if [ "$(getprop sys.boot_completed)" = "1" ]; then
  rm -f /data/adb/hide_everything_bootcnt
  echo "$(date) boot ok, 计数器已清零" >> /data/local/tmp/hide_everything.log
fi
