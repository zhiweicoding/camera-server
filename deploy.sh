#!/bin/bash

SERVER_IP="8.129.3.8"
SERVER_USER="root"
SERVER_PASS="Dawn4938"
REMOTE_DIR="/root/camera-server"
JAR_NAME="camera-server-1.0.0.jar"

echo "=== 开始打包 ==="
mvn clean package -DskipTests
if [ $? -ne 0 ]; then
    echo "打包失败"
    exit 1
fi

echo "=== 上传到服务器 ==="
sshpass -p "$SERVER_PASS" scp target/$JAR_NAME $SERVER_USER@$SERVER_IP:$REMOTE_DIR/

echo "=== 重启服务 ==="
sshpass -p "$SERVER_PASS" ssh $SERVER_USER@$SERVER_IP "cd $REMOTE_DIR && ./restart-camera.sh"

echo "=== 部署完成 ==="
