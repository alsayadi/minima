#!/bin/bash
# Push the OpenAI API key + mark onboarding as done on the running emulator.
# Used during development iterations after uninstall/reinstall cycles.
# Do NOT commit actual keys into the repo — this script reads from env.

set -e

ADB=~/Library/Android/sdk/platform-tools/adb
DEVICE=${1:-emulator-5554}
KEY="${OPENAI_API_KEY:?export OPENAI_API_KEY=sk-... first}"

echo "Pushing key to $DEVICE..."
XML="<?xml version=\"1.0\" encoding=\"utf-8\" standalone=\"yes\" ?>
<map>
    <string name=\"openai_api_key\">$KEY</string>
    <string name=\"api_key_OPENAI\">$KEY</string>
    <string name=\"llm_provider\">OPENAI</string>
    <boolean name=\"onboarding_completed\" value=\"true\" />
</map>"

$ADB -s "$DEVICE" shell "run-as com.minima.os sh -c 'mkdir -p shared_prefs && cat > shared_prefs/minima_prefs.xml'" <<< "$XML"

echo "Restart app to pick up the key:"
$ADB -s "$DEVICE" shell am force-stop com.minima.os
$ADB -s "$DEVICE" shell am start -n "com.minima.os/com.minima.os.LauncherActivity" 2>&1 | tail -1
echo "Done."
