#!/usr/bin/env bash
# Read-only production pre-flight. Changes nothing, prints no secrets.
#
#   bash deploy/scripts/preflight-check.sh
#
# Paste the output back; it answers the five open deployment questions:
# directories, service account, Java, certbot/TLS state, capacity.
set -uo pipefail

hr() { printf '\n===== %s =====\n' "$1"; }

hr "system"
hostnamectl 2>/dev/null | sed -n '1,6p' || uname -a
echo "uptime: $(uptime -p 2>/dev/null)"
echo "timezone: $(timedatectl show -p Timezone --value 2>/dev/null)"

hr "paths (existence, owner, mode)"
for p in /opt /opt/hrm /opt/hrm/backend /etc/hrm /var/www /var/www/hrm /var/backups/hrm /home/ubuntu; do
  if [ -e "$p" ]; then
    printf '%-24s %s\n' "$p" "$(stat -c '%U:%G %a %n' "$p" 2>/dev/null)"
  else
    printf '%-24s MISSING\n' "$p"
  fi
done

hr "service account"
if getent passwd hrm >/dev/null; then
  getent passwd hrm | awk -F: '{printf "hrm exists: uid=%s gid=%s home=%s shell=%s\n",$3,$4,$6,$7}'
else
  echo "user hrm: does not exist"
fi
echo "current user: $(whoami) (sudo: $(sudo -n true 2>/dev/null && echo yes || echo 'password required'))"

hr "java"
echo "java binary: $(readlink -f "$(command -v java)" 2>/dev/null)"
java -version 2>&1 | head -3

hr "postgresql"
psql --version 2>/dev/null || echo "psql missing"
echo "service: $(systemctl is-active postgresql 2>/dev/null)"
echo "listening: $(ss -ltnp 2>/dev/null | grep -c ':5432' ) socket(s) on 5432"
sudo -n -u postgres psql -tAc "select datname from pg_database where datname in ('hrm_prod','hrm_db');" 2>/dev/null | sed 's/^/  db: /'
sudo -n -u postgres psql -d hrm_prod -tAc "select count(*) from information_schema.tables where table_schema='public';" 2>/dev/null | sed 's/^/  public tables in hrm_prod: /'
sudo -n -u postgres psql -tAc "select rolname, rolcanlogin, rolsuper from pg_roles where rolname='hrm_app';" 2>/dev/null | sed 's/^/  role: /'

hr "schema evidence file"
ls -l /home/ubuntu/hrm_prod_schema.sql 2>/dev/null || echo "absent"
grep -c "CREATE" /home/ubuntu/hrm_prod_schema.sql 2>/dev/null | sed 's/^/  CREATE statements in file: /'

hr "nginx"
nginx -v 2>&1 | head -1
for f in /etc/nginx/sites-available/hrm /etc/nginx/sites-enabled/hrm; do
  [ -e "$f" ] && ls -l "$f" || echo "$f absent"
done
ls /etc/nginx/sites-enabled/ 2>/dev/null | sed 's/^/  enabled: /'
echo "config test: $(sudo -n nginx -t 2>&1 | tail -1)"

hr "tls / certbot"
command -v certbot >/dev/null && echo "certbot: $(certbot --version 2>&1)" || echo "certbot: not installed"
ls -l /etc/letsencrypt/live 2>/dev/null | sed 's/^/  /' || echo "  no /etc/letsencrypt/live"
sudo -n certbot certificates 2>/dev/null | grep -E "Certificate Name|Domains|Expiry" | head -6

hr "existing hrm service"
systemctl list-unit-files 'hrm*' 2>/dev/null | sed 's/^/  /'
echo "hrm-backend: $(systemctl is-active hrm-backend 2>/dev/null)"

hr "capacity"
free -h
df -h / /var 2>/dev/null
swapon --show 2>/dev/null
echo "cpu: $(nproc) vcpu"

hr "backups"
ls -lh /var/backups/hrm 2>/dev/null | head -5 || echo "no /var/backups/hrm"

hr "done - nothing was modified"
