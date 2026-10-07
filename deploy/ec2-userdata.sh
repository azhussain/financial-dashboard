#!/bin/bash
# Stocks Explorer backend bootstrap — Amazon Linux 2023 user-data.
# Requires: instance role with ECR read + ssm:GetParameter on /stocks-explorer/*.
# Overridable via environment: AWS_REGION, ECR_REPO, IMAGE_TAG, SSM_PARAM, APP_PORT.
set -euo pipefail

AWS_REGION="${AWS_REGION:-us-east-1}"
APP_PORT="${APP_PORT:-8080}"
ECR_REPO="${ECR_REPO:-stocks-explorer-backend}"
IMAGE_TAG="${IMAGE_TAG:-latest}"
SSM_PARAM="${SSM_PARAM:-/stocks-explorer/OPENAI_API_KEY}"

ACCOUNT_ID=$(curl -s http://169.254.169.254/latest/dynamic/instance-identity/document | grep -oP '"accountId"\s*:\s*"\K[^"]+')
ECR_URI="${ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/${ECR_REPO}"

dnf install -y docker
systemctl enable --now docker

aws ecr get-login-password --region "${AWS_REGION}" \
  | docker login --username AWS --password-stdin "${ECR_URI%%/*}"

OPENAI_API_KEY=$(aws ssm get-parameter --name "${SSM_PARAM}" --with-decryption \
  --region "${AWS_REGION}" --query Parameter.Value --output text || true)

docker rm -f stocks-api 2>/dev/null || true
docker pull "${ECR_URI}:${IMAGE_TAG}"
docker run -d --name stocks-api --restart unless-stopped \
  -p "${APP_PORT}:8080" \
  -e PORT=8080 \
  -e OPENAI_API_KEY="${OPENAI_API_KEY}" \
  "${ECR_URI}:${IMAGE_TAG}"

for i in $(seq 1 30); do
  curl -sf "http://localhost:${APP_PORT}/api/health" && break
  sleep 2
done
