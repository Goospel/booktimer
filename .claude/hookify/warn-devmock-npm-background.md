---
name: warn-devmock-npm-background
enabled: true
event: bash
action: warn
pattern: npm\s+(--prefix\s+\S*miniapp\S*\s+)?run\s+dev:mock
---
⚠️ **백그라운드로 띄운 목 모드 서버를 npm으로 띄우면 TaskStop이 vite를 못 끈다**(T-264). `npm run dev:mock`은 `cmd.exe /c vite`를 거쳐 node vite를 손자 프로세스로 띄워서, TaskStop이 「성공」을 돌려줘도 그 node가 포트를 쥐고 남는다(2026-10-14 대조 실험 — npm 경유 5307은 남고, 직접 실행 5306은 한 번에 꺼졌다).

→ miniapp 폴더에서 **vite를 직접** 띄운다:

```
cd <작업 폴더>/miniapp && node node_modules/vite/bin/vite.js --mode mock --port <N> --strictPort
```

사람이 터미널에서 돌리는 경우(Ctrl+C로 끄는 경우)는 그대로 써도 된다. 이미 npm으로 띄웠다면 끝낼 때 포트 리스너의 명령줄이 이 폴더 vite인지 확인하고 그 PID만 끈다(T-264 「해결」).
