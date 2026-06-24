# PreToolUse hook -- commit message guards (#1 + #3 combined)
#
# #1 -- commit title must NOT end with (#NNN).
#       This repo uses squash-merge: GitHub auto-appends (#NNN) at merge time.
#       A literal (#NNN) in the title yields "(#NNN) (#NNN)" and is irreversible
#       on main (force-push blocked). (BookTimer CLAUDE.md Git rule 2)
#
# #3 -- inline -m Korean commit message corrupts under PowerShell 5.1.
#       Use .commit-msg-tmp (UTF-8) + `git commit -F .commit-msg-tmp` instead.
#       (BookTimer rule T-026, CLAUDE.md section)
#
# Both checks: fail-open on parse errors, bypass tokens for intentional overrides.
# Block messages are English/ASCII only -- Korean corrupts in hook stderr.

$ErrorActionPreference = 'Stop'

try {
    $raw  = [Console]::In.ReadToEnd()
    $data = $raw | ConvertFrom-Json
    $cmd  = [string]$data.tool_input.command
} catch { exit 0 }

if ([string]::IsNullOrWhiteSpace($cmd)) { exit 0 }

# Only interested in git commit commands
if ($cmd -notmatch '\bgit\b' -or $cmd -notmatch '\bcommit\b') { exit 0 }

$cwd = [string]$data.cwd
if ([string]::IsNullOrWhiteSpace($cwd)) { $cwd = (Get-Location).Path }

# Helper: extract value of -m/--message/-c flag (double-quoted, single-quoted, or bare word)
function Get-InlineMessage([string]$command) {
    $m = [regex]::Match($command, '(?:-m|--message|-c)\s+(?:"([^"]*)"|''([^'']*)''|(\S+))')
    if (-not $m.Success) { return $null }
    if ($m.Groups[1].Success) { return $m.Groups[1].Value }
    if ($m.Groups[2].Success) { return $m.Groups[2].Value }
    return $m.Groups[3].Value
}

# ── Check #3: inline Korean in -m argument value ──────────────────────────────
# Scope regex strictly to the -m VALUE, not the whole command string.
# Avoids false-positives from chained helper commands that write to .commit-msg-tmp
# before git commit -F (e.g. printf 'Korean' > .commit-msg-tmp; git commit -F ...).
if ($cmd -notmatch 'ALLOW_INLINE_MSG') {
    $mValue = Get-InlineMessage $cmd
    if ($mValue) {
        # \p{IsHangulSyllables} = Unicode block U+AC00..U+D7A3 (Hangul syllables)
        if ($mValue -match '\p{IsHangulSyllables}') {
            $msg = @"
[BLOCKED] Inline Korean commit message corrupts under PowerShell 5.1.

Write the message to .commit-msg-tmp (UTF-8) and commit via:
  git commit -F .commit-msg-tmp        (BookTimer rule T-026)

Override: include token ALLOW_INLINE_MSG in the command.
"@
            [Console]::Error.WriteLine($msg)
            exit 2
        }
    }
}

# ── Check #1: commit title must not end with (#NNN) ───────────────────────────
if ($cmd -notmatch 'ALLOW_PR_NUM_IN_TITLE') {
    $title = $null

    # Path (a): inline -m / --message -- first line of the value
    $mValue2 = Get-InlineMessage $cmd
    if ($mValue2) {
        $title = ($mValue2 -split "`n")[0].Trim()
    }

    # Path (b): -F / --file -- read first non-blank line from the file
    if (-not $title) {
        $fMatch = [regex]::Match($cmd, '(?:-F|--file)\s+(?:"([^"]*)"|''([^'']*)''|(\S+))')
        if ($fMatch.Success) {
            $fVal = if ($fMatch.Groups[1].Success) { $fMatch.Groups[1].Value }
                    elseif ($fMatch.Groups[2].Success) { $fMatch.Groups[2].Value }
                    else { $fMatch.Groups[3].Value }
            # Resolve relative path against cwd
            if (-not [System.IO.Path]::IsPathRooted($fVal)) {
                $fVal = Join-Path $cwd $fVal
            }
            try {
                $lines = Get-Content -Path $fVal -Encoding UTF8 -ErrorAction Stop
                $title = ($lines | Where-Object { $_.Trim() -ne '' } | Select-Object -First 1)
                if ($title) { $title = $title.Trim() }
            } catch { $title = $null }   # file missing -- fail-open
        }
    }

    # Check for trailing (#NNN)
    if ($title -and $title -match '\(#\d+\)\s*$') {
        $msg = @"
[BLOCKED] Commit title ends with (#NNN).

Squash-merge auto-appends (#NNN) at merge time; a literal one yields
'(#NNN) (#NNN)' and cannot be fixed after merging to main.

Remove it. Cross-reference mid-title with bare #NNN (no parens) instead.
Override: include token ALLOW_PR_NUM_IN_TITLE in the command.
"@
        [Console]::Error.WriteLine($msg)
        exit 2
    }
}

exit 0
