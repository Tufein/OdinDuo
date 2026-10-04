#!/system/bin/sh
# Read-only diagnosis. The caller redirects output to our own log directory.
chmod 0644 /data/local/tmp/retroid-touch-logs/pserver-probe.txt
date +%s
id
for directory in /sys/devices/platform/soc/a600000.ssusb /sys/devices/platform/soc/a600000.ssusb/a600000.dwc3 /sys/bus/usb/devices/*; do
  [ -d "$directory/power" ] || continue
  echo "DEVICE $directory"
  for field in idVendor idProduct power/control power/runtime_status power/autosuspend_delay_ms power/wakeup; do
    [ -f "$directory/$field" ] || continue
    printf '%s: ' "$field"
    cat "$directory/$field"
  done
done
