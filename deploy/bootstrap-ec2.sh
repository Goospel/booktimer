#!/usr/bin/env bash
# EC2 최초 1회 셋업 (Amazon Linux 2023). SSM Session Manager로 접속해 `sudo bash bootstrap-ec2.sh`.
# 멱등 — 여러 번 돌려도 안전하다.
set -euo pipefail

# ── 1) Docker ──
dnf install -y docker
systemctl enable --now docker
usermod -aG docker ec2-user

# ── 2) docker compose v2 (AL2023 리포에 플러그인이 없어 직접 설치) ──
CLI_PLUGINS=/usr/local/lib/docker/cli-plugins
mkdir -p "$CLI_PLUGINS"
if ! docker compose version >/dev/null 2>&1; then
    curl -SL "https://github.com/docker/compose/releases/latest/download/docker-compose-linux-$(uname -m)" \
        -o "$CLI_PLUGINS/docker-compose"
    chmod +x "$CLI_PLUGINS/docker-compose"
fi

# ── 3) 스왑 2GB ──
# 평시엔 1.23GB/2GB로 여유가 있지만, 배포 순간 app 컨테이너가 2개가 되어 1.78GB까지 오른다.
# 스왑이 없으면 그 순간 OOM killer가 MySQL을 잡을 수 있다.
if ! swapon --show=NAME --noheadings | grep -qx /swapfile; then
    fallocate -l 2G /swapfile
    chmod 600 /swapfile
    mkswap /swapfile
    swapon /swapfile
    grep -q '^/swapfile' /etc/fstab || echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi

# ── 4) 컨테이너 로그 로테이션 ──
# 기본 json-file 드라이버는 무제한이다. Caddy가 없는 upstream에 3초마다 헬스체크 실패를
# info 로그로 남기므로, 두면 30GB 디스크가 로그로 찬다.
mkdir -p /etc/docker
cat > /etc/docker/daemon.json <<'JSON'
{
  "log-driver": "json-file",
  "log-opts": { "max-size": "10m", "max-file": "3" }
}
JSON
systemctl restart docker

# ── 5) 앱 디렉터리 ──
install -d -o ec2-user -g ec2-user /opt/booktimer

# ── 6) 백업 cron (03:00 KST = 18:00 UTC) ──
# ⚠️ Amazon Linux 2023은 cron을 기본 포함하지 않는다(systemd timer를 권장). cronie가 없으면
#    /etc/cron.d 디렉터리 자체가 없어서 아래 리다이렉트가 "No such file or directory"로 죽는다.
#    (실제로 첫 bootstrap 실행이 여기서 실패했다.)
dnf install -y cronie
systemctl enable --now crond

# ⚠️ root로 돌린다. ec2-user 로 두면 두 가지가 동시에 깨진다(실제로 그래서 백업이 0건이었다):
#   - /var/log/ 는 root 전용이라 로그 리다이렉트부터 실패 → 스크립트가 실행조차 안 되고
#     실패 흔적도 안 남는다(무성 실패).
#   - .env 는 render-env.sh 가 root:600 으로 만드므로 DB 비밀번호를 못 읽는다.
# 배포 경로(SSM Send-Command)도 root로 도니 소유권이 이쪽에 맞춰져 있다.
cat > /etc/cron.d/booktimer-backup <<'CRON'
0 18 * * * root cd /opt/booktimer && ./backup-mysql.sh >> /var/log/booktimer-backup.log 2>&1
CRON
chmod 644 /etc/cron.d/booktimer-backup

echo "[bootstrap] 완료. /opt/booktimer 에 compose.prod.yaml·Caddyfile·*.sh 를 배치한 뒤"
echo "[bootstrap]   cd /opt/booktimer && ./render-env.sh && docker compose -f compose.prod.yaml up -d caddy mysql app-blue"
