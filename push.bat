@echo off
rem RegMan 一键推送 (Windows/Git Bash 环境用法同 push.sh)
rem 用法: push.bat ^<GitHub用户名^> ^<Token^> [仓库名]
set USER=%1& set TOKEN=%2& set REPO=%3
if "%REPO%"=="" set REPO=regman-android
git init -b main 2>nul
git add -A
git commit -m "update"
git remote remove origin 2>nul
git remote add origin https://%USER%:%TOKEN%@github.com/%USER%/%REPO%.git
git push -u origin main
echo 完成后访问 https://github.com/%USER%/%REPO%/actions 查看编译
