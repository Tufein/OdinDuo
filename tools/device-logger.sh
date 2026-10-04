#!/system/bin/sh
base=/data/local/tmp/retroid-touch-logs
mkdir -p "$base"
run="$base/raw-$(date +%Y%m%d-%H%M%S)"
mkdir -p "$run"
echo "$run" > "$base/latest"
echo $$ > "$base/logger.pid"
getprop > "$run/getprop.txt"
logcat -b all -v threadtime -T 1 -f "$run/logcat.txt" -r 4096 -n 3 &
logpid=$!
rawpid=
stamp_pid=
fifo=
rawnode=
stop_raw() {
  [ -z "$rawpid" ] || kill "$rawpid" 2>/dev/null
  [ -z "$stamp_pid" ] || kill "$stamp_pid" 2>/dev/null
  [ -z "$fifo" ] || rm -f "$fifo"
  rawpid=
  stamp_pid=
  fifo=
}
cleanup() {
  kill "$logpid" 2>/dev/null
  stop_raw
  if [ "$(cat "$base/logger.pid" 2>/dev/null)" = "$$" ]; then rm -f "$base/logger.pid"; fi
}
trap cleanup EXIT
trap 'exit 0' HUP INT TERM
n=0
while [ "$n" -lt 900 ]; do
  stamp=$(date +%s)
  { date +%s; cat /proc/uptime; } > "$run/time-$stamp.txt"
  dumpsys input > "$run/input-$stamp.txt"
  dumpsys display > "$run/display-$stamp.txt"
  cat /proc/bus/input/devices > "$run/devices-$stamp.txt"
  {
    for dev in /sys/bus/usb/devices/*; do
      [ -f "$dev/idVendor" ] || continue
      echo "DEVICE $dev"
      for field in idVendor idProduct product power/control power/runtime_status power/autosuspend_delay_ms power/wakeup; do
        printf '%s: ' "$field"
        value=$(cat "$dev/$field" 2>/dev/null) && echo "$value" || echo '<unreadable>'
      done
    done
    echo "RAW_READER_PID=$rawpid"
    if [ -n "$rawpid" ] && kill -0 "$rawpid" 2>/dev/null; then echo RAW_READER_ALIVE; else echo RAW_READER_ABSENT; fi
  } > "$run/usb-power-$stamp.txt"
  node=
  for entry in /sys/class/input/event*; do
    name=$(cat "$entry/device/name" 2>/dev/null)
    case "$name" in
      *Retroid*Touch*) node=/dev/input/${entry##*/}; break ;;
    esac
  done
  if [ "$node" != "$rawnode" ] || { [ -n "$node" ] && ! kill -0 "$rawpid" 2>/dev/null; }; then
    stop_raw
    rawnode="$node"
    echo "$stamp node=$node" >> "$run/raw-device-transitions.txt"
    if [ -n "$node" ]; then
      getevent -lp "$node" > "$run/raw-capabilities-$stamp.txt" 2>&1
      fifo="/data/local/tmp/retroid-raw-pipe-$$-$stamp"
      mkfifo "$fifo"
      while IFS= read -r event; do
        printf "%s %s\n" "$(date +%s)" "$event"
      done < "$fifo" > "$run/raw-events-$stamp.txt" 2>&1 &
      stamp_pid=$!
      getevent -lt "$node" > "$fifo" 2> "$run/raw-errors-$stamp.txt" &
      rawpid=$!
    fi
  fi
  sleep 2
  n=$((n + 1))
done
