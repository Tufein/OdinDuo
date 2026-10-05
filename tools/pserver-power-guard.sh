#!/system/bin/sh
# OdinDuo: RDS-only runtime power protection, restored when the session ends.
base=/data/local/tmp/retroid-touch-logs
state=/data/local/tmp/retroid-power-guard-state
script=$(readlink -f "$0")
duration=1200
owner_pid=
owner_start=
owner_uid=
owner_token=
owner_missing_until=0
restart_grace=60
log() { echo "$(date +%s) $*"; }
clock_seconds() { read seconds unused < /proc/uptime; echo "${seconds%%.*}"; }
owner_alive() {
  [ -z "$owner_pid" ] && return 0
  [ -r "/proc/$owner_pid/stat" ] || return 1
  stat=$(cat "/proc/$owner_pid/stat" 2>/dev/null) || return 1
  stat=${stat##*) }
  start=$(printf '%s\n' "$stat" | awk '{print $20}')
  [ "$start" = "$owner_start" ] || return 1
  uid=$(awk '/^Uid:/{print $2}' "/proc/$owner_pid/status" 2>/dev/null)
  [ "$uid" = "$owner_uid" ] || return 1
  [ "$(cat /data/user/0/nl.retroid.touchguard/files/guard-enabled 2>/dev/null)" = "$owner_token" ] || return 1
  name=$(tr '\000' '\n' < "/proc/$owner_pid/cmdline" 2>/dev/null | head -n 1)
  case "$name" in nl.retroid.touchguard|nl.retroid.touchguard:guard) return 0 ;; *) return 1 ;; esac
}
refresh_owner() {
  [ -z "$owner_pid" ] && return 0
  owner_file=/data/user/0/nl.retroid.touchguard/files/guard-owner
  [ -r "$owner_file" ] || return 1
  { IFS= read -r next_pid; IFS= read -r next_start; IFS= read -r next_uid; IFS= read -r next_token; } < "$owner_file" || return 1
  for value in "$next_pid" "$next_start" "$next_uid"; do
    case "$value" in ''|*[!0-9]*) return 1 ;; esac
  done
  [ "$next_uid" = "$owner_uid" ] && [ "$next_token" = "$owner_token" ] || return 1
  owner_pid=$next_pid
  owner_start=$next_start
}
session_valid() {
  [ "$end" = 0 ] || [ "$(clock_seconds)" -lt "$end" ] || return 1
  [ -z "$owner_pid" ] && return 0
  # Stop is immediate. Only a missing service process receives a bounded restart grace.
  [ "$(cat /data/user/0/nl.retroid.touchguard/files/guard-enabled 2>/dev/null)" = "$owner_token" ] || return 1
  if refresh_owner && owner_alive; then
    [ "$owner_missing_until" = 0 ] || log "OWNER resumed pid=$owner_pid"
    owner_missing_until=0
    return 0
  fi
  now=$(clock_seconds)
  if [ "$owner_missing_until" = 0 ]; then
    owner_missing_until=$((now + restart_grace))
    log "OWNER missing; restart grace=${restart_grace}s"
  fi
  [ "$now" -lt "$owner_missing_until" ]
}
heartbeat() {
  [ -z "$owner_pid" ] || printf '%s %s\n' "$owner_token" "$(clock_seconds)" > "$base/guard-heartbeat"
}
find_rds() {
  for candidate in /sys/bus/usb/devices/*; do
    [ "$(cat "$candidate/idVendor" 2>/dev/null)" = 222a ] || continue
    [ "$(cat "$candidate/idProduct" 2>/dev/null)" = 0001 ] || continue
    readlink -f "$candidate"
    return 0
  done
  return 1
}
valid_control() {
  case "$1" in
    /sys/devices/platform/soc/a600000.ssusb/power/control|/sys/devices/platform/soc/a600000.ssusb/*/power/control) return 0 ;;
    *) return 1 ;;
  esac
}
restore() {
  # Walk snapshots in reverse order; do not restore an unrelated replacement USB device.
  remaining=$(cat "$state/count" 2>/dev/null)
  case "$remaining" in ''|*[!0-9]*) remaining=0 ;; esac
  while [ "$remaining" -gt 0 ]; do
    remaining=$((remaining - 1))
    entry="$state/saved-$remaining"
    [ -f "$entry" ] || continue
    { IFS= read -r control; IFS= read -r original; IFS= read -r identity; } < "$entry"
    valid_control "$control" || { log "RESTORE rejected unexpected path"; restore_failed=1; continue; }
    [ -f "$control" ] || continue
    if [ "$identity" != parent ]; then
      directory=${control%/power/control}
      current_identity="$(cat "$directory/idVendor" 2>/dev/null):$(cat "$directory/idProduct" 2>/dev/null):$(cat "$directory/devnum" 2>/dev/null)"
      [ "$current_identity" = "$identity" ] || { log "RESTORE skip replaced device $directory"; continue; }
    fi
    current=$(cat "$control" 2>/dev/null) || { log "RESTORE FAILED reading $control"; restore_failed=1; continue; }
    [ "$current" = on ] || { log "RESTORE skip changed value $control=$current"; continue; }
    case "$original" in on|auto) ;; *) log "RESTORE FAILED invalid original"; restore_failed=1; continue ;; esac
    if printf '%s\n' "$original" > "$control"; then
      restored=$(cat "$control" 2>/dev/null)
      if [ "$restored" = "$original" ]; then
        log "RESTORE $control=$original readback=$restored"
      else
        log "RESTORE FAILED readback $control expected=$original actual=$restored"
        restore_failed=1
      fi
    else
      log "RESTORE FAILED $control"
      restore_failed=1
    fi
  done
}
stop_worker() {
  [ -d "$state" ] || return 0
  touch "$state/stop"
  worker=$(cat "$state/pid" 2>/dev/null)
  case "$worker" in ''|*[!0-9]*) worker= ;; esac
  if [ -n "$worker" ] && [ -r "/proc/$worker/cmdline" ]; then
    command=$(tr '\000' ' ' < "/proc/$worker/cmdline")
    case "$command" in *"$script --watch"*) kill -TERM "$worker" 2>/dev/null ;; esac
  fi
  # Normal signal handling restores from the worker; recover snapshots after a hard kill.
  sleep 2
  if [ -d "$state" ] && { [ -z "$worker" ] || ! kill -0 "$worker" 2>/dev/null; }; then
    restore_failed=0
    restore
    [ "$restore_failed" = 0 ] && rm -rf "$state"
  fi
}
case "$1" in
  --stop)
    stop_worker
    [ ! -d "$state" ] || { log 'RESTORE FAILED pending snapshots'; exit 1; }
    log 'STOP completed'
    exit 0
    ;;
  --watchdog)
    case "$2" in ''|*[!0-9]*) exit 2 ;; esac
    end=$2
    owner_pid=$3
    owner_start=$4
    owner_uid=$5
    owner_token=$6
    worker=$7
    while session_valid && kill -0 "$worker" 2>/dev/null; do sleep 1; done
    stop_worker
    log 'GUARD stopped (watchdog)'
    exit 0
    ;;
  --watch-app)
    owner_pid=$2
    owner_start=$3
    owner_uid=$4
    owner_token=$5
    for value in "$owner_pid" "$owner_start" "$owner_uid"; do
      case "$value" in ''|*[!0-9]*) echo 'Invalid app identity'; exit 2 ;; esac
    done
    case "$owner_token" in ''|*[!a-f0-9]*) echo 'Invalid session token'; exit 2 ;; esac
    [ "$owner_uid" -ge 10000 ] && owner_alive || { echo 'App identity verification failed'; exit 1; }
    base=/data/user/0/nl.retroid.touchguard/files
    duration=0
    ;;
  --watch) ;;
  *) echo 'Use --watch, --watch-app or --stop'; exit 2 ;;
