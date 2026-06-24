#!/usr/bin/env bash
# docker-cleanup.sh — BookTimer bootRun docker-compose 컨테이너 정리기
#
# 목적: bootRun(spring-boot-docker-compose)이 워크트리별로 띄운 MySQL 컨테이너가
#   ① 검증 후 미정리(Up 좀비) ② 워크트리 삭제 후 고아(Exited)로 누적되는 것을 청소한다.
#   ※ 테스트(./gradlew test)는 H2라 컨테이너를 안 만든다 — 누적의 범인은 bootRun이다.
#
# 안전(핵심): com.docker.compose.project.working_dir 라벨이 이 repo 폴더 계열
#   (메인 BookTimer / 형제 워크트리 BookTimer-* / 내부 .claude/worktrees/*)인 컨테이너만
#   대상으로 삼는다. 다른 프로젝트(hoseo/twitter 등)는 working_dir 경로가 달라 절대 안 건드린다.
#
# 사용:
#   bash .claude/scripts/docker-cleanup.sh            # 기본: Exited(멈춘)만 제거 — 멀티세션 안전
#   bash .claude/scripts/docker-cleanup.sh --all      # Up(실행 중) 포함 전부 강제 제거 — 대청소
#   bash .claude/scripts/docker-cleanup.sh --dry-run  # 대상만 보여주고 삭제는 안 함
#   (--all --dry-run 조합 가능)
#
# ⚠️ --all 은 Up 컨테이너까지 죽인다. 다른 세션이 그 워크트리에서 bootRun 중이면 그 DB가 끊긴다.
#    멀티 세션 동시 작업 중이면 기본(Exited만)을 쓰거나, 먼저 --dry-run 으로 대상을 확인하라.
#
# 종료코드: 0 정상, 2 사용법오류, 3 docker 명령 없음.
set -u

note() { echo "[docker-cleanup] $*"; }

# ── 인자 파싱 ───────────────────────────────────────────────────────────────────
ALL=0
DRYRUN=0
for a in "$@"; do
  case "$a" in
    --all)     ALL=1 ;;
    --dry-run) DRYRUN=1 ;;
    -h|--help)
      sed -n '2,30p' "$0" | sed 's/^# \{0,1\}//'
      exit 0 ;;
    *) echo "알 수 없는 옵션: $a (사용법: --all, --dry-run)" >&2; exit 2 ;;
  esac
done

command -v docker >/dev/null 2>&1 || { echo "docker 명령을 찾을 수 없습니다." >&2; exit 3; }

# ── 대상 선별 ───────────────────────────────────────────────────────────────────
# 형식: ID|State|Names|working_dir 라벨. working_dir 이 '<구분자>BookTimer' 를 포함하면 우리 것.
#   매칭:  ...\ClodeProjects\BookTimer  /  ...\BookTimer-dpolish-b  /  ...\BookTimer\.claude\worktrees\...
#   비매칭: ...\hoseoGraduationProject\... (BookTimer 세그먼트 없음) → 보호
TARGETS_EXITED=()   # "id|names"
TARGETS_RUNNING=()  # "id|names"
PROTECTED=0         # BookTimer 소속이 아니라 건드리지 않는 컨테이너 수(가시성용)

while IFS='|' read -r id state names wdir; do
  [ -z "$id" ] && continue
  case "$wdir" in
    *[\\/]BookTimer*) ;;          # BookTimer 폴더 계열 → 대상
    *) PROTECTED=$((PROTECTED + 1)); continue ;;  # 그 외 → 보호
  esac
  if [ "$state" = "running" ]; then
    TARGETS_RUNNING+=("$id|$names")
  else
    TARGETS_EXITED+=("$id|$names")
  fi
done < <(docker ps -a --format '{{.ID}}|{{.State}}|{{.Names}}|{{.Label "com.docker.compose.project.working_dir"}}')

# ── 대상 출력 ───────────────────────────────────────────────────────────────────
note "BookTimer 소속 — Exited ${#TARGETS_EXITED[@]}개 / Up ${#TARGETS_RUNNING[@]}개. 보호(타 프로젝트) ${PROTECTED}개."

print_list() {  # $1=헤더, 나머지=항목들("id|names")
  local header="$1"; shift
  [ "$#" -eq 0 ] && return 0
  echo "  $header:"
  for t in "$@"; do echo "    - ${t#*|}  (${t%%|*})"; done
}
print_list "Exited(멈춤)" "${TARGETS_EXITED[@]}"
if [ "$ALL" = 1 ]; then
  print_list "Up(실행 중, --all 로 강제 제거)" "${TARGETS_RUNNING[@]}"
elif [ "${#TARGETS_RUNNING[@]}" -gt 0 ]; then
  note "Up ${#TARGETS_RUNNING[@]}개는 기본 모드에서 보존(다른 세션 보호). 전부 지우려면 --all."
fi

# ── 삭제 실행 ───────────────────────────────────────────────────────────────────
if [ "$DRYRUN" = 1 ]; then
  note "dry-run — 삭제하지 않음."
  exit 0
fi

removed=0
for t in "${TARGETS_EXITED[@]:-}"; do
  [ -z "$t" ] && continue
  if docker rm "${t%%|*}" >/dev/null 2>&1; then removed=$((removed + 1)); fi
done
if [ "$ALL" = 1 ]; then
  for t in "${TARGETS_RUNNING[@]:-}"; do
    [ -z "$t" ] && continue
    if docker rm -f "${t%%|*}" >/dev/null 2>&1; then removed=$((removed + 1)); fi
  done
fi

note "✅ ${removed}개 제거 완료. (보호된 타 프로젝트 ${PROTECTED}개는 그대로.)"
