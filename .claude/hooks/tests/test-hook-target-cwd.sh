#!/usr/bin/env bash
# TDD test for the OTHER-WORKTREE BLIND SPOT in git-state hooks (T-242).
#
# Every git-state PreToolUse hook takes the SESSION cwd from the hook input
# (`$data.cwd`). A command can move first -- `cd "<other worktree>" && git
# commit ...`, `git -C <path> commit`, `Set-Location <path>; git push` -- and the
# hook then inspects the wrong repository. Measured 2026-09-13 (PR #1114): the
# test gate saw an empty index in the session worktree and skipped
# `./gradlew test` for two commits made in another worktree.
#
# Fix: .claude/hooks/lib/resolve-target-cwd.ps1 follows cd / Set-Location /
# git -C in the command and returns the target worktree's top level.
#   no directory change              -> session cwd (unchanged behaviour)
#   literal path, missing / not repo -> session cwd (git never runs there anyway)
#   unexpandable ($VAR, %VAR%)       -> gate hooks BLOCK; message/TOC hooks fall back
#
# Fixtures: S = clean session repo, T = target repo holding what the hook must see.
#   [RED]  fails before the fix
#   [dir]  reverse direction (session=T, cd S) -- also fails before the fix
#   [ctrl] holds before AND after, so "block everything" cannot pass

HOOKS="${HOOKS:-.claude/hooks}"   # overridable: run against a mutated copy
FAILED=0
TMPS=()
cleanup() { for d in "${TMPS[@]}"; do rm -rf "$d" 2>/dev/null; done; }
trap cleanup EXIT

ERRF=$(mktemp); TMPS+=("$ERRF")
json_esc() { printf '%s' "$1" | sed 's/\\/\\\\/g; s/"/\\"/g' | awk 'BEGIN{ORS=""} NR>1{print "\\n"} {print}'; }  # newlines -> \n
win()   { cygpath -w "$1"; }   # C:\Users\...
mixed() { cygpath -m "$1"; }   # C:/Users/...
drive_posix() { local m; m=$(cygpath -m "$1"); printf '/%s%s' "$(printf '%s' "${m:0:1}" | tr 'A-Z' 'a-z')" "${m:2}"; }  # /c/Users/...

# Run a hook; prints exit code, keeps stderr in $ERRF.
run_hook() {
    local esc_cmd esc_cwd
    esc_cmd=$(json_esc "$2"); esc_cwd=$(json_esc "$3")
    printf '{"tool_input":{"command":"%s"},"cwd":"%s"}' "$esc_cmd" "$esc_cwd" \
        | timeout 90 powershell.exe -NoProfile -File "$HOOKS/$1" >/dev/null 2>"$ERRF"
    echo $?
}

check() {
    local label="$1" expected="$2" got="$3"
    if [ "$got" = "$expected" ]; then echo "PASS: $label"; else echo "FAIL: $label (expected $expected, got $got)"; FAILED=1; fi
}

# exit code + "+msg" when the block names T-242 (unresolvable target)
run_unresolved() {
    local got; got=$(run_hook "$1" "$2" "$3")
    grep -q 'T-242' "$ERRF" && got="$got+msg"
    echo "$got"
}

init_repo() {
    mkdir -p "$1"
    git -C "$1" init -q -b main 2>/dev/null || { git -C "$1" init -q; git -C "$1" checkout -q -b main; }
    git -C "$1" config user.email t@t.t; git -C "$1" config user.name tester; git -C "$1" config core.autocrlf false
    printf '# doc\n' > "$1/README.md"
    git -C "$1" add . && git -C "$1" commit -q -m init
}

P=$(mktemp -d); TMPS+=("$P")
S="$P/S"; T="$P/T"
init_repo "$S"
printf 'feat: x\n' > "$S/.commit-msg-tmp"                    # clean title (message hook fixture)

# T: staged .java change + gradlew that always FAILS -> exit 2 proves the gate ran on T
init_repo "$T"
printf '@echo off\r\nexit /b 1\r\n' > "$T/gradlew.bat"
echo 'class Foo {}' > "$T/Foo.java"
git -C "$T" add Foo.java
mkdir -p "$T/sub"

# same, under a directory with a space
SP="$P/my repo"
init_repo "$SP"
printf '@echo off\r\nexit /b 1\r\n' > "$SP/gradlew.bat"
echo 'class Foo {}' > "$SP/Foo.java"
git -C "$SP" add Foo.java

SW=$(win "$S"); TW=$(win "$T"); TM=$(mixed "$T")
C='git commit -F .commit-msg-tmp'
tg() { check "$1" "$2" "$(run_hook require-tests-before-commit.ps1 "$3" "$4")"; }