esac
[ "$(id -u)" = 0 ] || { echo 'Requires the stock privileged AYN service'; exit 1; }
mkdir "$state" 2>/dev/null || { echo 'An experiment or pending restoration already exists'; exit 1; }
chmod 0700 "$state"
chmod 0644 "$base/power-guard.txt"
echo $$ > "$state/pid"
watchdog=
cleanup() {
  trap - EXIT HUP INT TERM
  [ -z "$watchdog" ] || kill "$watchdog" 2>/dev/null
  restore_failed=0
  restore
  if [ -n "$owner_token" ] && [ "$(cat "$base/guard-enabled" 2>/dev/null)" = "$owner_token" ]; then
    rm -f "$base/guard-enabled" "$base/guard-owner" "$base/guard-heartbeat"
  fi
  if [ "$restore_failed" = 0 ]; then rm -rf "$state"; else log "Restoration needs attention; snapshots retained in $state"; fi
  log 'GUARD stopped'
}
trap cleanup EXIT
trap 'exit 0' HUP INT TERM
end=0
[ "$duration" = 0 ] || end=$(( $(clock_seconds) + duration ))
# Independent root child can restore snapshots even if the worker is killed outright.
/system/bin/sh "$script" --watchdog "$end" "$owner_pid" "$owner_start" "$owner_uid" "$owner_token" "$$" </dev/null >> "$base/power-guard.txt" 2>&1 &
watchdog=$!
log "READY waiting for RDS; duration=${duration}s owner=$owner_pid worker=$$ watchdog=$watchdog"
rds=
while session_valid && [ ! -f "$state/stop" ]; do
  heartbeat
  rds=$(find_rds) && break
  sleep 1
