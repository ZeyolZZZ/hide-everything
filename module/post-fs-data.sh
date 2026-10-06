#!/system/bin/sh
MODDIR=${0%/*}
LOG=/data/local/tmp/hide_everything.log
CONFDIR=/data/adb/hide_everything

# ---------- ① 开机失败自动禁用（计数式）----------
CNT=/data/adb/hide_everything_bootcnt
N=$(cat "$CNT" 2>/dev/null || echo 0)
case "$N" in ''|*[!0-9]*) N=0 ;; esac
N=$((N+1)); echo "$N" > "$CNT"
if [ "$N" -ge 3 ]; then
  touch "$MODDIR/disable"
  echo "$(date) 连续 $N 次未完成启动，已自动禁用" >> $LOG
  exit 0
fi

# ---------- ② 清除残留（默认关闭，需 WebUI 开启）----------
[ -f "$CONFDIR/cleanup.enabled" ] || exit 0
while read -r p; do
  case "$p" in ''|'#'*) continue ;; esac
  if [ -e "$p" ]; then
    rm -rf "$p" 2>/dev/null && echo "$(date) removed $p" >> $LOG
  fi
done < "$MODDIR/cleanup.list"