# ══ Parser variants (test gate, session = S) ═════════════════════════════════
tg '[P1 RED] cd "C:/..." && commit'                  2 "cd \"$TM\" && $C" "$SW"
tg '[P2 RED] cd C:/... unquoted'                     2 "cd $TM && $C" "$SW"
tg '[P3 RED] cd "C:\..." backslashes'                2 "cd \"$TW\" && $C" "$SW"
tg '[P4 RED] cd /c/... (Git Bash drive path)'        2 "cd $(drive_posix "$T") && $C" "$SW"
tg '[P5 RED] cd /d "C:\..." (cmd)'                   2 "cd /d \"$TW\" && $C" "$SW"
tg '[P6 RED] git -C "C:\..." commit'                 2 "git -C \"$TW\" commit -F .commit-msg-tmp" "$SW"
tg '[P7 RED] Set-Location -Path "..."; commit (PS)'  2 "Set-Location -Path \"$TW\"; $C" "$SW"
tg '[P8 RED] relative cd ../T'                       2 "cd ../T && $C" "$SW"
tg '[P9 RED] cd T/sub -> resolves to top level'      2 "cd \"$TM/sub\" && $C" "$SW"
tg '[P10 RED] cd P && git -C T commit (relative -C)' 2 "cd \"$(mixed "$P")\" && git -C T commit -F .commit-msg-tmp" "$SW"
tg '[P11 RED] quoted path containing a space'        2 "cd '$(win "$SP")' && $C" "$SW"
check '[P12 RED] cd "$WT" unexpandable -> BLOCK naming T-242' '2+msg' \
    "$(run_unresolved require-tests-before-commit.ps1 'cd "$WT" && git commit -F .commit-msg-tmp' "$SW")"

# ══ Controls (test gate) ═════════════════════════════════════════════════════
tg '[K1 ctrl] session=T, plain commit -> gate runs'                   2 "$C" "$TW"
tg '[K2 ctrl] session=S, plain commit -> skip'                        0 "$C" "$SW"
check '[K3 ctrl] cd <missing>; commit -> session cwd (T), gate runs (not a T-242 block)' 2 \
    "$(run_unresolved require-tests-before-commit.ps1 "cd \"$(mixed "$P")/nope\"; $C" "$TW")"
tg '[K4 ctrl] SKIP_TESTS still bypasses after cd'                     0 "cd \"$TM\" && $C SKIP_TESTS" "$SW"
tg '[K5 ctrl] "echo cd <T>" is not a directory change'                0 "echo cd \"$TM\" && $C" "$SW"
tg '[K6 ctrl] git -C T add && git commit -> -C belongs to add only'   0 "git -C \"$TM\" add Foo.java && $C" "$SW"
tg '[K7 dir]  session=T, cd S && commit -> follows cd, skip'          0 "cd \"$(mixed "$S")\" && $C" "$TW"
tg '[K8 RED] session=T/sub (no cd) -> top level, gradlew found'      2 "$C" "$(win "$T/sub")"
tg '[K9 ctrl] commit && cd T afterwards -> not a move before commit'  0 "$C && cd \"$TM\"" "$SW"
tg '[K10 ctrl] session=T/sub, git -c k=v commit -> -c value is no path' 2 "git -c user.name=x commit -F .commit-msg-tmp" "$(win "$T/sub")"
for a in chdir pushd sl push-location; do
    tg "[K-alias ctrl] $a \"<T>\" && commit" 2 "$a \"$TM\" && $C" "$SW"
done
check '[K11 ctrl] cd ~/T expands the home folder' 2 \
    "$(export USERPROFILE="$(win "$P")"; run_hook require-tests-before-commit.ps1 "cd ~/T && $C" "$SW")"
tg '[K12 RED] if ...; then cd T; fi; commit -> move after "then" counts' 2 "if true; then cd \"$TM\"; fi; $C" "$SW"
for u in 'cd -' 'cd %WT%'; do
    check "[K-unres ctrl] $u -> BLOCK naming T-242" '2+msg' "$(run_unresolved require-tests-before-commit.ps1 "$u && $C" "$SW")"
done
# review M-3: moves that do not decide where the commit runs must not block or redirect
tg '[K13 RED] (cd "$D" && npm run build) && commit -> closed subshell ignored' 0 '(cd "$D" && npm run build) && git commit -F .commit-msg-tmp' "$SW"
tg '[K14 RED] (cd T && make) && commit -> commit stays in S'               0 "(cd \"$TM\" && make) && $C" "$SW"
check '[K15 RED] cd "$(git rev-parse --show-toplevel)" -> session top level, no block' 2 \
    "$(run_unresolved require-tests-before-commit.ps1 'cd "$(git rev-parse --show-toplevel)" && git commit -F .commit-msg-tmp' "$(win "$T/sub")")"
