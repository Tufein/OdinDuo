#!/system/bin/sh
# Keep the vendor Binder request short; execute restoration and acknowledgement here.
app=/data/user/0/nl.retroid.touchguard/files
token=$1
case "$token" in ''|*[!a-f0-9]*) exit 2 ;; esac
[ "${#token}" = 32 ] || exit 2
[ "$(id -u)" = 0 ] || exit 1
/system/bin/sh "$app/power-guard.sh" --stop >> "$app/power-guard.txt" 2>&1
result=$?
[ ! -d /data/local/tmp/retroid-power-guard-state ] || result=1
printf 'STOP acknowledged %s result=%s\n' "$token" "$result" >> "$app/power-guard.txt" || exit 1
exit "$result"
