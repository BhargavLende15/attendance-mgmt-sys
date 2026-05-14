#!/usr/bin/env bash
# ============================================================
#  deploy.sh – runs on EC2 to pull image from ECR and start container
#  Called by CD Jenkinsfile via SSH
# ============================================================
set -euo pipefail

# ── Required env vars (passed by Jenkins) ─────────────────────────────
: "${AWS_ACCESS_KEY_ID:?Need AWS_ACCESS_KEY_ID}"
: "${AWS_SECRET_ACCESS_KEY:?Need AWS_SECRET_ACCESS_KEY}"
: "${AWS_REGION:?Need AWS_REGION}"
: "${ECR_REGISTRY:?Need ECR_REGISTRY}"
: "${ECR_REPOSITORY:?Need ECR_REPOSITORY}"
: "${IMAGE_TAG:?Need IMAGE_TAG}"
: "${APP_PORT:=8086}"
: "${APP_ENV:=Staging}"
: "${INSTANCE_ID:=unknown}"

FULL_IMAGE="${ECR_REGISTRY}/${ECR_REPOSITORY}:${IMAGE_TAG}"
CONTAINER_NAME="attendance-app"

echo "========================================================"
echo "  Deploying: ${FULL_IMAGE}"
echo "  Instance : ${INSTANCE_ID}"
echo "  Env      : ${APP_ENV}"
echo "  Port     : ${APP_PORT}"
echo "========================================================"

# ── Ensure Docker is running ───────────────────────────────────────────
if ! docker info >/dev/null 2>&1; then
    echo "Starting Docker daemon..."
    sudo systemctl start docker
    sleep 3
fi

# ── ECR Login ─────────────────────────────────────────────────────────
echo "==> Logging in to ECR..."
export AWS_ACCESS_KEY_ID AWS_SECRET_ACCESS_KEY AWS_REGION
aws ecr get-login-password --region "${AWS_REGION}" | \
    docker login --username AWS --password-stdin "${ECR_REGISTRY}"

# ── Pull new image ─────────────────────────────────────────────────────
echo "==> Pulling image ${FULL_IMAGE}..."
docker pull "${FULL_IMAGE}"

# ── Graceful stop of existing container ───────────────────────────────
if docker ps -q -f name="${CONTAINER_NAME}" | grep -q .; then
    echo "==> Stopping existing container..."
    docker stop "${CONTAINER_NAME}" --time 30 || true
fi

if docker ps -aq -f name="${CONTAINER_NAME}" | grep -q .; then
    echo "==> Removing old container..."
    docker rm "${CONTAINER_NAME}" || true
fi

# ── Start new container ────────────────────────────────────────────────
echo "==> Starting new container on port ${APP_PORT}..."
docker run -d \
    --name "${CONTAINER_NAME}" \
    --restart unless-stopped \
    -p "${APP_PORT}:8086" \
    -e APP_ENV="${APP_ENV}" \
    -e INSTANCE_ID="${INSTANCE_ID}" \
    -e SPRING_PROFILES_ACTIVE="${APP_ENV,,}" \
    --memory="512m" \
    --cpus="0.5" \
    "${FULL_IMAGE}"

# ── Wait for health ────────────────────────────────────────────────────
echo "==> Waiting for application to become healthy..."
MAX_ATTEMPTS=20
ATTEMPT=1
until docker exec "${CONTAINER_NAME}" \
        wget -qO- "http://localhost:8086/attendance/status" \
        >/dev/null 2>&1; do
    if [ ${ATTEMPT} -ge ${MAX_ATTEMPTS} ]; then
        echo "ERROR: Application did not start within timeout!"
        docker logs "${CONTAINER_NAME}" --tail 50
        exit 1
    fi
    echo "  Attempt ${ATTEMPT}/${MAX_ATTEMPTS}: not ready yet, waiting 5s..."
    sleep 5
    ATTEMPT=$((ATTEMPT + 1))
done

echo ""
echo "========================================================"
echo "  ✅ Deployment SUCCESSFUL on ${INSTANCE_ID}"
echo "  Container: $(docker ps --filter name=${CONTAINER_NAME} --format '{{.Status}}')"
echo "  URL: http://$(curl -s http://169.254.169.254/latest/meta-data/public-ipv4):${APP_PORT}/attendance/status"
echo "========================================================"

# ── Cleanup unused images (keep last 3) ───────────────────────────────
echo "==> Cleaning up old images..."
docker image prune -f --filter "label!=keep" || true
