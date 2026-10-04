#!/system/bin/sh
# Experimental one-shot recovery. Run before replacing the Mac cable with RDS.
# Waits 90 seconds after RDS is detected, then requests one USB-port reset.
base=/data/local/tmp/retroid-touch-logs
mkdir -p "$base"
log="$base/usb-recovery-$(date +%Y%m%d-%H%M%S).txt"
exec >"$log" 2>&1
echo "Waiting for RDS: $(date)"
n=0
while ! grep -q 'RetroidPocket RDS Touchscreen' /proc/bus/input/devices; do
  sleep 2
  n=$((n+1))
  [ "$n" -lt 90 ] || { echo 'No RDS found; exiting'; exit 1; }
done
echo "RDS detected: $(date); reset in 90 seconds"
sleep 90
grep -q 'RetroidPocket RDS Touchscreen' /proc/bus/input/devices || { echo 'RDS disconnected; skipping reset'; exit 1; }
dumpsys usb > "$log.before-usb"
echo "Requesting one USB port reset: $(date)"
svc usb resetUsbPort
sleep 5
dumpsys usb > "$log.after-usb"
echo "Finished: $(date)"
