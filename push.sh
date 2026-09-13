#!/data/data/com.termux/files/usr/bin/bash
# RegMan 一键推送 GitHub：自动建仓库 + 推送
# 用法:
#   ./push.sh <GitHub用户名> <Personal Access Token> [仓库名，默认 regman-android]
set -e

USER="$1"; TOKEN="$2"; REPO="${3:-regman-android}"
if [ -z "$USER" ] || [ -z "$TOKEN" ]; then
  echo "用法: ./push.sh <GitHub用户名> <Token> [仓库名]"
  exit 1
fi

# ── 1. 检测仓库是否存在，不存在则自动创建 ──
CODE=$(curl -s -o /dev/null -w "%{http_code}" -u "${USER}:${TOKEN}" \
  "https://api.github.com/repos/${USER}/${REPO}")
if [ "$CODE" = "404" ]; then
  echo "仓库不存在，正在创建 ${USER}/${REPO} ..."
  curl -s -u "${USER}:${TOKEN}" -X POST "https://api.github.com/user/repos" \
    -d "{\"name\":\"${REPO}\",\"private\":false,\"auto_init\":false}" > /dev/null
  echo "已创建。"
elif [ "$CODE" != "200" ]; then
  echo "GitHub API 返回 $CODE —— 请检查用户名和 Token（需勾选 repo 权限）"
  exit 1
else
  echo "仓库 ${USER}/${REPO} 已存在。"
fi

# ── 2. 本地提交 ──
[ -d .git ] || git init -b main
git add -A
git commit -m "update: $(date '+%Y-%m-%d %H:%M')" || echo "无变更可提交"

# ── 3. 推送 ──
git remote remove origin 2>/dev/null || true
git remote add origin "https://${USER}:${TOKEN}@github.com/${USER}/${REPO}.git"
if ! git push -u origin main; then
  echo "首次推送失败，尝试合并远程已有内容..."
  git pull origin main --rebase --allow-unrelated-histories
  git push -u origin main
fi

echo
echo "推送成功。"
echo "当前工作流为【手动触发】，push 不会自动编译。"
echo "请手动触发: https://github.com/${USER}/${REPO}/actions"
echo "  → 选 Android CI → Run workflow → 填版本号(如 0.1.1) → Run"
echo "编译完成后在 Artifacts 下载对应版本的 APK。"
