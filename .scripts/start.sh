#!/bin/bash
# JNClub 全套启动脚本
# 三服务各自独立 Terminal 窗口，Ctrl+C 关单个窗口即可
set -e
REPO_DIR="/Users/jiangnan/Documents/Workspace/JNClub"
SSO_DIR="/Users/jiangnan/Documents/Workspace/JN_SSO"

echo "=== 构建（跳过测试）==="
cd "$SSO_DIR" && mvn clean package -DskipTests -q && echo "  SSO 构建完成"
cd "$REPO_DIR" && mvn clean package -DskipTests -q && echo "  JNClub 构建完成"

echo ""
echo "=== 启动三服务 ==="

# SSO (8080)
echo "→ SSO :8080"
lsof -ti:8080 | xargs kill 2>/dev/null || true
osascript -e "tell app \"Terminal\" to do script \"echo '=== SSO Server (8080) ==='; cd $SSO_DIR; java -jar sso-server/target/sso-server-1.0.0.jar\""

sleep 5

# JNClub (19005)
echo "→ JNClub :19005"
lsof -ti:19005 | xargs kill 2>/dev/null || true
osascript -e "tell app \"Terminal\" to do script \"echo '=== JNClub (19005) ==='; cd $REPO_DIR; java -jar jnclub-gateway/target/jnclub-gateway-1.0.0-SNAPSHOT.jar\""

sleep 5

# 前端 (5173)
echo "→ 前端 :5173"
lsof -ti:5173 | xargs kill 2>/dev/null || true
osascript -e "tell app \"Terminal\" to do script \"echo '=== 前端 Dev (5173) ==='; cd $REPO_DIR/jnclub-web; pnpm dev\""

sleep 5

echo ""
echo "=== 验证 ==="
curl -s -o /dev/null -w "SSO 8080:    HTTP %{http_code}\n" http://localhost:8080/sso/getAppList
curl -s -o /dev/null -w "JNClub 19005: HTTP %{http_code}\n" http://localhost:19005/api/auth/check
curl -s -o /dev/null -w "前端 5173:   HTTP %{http_code}\n" http://localhost:5173/jnclub/
echo ""
echo "打开浏览器: open http://localhost:5173/jnclub/"
