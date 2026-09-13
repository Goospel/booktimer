#!/usr/bin/env bash
# TDD test for require-single-changelog-commit-before-rebase.ps1 (PreToolUse hook)
#
# T-210, 7 recurrences. `.gitattributes` sets `claude-docs/changelog.md merge=union`
# (T-098): during a rebase git replays commits one by one and union keeps BOTH the
# first draft and the final version of the branch's own row. No conflict, no failing
# test. The cure is to squash BEFORE the rebase:
#   git reset --soft $(git merge-base HEAD origin/main) && recommit
# Prose never changed that reflex in 7 recurrences, and the existing push-time hook
# (require-changelog-no-dup.ps1) fires too late -- manual inspection caught the
# duplicate first every single time. So this hook gates `git rebase` ITSELF: if the
# branch has 2+ commits touching claude-docs/changelog.md, block before the damage.
#
# fail-open on anything unknown (no changelog, no merge-base) -- a guard must not
# stop somebody else's rebase.

HOOKS="${HOOKS:-.claude/hooks}"   # overridable: run against a mutated copy
HOOK="$HOOKS/require-single-changelog-commit-before-rebase.ps1"
FAILED=0
TMPS=()
cleanup() { for d in "${TMPS[@]}"; do rm -rf "$d" 2>/dev/null; done; }
trap cleanup EXIT

ERRF=$(mktemp); TMPS+=("$ERRF")
json_esc() { printf '%s' "$1" | sed 's/\\/\\\\/g; s/"/\\"/g' | awk 'BEGIN{ORS=""} NR>1{print "\\n"} {print}'; }

# Run the hook with a given command + session cwd; echo exit code, stderr -> $ERRF
run_hook() {
    local esc_cmd esc_cwd
    esc_cmd=$(json_esc "$1"); esc_cwd=$(json_esc "$(cygpath -w "$2")")
    printf '{"tool_input":{"command":"%s"},"cwd":"%s"}' "$esc_cmd" "$esc_cwd" \
        | timeout 90 powershell.exe -NoProfile -File "$HOOK" >/dev/null 2>"$ERRF"
    echo $?
}

check() {
    local label="$1" expected="$2" got="$3"
    if [ "$got" = "$expected" ]; then echo "PASS: $label"; else echo "FAIL: $label (expected $expected, got $got)"; FAILED=1; fi
}

git_init() {
    local d="$1"
    # ⚠️ The single choke point for every `git -C "$d"` below. `git -C ""` means THE CURRENT
    # DIRECTORY, so an empty $d here does not fail -- it runs against the live BookTimer
    # worktree. Measured 2026-09-13: this file was edited WHILE bash was executing it, bash
    # resumed at a stale byte offset inside a fixture helper, and the shifted fragment ran
    # `git -C "" commit` + `git -C "" checkout main` on the real repo (committed the live tree
    # and swapped the branch). Never edit a running shell script; and keep this guard so the
    # blast radius of any repeat is a dead test, not a mutated repository.
    case "$d" in ""|/|.|..) echo "FATAL: refusing fixture dir '$d'" >&2; exit 1 ;; esac
    mkdir -p "$d"
    git -C "$d" init -q -b main 2>/dev/null || { git -C "$d" init -q; git -C "$d" checkout -q -b main; }
    git -C "$d" config user.email t@t.t
    git -C "$d" config user.name tester
    git -C "$d" config commit.gpgsign false
}

# build <dir> <changelog-commits> [--no-changelog] [--no-upstream] [--extra N]
#   base commit -> refs/remotes/origin/main pinned there -> N commits on top, each
#   touching claude-docs/changelog.md (that is what the hook counts), then N extra
#   commits touching only README.
build() {
    local d="$1" n="$2"; shift 2
    local no_changelog=0 no_upstream=0 extra=0
    while [ $# -gt 0 ]; do
        case "$1" in
            --no-changelog) no_changelog=1 ;;
            --no-upstream)  no_upstream=1 ;;
            --extra) extra="$2"; shift ;;
        esac
        shift
    done
    git_init "$d"
    printf 'base\n' > "$d/README.md"
    if [ "$no_changelog" = "0" ]; then
        mkdir -p "$d/claude-docs"
        printf '| date | what |\n| --- | --- |\n' > "$d/claude-docs/changelog.md"
    fi
    git -C "$d" add -A && git -C "$d" commit -q -m base
    [ "$no_upstream" = "0" ] && git -C "$d" update-ref refs/remotes/origin/main HEAD
    local i
    for ((i = 1; i <= n; i++)); do
        if [ "$no_changelog" = "0" ]; then
            printf '| 2026-09-13 | **row %d** |\n' "$i" >> "$d/claude-docs/changelog.md"
        else
            printf 'row %d\n' "$i" >> "$d/README.md"
        fi
        git -C "$d" add -A && git -C "$d" commit -q -m "changelog $i"
    done
    for ((i = 1; i <= extra; i++)); do
        printf 'other %d\n' "$i" >> "$d/README.md"
        git -C "$d" add -A && git -C "$d" commit -q -m "other $i"
    done
}

