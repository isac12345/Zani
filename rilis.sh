#!/data/data/com.termux/files/usr/bin/sh
# pakai: ./rilis.sh 1.1.0
set -eu
V="${1:?pakai: ./rilis.sh 1.1.0}"
case "$V" in [0-9]*.[0-9]*.[0-9]*) ;; *) echo "format versi salah"; exit 1;; esac
[ "$(git branch --show-current)" = main ] || { echo "bukan di branch main"; exit 1; }
git diff --quiet && git diff --cached --quiet || { echo "ada perubahan belum di-commit"; exit 1; }
grep -q "versionName '$V'" app/build.gradle || { echo "versionName di build.gradle bukan $V"; exit 1; }
git ls-files | grep -qiE '\.jks$|keystore' && { echo "keystore ter-track git, berhenti"; exit 1; }
git rev-parse -q --verify "refs/tags/v$V" >/dev/null && { echo "tag v$V sudah ada"; exit 1; }
echo "Commit yang akan di-push:"; git log --oneline origin/main..HEAD
printf 'Push dan rilis v%s? ketik YA: ' "$V"; read a; [ "$a" = YA ] || { echo batal; exit 1; }
git tag "v$V" && git push origin main && git push origin "v$V"
echo "Selesai. Pantau: https://github.com/isac12345/Zani/actions"