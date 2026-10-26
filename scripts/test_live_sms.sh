#!/usr/bin/env bash
set -euo pipefail
mkdir -p app/build
# -e constrains all commands to the emulator, never a physical user's phone.
adb -e install -r app/build/outputs/apk/debug/app-debug.apk
adb -e install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -e shell am instrument -w -e liveSms true -e class com.paisapilot.app.LiveSmsBroadcastTest com.paisapilot.app.test/androidx.test.runner.AndroidJUnitRunner > app/build/live-sms-test.txt 2>&1 &
pilot_sms_pid=$!
trap 'kill "$pilot_sms_pid" 2>/dev/null || true' EXIT
pilot_sms_ready=false
for attempt in $(seq 1 40); do
  if grep -Fq 'WAITING_FOR_TEST_SMS' app/build/live-sms-test.txt; then
    pilot_sms_ready=true
    break
  fi
  if ! kill -0 "$pilot_sms_pid" 2>/dev/null; then
    cat app/build/live-sms-test.txt
    exit 1
  fi
  sleep 1
done
if [ "$pilot_sms_ready" != true ]; then
  cat app/build/live-sms-test.txt
  exit 1
fi
adb -e emu sms send +15550001111 'INR 29.00 debited from test account'
wait "$pilot_sms_pid"
trap - EXIT
cat app/build/live-sms-test.txt
grep -Fq 'OK (1 test)' app/build/live-sms-test.txt