# A repo whose HEAD is CLEAN (0 changelog commits past base) but whose branch feat/x
# carries 2. Only a hook that counts the rebase's explicit <branch> argument can see it --
# one that counts HEAD reads 0 and waves the rebase through (measured in review).
build_branch() {
    local d="$1"
    git_init "$d"
    mkdir -p "$d/claude-docs"
    printf '| date | what |\n| --- | --- |\n' > "$d/claude-docs/changelog.md"
    printf 'base\n' > "$d/README.md"
    git -C "$d" add -A && git -C "$d" commit -q -m base
    git -C "$d" update-ref refs/remotes/origin/main HEAD
    git -C "$d" checkout -q -b feat/x
    local i
    for ((i = 1; i <= 2; i++)); do
        printf '| 2026-09-13 | **row %d** |\n' "$i" >> "$d/claude-docs/changelog.md"
        git -C "$d" add -A && git -C "$d" commit -q -m "changelog $i"
    done
    git -C "$d" checkout -q main          # HEAD back on the clean base
}

P=$(mktemp -d); TMPS+=("$P")
build "$P/n0" 0                       # (a) nothing touched changelog
build "$P/n1" 1                       # (b) one commit -- the healthy shape
build "$P/n2" 2                       # (c) the T-210 shape: add the row, then amend it
build "$P/n3" 3                       # (e) 7th recurrence: feature + review + cleanup
build "$P/mixed" 1 --extra 1          # (d) 2 commits but only one touches changelog
build "$P/nolog" 2 --no-changelog     # (k) not this repo at all
build "$P/noup" 2 --no-upstream       # (m) no origin/main -> merge-base fails
build_branch "$P/br"                  # HEAD clean (0), branch feat/x has 2

R='git rebase origin/main'

# -- the gate itself ---------------------------------------------------------
check '(a) no changelog commit passes'                 0 "$(run_hook "$R" "$P/n0")"
check '(b) single changelog commit passes'             0 "$(run_hook "$R" "$P/n1")"
check '(c) two changelog commits BLOCK'                2 "$(run_hook "$R" "$P/n2")"
check '(d) two commits, one touches changelog -> pass' 0 "$(run_hook "$R" "$P/mixed")"
check '(e) three changelog commits BLOCK'              2 "$(run_hook "$R" "$P/n3")"

# -- rebase control verbs are not a replay, they must never be gated ---------
for v in --abort --continue --skip --quit --edit-todo; do
    check "(f) git rebase $v passes" 0 "$(run_hook "git rebase $v" "$P/n2")"
done

# -- override + other rebase spellings --------------------------------------
check '(g) ALLOW_MULTI_CHANGELOG_REBASE overrides' 0 \
    "$(run_hook "$R  # ALLOW_MULTI_CHANGELOG_REBASE" "$P/n2")"
check '(h) git pull --rebase BLOCKs'  2 "$(run_hook 'git pull --rebase origin main' "$P/n2")"
check '(h) git pull -r BLOCKs'        2 "$(run_hook 'git pull -r' "$P/n2")"
check '(h) plain git pull passes'     0 "$(run_hook 'git pull' "$P/n2")"
check '(-) git push is not this hook' 0 "$(run_hook 'git push -u origin feat/x' "$P/n2")"
check '(-) bare "git rebase" defaults to origin/main' 2 "$(run_hook 'git rebase' "$P/n2")"
check '(-) git rebase -i <ref> still counted'         2 "$(run_hook 'git rebase -i origin/main' "$P/n2")"

# -- (p) a control verb must not shield a real rebase behind it. Taking only the FIRST
#        regex match let `git rebase --abort && git rebase origin/main` pass silently.
check '(p) rebase --abort && rebase <ref> -> still BLOCKs' 2 \
    "$(run_hook 'git rebase --abort && git rebase origin/main' "$P/n2")"
check '(p) rebase --continue && rebase <ref> -> still BLOCKs' 2 \
    "$(run_hook 'git rebase --continue && git rebase origin/main' "$P/n2")"

