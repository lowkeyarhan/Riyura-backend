#!/bin/bash
set -e

# --- RIYURA CONFIGURATION ---
EC2_USER="ec2-user"
EC2_IP="15.207.99.129"
KEY_PATH="/Users/lowkeyarhan/Desktop/Riyura-backend/riyura-backend-key.pem"
REMOTE_DIR="/home/$EC2_USER/riyura-backend"
# ----------------------------

echo "📦 Archiving Riyura backend code layout..."
# Compress source structure while skipping bulky local compilation folders
tar --exclude='./target' --exclude='./.git' --exclude='./.env' -czf riyura-src.tar.gz .
tar --exclude='./riyura-src.tar.gz' --exclude='./target' --exclude='./.git' --exclude='./.env' -czf riyura-src.tar.gz .

echo "📤 Tunneling codebase to AWS Amazon Linux engine..."
scp -i "$KEY_PATH" riyura-src.tar.gz "$EC2_USER@$EC2_IP:$REMOTE_DIR/"

echo "🔄 Orchestrating remote production environment..."
ssh -i "$KEY_PATH" "$EC2_USER@$EC2_IP" << EOF
    cd $REMOTE_DIR
    tar -xzf riyura-src.tar.gz
    rm riyura-src.tar.gz

    # Enterprise Env Sync: Map the local container target to your production configurations
    cp .env.prod .env

    # Initialize the external Next.js network bridge if missing
    docker network inspect riyura-frontend_default >/dev/null 2>&1 || \
    docker network create riyura-frontend_default

    echo "🚀 Triggering multi-stage optimized build layers..."
    docker compose -f docker-compose.yml -f docker-compose.prod.yml --env-file .env.prod up -d --build

    echo "🧹 Purging stale dangling images..."
    docker image prune -f
EOF

rm riyura-src.tar.gz
echo "✨ Riyura project is officially live on AWS production!"
