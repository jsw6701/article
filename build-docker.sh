#!/bin/bash
# ========================================
# Docker 빌드를 위한 준비 스크립트
# 프론트엔드와 백엔드를 한 컨텍스트로 모음
# ========================================

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
FRONTEND_DIR="${FRONTEND_DIR:-C:/front/news-briefing-frontend}"
DEPLOY_CONTEXT="$SCRIPT_DIR/deploy-context"

echo "=== Docker 빌드 준비 ==="

# 기존 deploy-context 정리
rm -rf "$DEPLOY_CONTEXT"
mkdir -p "$DEPLOY_CONTEXT"

# 백엔드 복사
echo "백엔드 복사 중..."
mkdir -p "$DEPLOY_CONTEXT/article"
cp -r "$SCRIPT_DIR/src" "$DEPLOY_CONTEXT/article/"
cp "$SCRIPT_DIR/build.gradle.kts" "$DEPLOY_CONTEXT/article/"
cp "$SCRIPT_DIR/settings.gradle.kts" "$DEPLOY_CONTEXT/article/"
cp -r "$SCRIPT_DIR/gradle" "$DEPLOY_CONTEXT/article/"

# 프론트엔드 복사
echo "프론트엔드 복사 중..."
mkdir -p "$DEPLOY_CONTEXT/news-briefing-frontend"
cp -r "$FRONTEND_DIR/src" "$DEPLOY_CONTEXT/news-briefing-frontend/"
cp "$FRONTEND_DIR/package.json" "$DEPLOY_CONTEXT/news-briefing-frontend/"
cp "$FRONTEND_DIR/package-lock.json" "$DEPLOY_CONTEXT/news-briefing-frontend/" 2>/dev/null || true
cp "$FRONTEND_DIR/svelte.config.js" "$DEPLOY_CONTEXT/news-briefing-frontend/"
cp "$FRONTEND_DIR/svelte.config.static.js" "$DEPLOY_CONTEXT/news-briefing-frontend/"
cp "$FRONTEND_DIR/svelte.config.web.js" "$DEPLOY_CONTEXT/news-briefing-frontend/"
cp "$FRONTEND_DIR/svelte.config.app.js" "$DEPLOY_CONTEXT/news-briefing-frontend/"
cp "$FRONTEND_DIR/vite.config.ts" "$DEPLOY_CONTEXT/news-briefing-frontend/" 2>/dev/null || true
cp "$FRONTEND_DIR/tsconfig.json" "$DEPLOY_CONTEXT/news-briefing-frontend/" 2>/dev/null || true

# Dockerfile 복사
cp "$SCRIPT_DIR/Dockerfile" "$DEPLOY_CONTEXT/"

echo "=== 준비 완료 ==="
echo "deploy-context 폴더가 생성되었습니다."
echo ""
echo "Docker 빌드 실행:"
echo "  docker-compose up --build"