done
[ -n "$rds" ] || { log 'No RDS connected before timeout/stop; no power changes'; exit 0; }
case "$rds" in /sys/devices/platform/soc/a600000.ssusb/*) ;; *) log 'Unexpected RDS topology; no power changes'; exit 1 ;; esac
number=$(cat "$rds/devnum")
log "RDS detected path=$rds devnum=$number"
# Save ancestors first so cleanup restores child first. The whitelist is this USB chain only.
chain=
directory=$rds
while [ "$directory" != /sys/devices/platform/soc ]; do
  [ ! -f "$directory/power/control" ] || chain="$directory $chain"
  directory=${directory%/*}
done
n=0
for directory in $chain; do
  control=$directory/power/control
  valid_control "$control" || exit 1
  original=$(cat "$control") || exit 1
  case "$original" in on|auto) ;; *) log "Unsupported control $control=$original"; exit 1 ;; esac
  identity=parent
  if [ -f "$directory/idVendor" ]; then
    identity="$(cat "$directory/idVendor"):$(cat "$directory/idProduct"):$(cat "$directory/devnum")"
  fi
  printf '%s\n%s\n%s\n' "$control" "$original" "$identity" > "$state/saved-$n"
  log "SAVED $control=$original identity=$identity"
  n=$((n + 1))
  echo "$n" > "$state/count"
done
previous=
while session_valid && [ ! -f "$state/stop" ]; do
  heartbeat
  current=$(find_rds)
  [ "$current" = "$rds" ] && [ "$(cat "$rds/devnum" 2>/dev/null)" = "$number" ] || { log 'RDS detached/replaced; restoring'; break; }
  for directory in $chain; do
    control=$directory/power/control
    [ "$(cat "$control" 2>/dev/null)" = on ] && continue
    printf 'on\n' > "$control" || { log "PIN FAILED $control"; exit 1; }
    [ "$(cat "$control")" = on ] || { log "PIN readback failed $control"; exit 1; }
    log "PIN $control=on"
  done
  status="control=$(cat "$rds/power/control") runtime=$(cat "$rds/power/runtime_status") host=$(cat "${rds%/*}/power/runtime_status")"
  [ "$status" = "$previous" ] || log "STATE $status"
  previous=$status
  sleep 1
done
