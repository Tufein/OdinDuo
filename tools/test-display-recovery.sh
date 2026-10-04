#!/system/bin/sh
base=/data/local/tmp/retroid-touch-logs
log="$base/display-recovery-$(date +%Y%m%d-%H%M%S).txt"
mkdir -p "$base"
exec >"$log" 2>&1
echo "Waiting for an external display and OFF -> ON transition: $(date)"
seen_off=0
n=0
while [ "$n" -lt 900 ]; do
  dumpsys display > /data/local/tmp/retroid-display-watch.txt
  line=$(grep 'DisplayDeviceInfo{' /data/local/tmp/retroid-display-watch.txt | grep 'type EXTERNAL' | head -1)
  case "$line" in *'state OFF,'*) seen_off=1 ;; esac
  if [ "$seen_off" = 1 ]; then
    case "$line" in
      *'state ON,'*)
        sleep 5
        id=$(cmd display get-displays --ids-only --type external | tr -cd '0-9\n' | head -1)
        case "$id" in ''|0) echo "No safe external display ID: $id"; exit 1 ;; esac
        echo "External display $id; cycling once: $(date)"
        cmd display disable-display "$id"
        sleep 1
        cmd display enable-display "$id"
        echo "Enable requested: $(date)"
        exit 0
        ;;
    esac
  fi
  sleep 2
  n=$((n+1))
done
echo 'No sleep/wake transition within thirty minutes; exiting'
