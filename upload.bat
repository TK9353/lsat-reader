@echo off
cd /d "%~dp0"
echo ===== LSAT Reader: upload to GitHub =====
where git >nul 2>nul
if errorlevel 1 (
  echo [ERROR] Git is not installed. Install from https://git-scm.com/download/win and run this file again.
  pause
  exit /b 1
)
git config --global credential.gitHubAuthModes pat
if not exist .github\workflows mkdir .github\workflows
copy /Y ci\build.yml .github\workflows\build.yml >nul
if not exist .git git init -b main
git add -A
git -c user.name=TK9353 -c user.email=poweringyo2@gmail.com commit -q -m "Update LSAT Reader"
git remote get-url origin >nul 2>nul || git remote add origin https://github.com/TK9353/lsat-reader.git
echo.
echo If a token window appears, paste your lsat-reader token there.
git push -u origin main --force
echo.
if errorlevel 1 (echo [FAILED] Push failed. Check the message above.) else (echo [DONE] Upload complete.)
pause