HEREDOC=$'cat > x.sh <<\'EOF\'\ncd "$HOME/elsewhere"\nEOF\ngit commit -F .commit-msg-tmp'
check '[K16 RED] cd inside a heredoc body is ignored (gate runs on session T)' 2 \
    "$(run_unresolved require-tests-before-commit.ps1 "$HEREDOC" "$TW")"

# ══ Wiring: one hit + one reverse per hook ═══════════════════════════════════

# -- require-css-comment-safe.ps1: staged css with a glued '*/'
CT="$P/css"; init_repo "$CT"
printf '/* shared: .oauth-*/.entry-hero */\n.a { color: red; }\n' > "$CT/a.css"
git -C "$CT" add a.css
check '[W-C1 RED] css: session=S, cd T -> blocks'   2 "$(run_hook require-css-comment-safe.ps1 "cd \"$(mixed "$CT")\" && $C" "$SW")"
check '[W-C2 dir] css: session=T, cd S -> passes'   0 "$(run_hook require-css-comment-safe.ps1 "cd \"$(mixed "$S")\" && $C" "$(win "$CT")")"
check '[W-C3 RED] css: cd "$WT" -> BLOCK naming T-242' '2+msg' \
    "$(run_unresolved require-css-comment-safe.ps1 'cd "$WT" && git commit -F .commit-msg-tmp' "$SW")"

# -- check-commit-message.ps1: relative -F must resolve in the target
MT="$P/msg"; init_repo "$MT"
printf 'feat: x (#123)\n' > "$MT/.commit-msg-tmp"
check '[W-M1 RED] msg: session=S, cd T -> reads T title (#123) -> blocks' 2 "$(run_hook check-commit-message.ps1 "cd \"$(mixed "$MT")\" && $C" "$SW")"
check '[W-M2 dir] msg: session=T, cd S -> reads S title -> passes'        0 "$(run_hook check-commit-message.ps1 "cd \"$(mixed "$S")\" && $C" "$(win "$MT")")"
check '[W-M3 ctrl] msg: cd "$WT" -> falls back to session S (no block)'   0 "$(run_hook check-commit-message.ps1 'cd "$WT" && git commit -F .commit-msg-tmp' "$SW")"
# git reads a relative -F from the directory it runs in, not from the top level
MS="$P/msgsub"; init_repo "$MS"; mkdir -p "$MS/sub"
printf 'feat: clean\n' > "$MS/.commit-msg-tmp"
printf 'feat: x (#123)\n' > "$MS/sub/.commit-msg-tmp"
check '[W-M4 RED] msg: session=T/sub -> -F read from sub, not top level' 2 "$(run_hook check-commit-message.ps1 "$C" "$(win "$MS/sub")")"
check '[W-M5 RED] msg: cd T/sub && commit -> -F read from sub'          2 "$(run_hook check-commit-message.ps1 "cd \"$(mixed "$MS")/sub\" && $C" "$SW")"

# -- require-troubleshooting-toc.ps1: auto-fix must land in the target's index
toc_repo() {
    init_repo "$1"; mkdir -p "$1/claude-docs" "$1/.claude/scripts"
    printf '# ts\n' > "$1/claude-docs/troubleshooting.md"
    printf 'param([string]$Path)\nAdd-Content -Path $Path -Value "REBUILT-MARK"\nWrite-Output "changed"\n' \
        > "$1/.claude/scripts/rebuild-troubleshooting-toc.ps1"
    git -C "$1" add claude-docs/troubleshooting.md
}
marked() { git -C "$1" show :claude-docs/troubleshooting.md 2>/dev/null | grep -q REBUILT-MARK && echo yes || echo no; }
TT1="$P/toc1"; toc_repo "$TT1"
run_hook require-troubleshooting-toc.ps1 "cd \"$(mixed "$TT1")\" && $C" "$SW" >/dev/null
check '[W-T1 RED] toc: session=S, cd T -> TOC rebuilt into T index' yes "$(marked "$TT1")"
TT2="$P/toc2"; toc_repo "$TT2"
run_hook require-troubleshooting-toc.ps1 "cd \"$(mixed "$S")\" && $C" "$(win "$TT2")" >/dev/null
check '[W-T2 dir] toc: session=T, cd S -> T untouched' no "$(marked "$TT2")"
TT3="$P/toc3"; toc_repo "$TT3"
got=$(run_hook require-troubleshooting-toc.ps1 'cd "$WT" && git commit -F .commit-msg-tmp' "$(win "$TT3")")
check '[W-T3 ctrl] toc: cd "$WT" -> falls back to session T, rebuilds, exit 0' "0 yes" "$got $(marked "$TT3")"

