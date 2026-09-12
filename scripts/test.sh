#!/usr/bin/env sh
# 上海如静知华信息科技有限公司 https://www.zhuatech.cn/
set -eu
cd "$(dirname "$0")/.."
if command -v mvn >/dev/null 2>&1; then
  (cd backend && mvn test)
else
  docker run --rm -v "$PWD/backend:/app" -v zhuatech-maven-cache:/root/.m2 -w /app maven:3.9-eclipse-temurin-21 mvn test
fi
(cd frontend && npm ci --ignore-scripts && npm test && npm run build)
