#!/bin/sh
cd ~/Projects/roc-mic

printf '\n=== Git status ===\n'
git status --short

printf '\n=== Current changes summary ===\n'
git diff --stat

printf '\n=== Source and resource files ===\n'
find app/src/main -type f | sort

printf '\n=== ROC endpoints, ports, audio loop, UI, persistence ===\n'
grep -RInE \
  'RocSender|Endpoint\.builder|AUDIO_SOURCE|AUDIO_REPAIR|10001|10002|10003|AudioRecord|SharedPreferences|DataStore|setOnClickListener|@Composable|write\(' \
  app/src/main