# -- require-changelog-no-dup.ps1 (git push): duplicate rows in the target
DT="$P/dup"; init_repo "$DT"; mkdir -p "$DT/claude-docs"
printf '| d | x |\n|---|---|\n| 2026-09-13 | **fix: same** - a |\n| 2026-09-13 | **fix: same** - a longer |\n' > "$DT/claude-docs/changelog.md"
PUSH='git push -u origin fix/x'
check '[W-D1 RED] dup: session=S, cd T && push -> blocks' 2 "$(run_hook require-changelog-no-dup.ps1 "cd \"$(mixed "$DT")\" && $PUSH" "$SW")"
check '[W-D2 dir] dup: session=T, cd S && push -> passes' 0 "$(run_hook require-changelog-no-dup.ps1 "cd \"$(mixed "$S")\" && $PUSH" "$(win "$DT")")"
check '[W-D3 RED] dup: cd "$WT" -> BLOCK naming T-242' '2+msg' \
    "$(run_unresolved require-changelog-no-dup.ps1 'cd "$WT" && git push -u origin fix/x' "$SW")"

# -- block-main-push.ps1 (bare git push): branch of the target, not the session
FT="$P/feat"; init_repo "$FT"; git -C "$FT" checkout -q -b feat/x   # session on a feature branch
MN="$P/mainrepo"; init_repo "$MN"                                    # target on main
check '[W-P1 RED] push: session=feat, cd <main repo> && git push -> blocks' 2 "$(run_hook block-main-push.ps1 "cd \"$(mixed "$MN")\" && git push" "$(win "$FT")")"
check '[W-P2 dir] push: session=main, cd <feat repo> && git push -> passes' 0 "$(run_hook block-main-push.ps1 "cd \"$(mixed "$FT")\" && git push" "$(win "$MN")")"
check '[W-P3 RED] push: cd "$WT" && bare push -> BLOCK naming T-242' '2+msg' \
    "$(run_unresolved block-main-push.ps1 'cd "$WT" && git push' "$(win "$FT")")"
check '[W-P4 ctrl] push: cd "$WT" && push origin feat/x -> explicit ref, passes' 0 \
    "$(run_hook block-main-push.ps1 'cd "$WT" && git push -u origin feat/x' "$(win "$FT")")"
check '[W-P5 RED] push: cd "$WT" && git push ALLOW_MAIN_PUSH -> token still honored' 0 \
    "$(run_hook block-main-push.ps1 'cd "$WT" && git push ALLOW_MAIN_PUSH' "$(win "$FT")")"

# -- require-bundle-build.ps1: needs node
if command -v node >/dev/null 2>&1; then
    BT="$P/bundle"; init_repo "$BT"
    mkdir -p "$BT/src/main/resources/static/garden" "$BT/frontend/src"
    printf 'old content\n' > "$BT/src/main/resources/static/garden/garden.js"
    cat > "$BT/frontend/build-fixture.js" << 'EOF'
const fs = require('fs'), path = require('path');
fs.writeFileSync(path.join(__dirname, '..', 'src', 'main', 'resources', 'static', 'garden', 'garden.js'), 'new content\n');
EOF
    printf '{"scripts":{"build":"node build-fixture.js"}}' > "$BT/frontend/package.json"
    printf 'export const x = 1;\n' > "$BT/frontend/src/index.ts"
    git -C "$BT" add . && git -C "$BT" commit -q -m bundle
    printf 'export const x = 2;\n' > "$BT/frontend/src/index.ts"
    git -C "$BT" add frontend/src/index.ts
    check '[W-B1 RED] bundle: session=S, cd T -> stale bundle blocks' 2 "$(run_hook require-bundle-build.ps1 "cd \"$(mixed "$BT")\" && $C" "$SW")"
    check '[W-B2 dir] bundle: session=T, cd S -> passes'             0 "$(run_hook require-bundle-build.ps1 "cd \"$(mixed "$S")\" && $C" "$(win "$BT")")"
    check '[W-B3 RED] bundle: cd "$WT" -> BLOCK naming T-242' '2+msg' \
        "$(run_unresolved require-bundle-build.ps1 'cd "$WT" && git commit -F .commit-msg-tmp' "$SW")"
    # another project followed by cd: has frontend/ but no BookTimer bundle layout -> not built
    OB="$P/otherproj"; init_repo "$OB"; mkdir -p "$OB/frontend"
    printf 'process.exit(1);\n' > "$OB/frontend/fail.js"
    printf '{"scripts":{"build":"node fail.js"}}' > "$OB/frontend/package.json"
    git -C "$OB" add frontend
    check '[W-B4 RED] bundle: cd <repo without src/main/resources/static> -> skipped, not built' 0 \
        "$(run_hook require-bundle-build.ps1 "cd \"$(mixed "$OB")\" && $C" "$SW")"
else
    echo "SKIP: node not in PATH -- bundle wiring cases skipped"
fi

exit $FAILED
