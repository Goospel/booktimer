# PreToolUse hook -- CSS comment early-terminator guard
#
# Catches a recurring trap: a block comment whose text contains a glued '*/'
# (e.g. a shared-class list written as '.oauth-*/.entry-hero' or
# '.book-*/.record-*'). The FIRST '*/' terminates the /* ... */ comment, so the
# author's intended comment tail becomes stray text and -- worse -- the CSS rule
# immediately after the early close is silently dropped by the parser.
#
# Real incidents (T-087): #522 (.dash-card dropped), #526 (.container width
# dropped, N-118), and the auth re-skin (.auth-page .auth-shell max-width
# dropped). 3rd occurrence => promoted from prose to a hard gate. The hook was
# deferred earlier over false positives; the two-sided rule below (glued on BOTH
# sides) resolves them -- '/* c */color' and standalone '/*x*/' are NOT flagged.
#
# Detection: scan staged .css blobs, tracking comment/string state. When a block
# comment closes ('*/'), flag it only if BOTH the char before '*' and the char
# after '/' are selector/identifier-ish ([A-Za-z0-9._#*-]). A legitimate comment
# terminator is followed by whitespace/newline/brace/EOF, so it is never flagged;
# only a '*/' wedged inside an identifier (the bug signature) trips it.
#
# Scope: only files matching \.css$ in the commit's staged set.
# Bypass token (include in the commit command to skip): SKIP_CSS_COMMENT_CHECK
#
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

# Bypass token
if ($cmd -match 'SKIP_CSS_COMMENT_CHECK') { exit 0 }

$cwd = [string]$data.cwd
if ([string]::IsNullOrWhiteSpace($cwd)) { $cwd = (Get-Location).Path }

# Staged .css files (added/copied/modified -- skip deletes)
try {
    $staged = @(& git -C $cwd diff --cached --name-only --diff-filter=ACM 2>$null)
} catch { $staged = @() }

$cssStaged = @($staged | Where-Object { $_ -match '\.css$' })
if ($cssStaged.Count -eq 0) { exit 0 }   # no CSS staged -- skip

# selector/identifier-ish char (class/id list fragment)
function Test-SelChar([char]$ch) {
    return ([string]$ch -match '[A-Za-z0-9._#*\-]')
}

# Scan content; return 1-based line of the first offending early-close, or 0.
function Find-EarlyClose([string]$content) {
    if ([string]::IsNullOrEmpty($content)) { return 0 }
    # Fast pre-filter: no glued '*/' candidate at all -> definitely clean.
    if ($content -notmatch '[A-Za-z0-9._#*\-]\*/[A-Za-z0-9._#*\-]') { return 0 }

    $n = $content.Length
    $i = 0
    $line = 1
    $inComment = $false
    $inString = $false
    $stringCh = [char]0
    while ($i -lt $n) {
        $c  = $content[$i]
        $nx = if ($i + 1 -lt $n) { $content[$i + 1] } else { [char]0 }
        if ($c -eq "`n") { $line++; $i++; continue }
        if ($inComment) {
            if ($c -eq '*' -and $nx -eq '/') {
                $before = if ($i -ge 1)       { $content[$i - 1] } else { [char]0 }
                $after  = if ($i + 2 -lt $n)  { $content[$i + 2] } else { [char]0 }
                if ((Test-SelChar $before) -and (Test-SelChar $after)) { return $line }
                $inComment = $false
                $i += 2
                continue
            }
            $i++
            continue
        }
        if ($inString) {
            if ($c -eq '\') { $i += 2; continue }   # skip escaped char
            if ($c -eq $stringCh) { $inString = $false }
            $i++
            continue
        }
        # outside comment/string
        if ($c -eq '/' -and $nx -eq '*') { $inComment = $true; $i += 2; continue }
        if ($c -eq '"' -or $c -eq "'")   { $inString = $true; $stringCh = $c; $i++; continue }
        $i++
    }
    return 0
}

foreach ($f in $cssStaged) {
    # Read the STAGED blob (not the working tree) so the gate matches what is committed.
    $prevEAP = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    $content = (& git -C $cwd show ":$f" 2>$null) -join "`n"
    $ErrorActionPreference = $prevEAP
    if ([string]::IsNullOrEmpty($content)) { continue }

    $badLine = Find-EarlyClose $content
    if ($badLine -gt 0) {
        $msg = @"
[BLOCKED] CSS comment closes early on a glued '*/'.

  file: $f  (line $badLine)

A '*/' is wedged inside an identifier (e.g. '.oauth-*/.entry-hero'). The first
'*/' terminates the /* ... */ comment, so the rule right after the comment is
silently dropped by the CSS parser -- a styling bug that passes tests and only
shows in a real browser.

Fix: in the comment, keep '*' from being immediately followed by '/'.
Separate list items with a middle dot or comma, e.g. '.oauth-*, .entry-hero'.

This trap recurred 3x (BookTimer T-087 / N-118). Override: add token SKIP_CSS_COMMENT_CHECK.
"@
        [Console]::Error.WriteLine($msg)
        exit 2
    }
}

exit 0
