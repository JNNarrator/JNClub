#!/bin/bash
# JNClub 全套停止脚本
echo "停止 JNClub 全家桶..."
for port in 5173 19005 8080; do
  PID=$(lsof -ti:$port 2>/dev/null)
  if [ -n "$PID" ]; then
    kill $PID 2>/dev/null && echo "  端口 $port 已停止 (PID $PID)"
  else
    echo "  端口 $port 未运行"
  fi
done
echo "全部已停止"