# -- (q) `git rebase <upstream> <branch>` replays THAT branch, not HEAD. In this fixture
#        HEAD is clean, so a hook counting HEAD reads 0 and waves it through.
check '(q) rebase <upstream> <branch> counts the branch, not HEAD' 2 \
    "$(run_hook 'git rebase origin/main feat/x' "$P/br")"
check '(q) rebase --onto <new> <upstream> <branch> counts the branch' 2 \
    "$(run_hook 'git rebase --onto origin/main main feat/x' "$P/br")"
check '(q ctrl) same repo, no branch arg -> HEAD is clean -> passes' 0 \
    "$(run_hook "$R" "$P/br")"

# -- (r) more positionals than git's own grammar allows: undecidable. Blocking is the
#        safe side here -- "don't know, so pass" is how this trap leaked 7 times.
check '(r) three positionals -> BLOCK (undecidable, not fail-open)' 2 \
    "$(run_hook 'git rebase a b c' "$P/n2")"

# -- (s) the subcommand POSITION is what makes it a rebase. Matching the bare word
#        `rebase` anywhere turned all three of these into false blocks (measured).
check '(s) git pull --no-rebase passes'              0 "$(run_hook 'git pull --no-rebase' "$P/n2")"
check '(s) git log --grep=rebase passes'             0 "$(run_hook 'git log --grep=rebase' "$P/n2")"
# ⚠️ the commit message has to carry a WORD AFTER `rebase`, else a position-blind hook
#    reads an empty ref, merge-base fails and it fail-opens -- passing for the wrong
#    reason. Measured: with the anchor removed, the short form survived and this one dies.
check '(s) git commit -m "...rebase origin/main" passes' 0 \
    "$(run_hook 'git commit -m "gate before rebase origin/main"' "$P/n2")"

# -- T-242: the rebase can run in ANOTHER worktree --------------------------
check '(i) cd <other worktree> && rebase -> judges THAT repo (block)' 2 \
    "$(run_hook "cd \"$(cygpath -m "$P/n2")\" && $R" "$P/n0")"
check '(j) session cwd dirty but cd <clean repo> -> pass' 0 \
    "$(run_hook "cd \"$(cygpath -m "$P/n0")\" && $R" "$P/n2")"
check '(i) git -C <other worktree> rebase -> judges THAT repo (block)' 2 \
    "$(run_hook "git -C \"$(cygpath -m "$P/n2")\" rebase origin/main" "$P/n0")"

# -- fail-open --------------------------------------------------------------
check '(k) repo without claude-docs/changelog.md passes' 0 "$(run_hook "$R" "$P/nolog")"
check '(m) no origin/main -> fail-open'                  0 "$(run_hook "$R" "$P/noup")"
check '(m) unknown ref -> fail-open'                     0 "$(run_hook 'git rebase origin/nope' "$P/n2")"

# -- (l) `git rebase` living inside prose cannot be parsed away; the point is
#        only that the JUDGEMENT decides, so a healthy branch still passes ---
PROSE=$'cat > .commit-msg-tmp <<\'EOF\'\ndocs: describe the git rebase step\nEOF'
check '(l) "git rebase" in heredoc prose, 1 changelog commit -> pass' 0 "$(run_hook "$PROSE" "$P/n1")"

# -- (n) the block message has to carry its own escape hatch ----------------
run_hook "$R" "$P/n2" >/dev/null
msg=0
grep -q 'T-210' "$ERRF" || msg=1
grep -q 'ALLOW_MULTI_CHANGELOG_REBASE' "$ERRF" || msg=1
grep -q 'reset --soft' "$ERRF" || msg=1
check '(n) block message names T-210, the token and the cure' 0 "$msg"

# -- (o) the Korean half has to survive too. [Console]::Error.WriteLine goes through
#        the console output encoding (CP949 on a Korean Windows) and garbles it --
#        measured 2026-09-13: "이 브랜치는" arrived as mojibake. The hook writes raw
#        UTF-8 bytes to stderr instead. Assert the BYTES: Git Bash grep is C-locale
#        and silently matches nothing for a Korean literal, so escape them.
ko=0
grep -qF "$(printf '\xEC\xBB\xA4\xEB\xB0\x8B')" "$ERRF" || ko=1   # 커밋
check '(o) block message keeps Korean as raw UTF-8 (no CP949 mojibake)' 0 "$ko"

if [ "$FAILED" = "0" ]; then echo "ALL PASS"; else echo "SOME TESTS FAILED"; exit 1; fi
