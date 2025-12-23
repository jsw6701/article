@echo off
REM ========================================
REM Docker 빌드를 위한 준비 스크립트 (Windows)
REM 프론트엔드와 백엔드를 한 컨텍스트로 모음
REM ========================================

setlocal enabledelayedexpansion

set SCRIPT_DIR=%~dp0
set FRONTEND_DIR=C:\front\news-briefing-frontend
set DEPLOY_CONTEXT=%SCRIPT_DIR%deploy-context

echo === Docker 빌드 준비 ===

REM 기존 deploy-context 정리
if exist "%DEPLOY_CONTEXT%" rmdir /s /q "%DEPLOY_CONTEXT%"
mkdir "%DEPLOY_CONTEXT%"

REM 백엔드 복사
echo 백엔드 복사 중...
mkdir "%DEPLOY_CONTEXT%\article"
xcopy /e /i /q "%SCRIPT_DIR%src" "%DEPLOY_CONTEXT%\article\src"
copy "%SCRIPT_DIR%build.gradle.kts" "%DEPLOY_CONTEXT%\article\" >nul
copy "%SCRIPT_DIR%settings.gradle.kts" "%DEPLOY_CONTEXT%\article\" >nul
xcopy /e /i /q "%SCRIPT_DIR%gradle" "%DEPLOY_CONTEXT%\article\gradle"

REM 프론트엔드 복사
echo 프론트엔드 복사 중...
mkdir "%DEPLOY_CONTEXT%\news-briefing-frontend"
xcopy /e /i /q "%FRONTEND_DIR%\src" "%DEPLOY_CONTEXT%\news-briefing-frontend\src"
copy "%FRONTEND_DIR%\package.json" "%DEPLOY_CONTEXT%\news-briefing-frontend\" >nul
copy "%FRONTEND_DIR%\package-lock.json" "%DEPLOY_CONTEXT%\news-briefing-frontend\" 2>nul
copy "%FRONTEND_DIR%\svelte.config.js" "%DEPLOY_CONTEXT%\news-briefing-frontend\" >nul
copy "%FRONTEND_DIR%\svelte.config.static.js" "%DEPLOY_CONTEXT%\news-briefing-frontend\" >nul
copy "%FRONTEND_DIR%\svelte.config.web.js" "%DEPLOY_CONTEXT%\news-briefing-frontend\" >nul
copy "%FRONTEND_DIR%\svelte.config.app.js" "%DEPLOY_CONTEXT%\news-briefing-frontend\" >nul
copy "%FRONTEND_DIR%\vite.config.ts" "%DEPLOY_CONTEXT%\news-briefing-frontend\" 2>nul
copy "%FRONTEND_DIR%\tsconfig.json" "%DEPLOY_CONTEXT%\news-briefing-frontend\" 2>nul

REM Dockerfile 복사
copy "%SCRIPT_DIR%Dockerfile" "%DEPLOY_CONTEXT%\" >nul

echo.
echo === 준비 완료 ===
echo deploy-context 폴더가 생성되었습니다.
echo.
echo Docker 빌드 실행:
echo   docker-compose up --build

endlocal
