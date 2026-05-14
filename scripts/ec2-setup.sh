#!/usr/bin/env bash
# ============================================================
#  ec2-setup.sh – Run once on each EC2 instance to install dependencies
#  Amazon Linux 2023 / Amazon Linux 2
# ============================================================
set -euo pipefail

echo "========================================================"
echo "  EC2 Instance Setup for Attendance Management System"
echo "========================================================"

# ── Update system ──────────────────────────────────────────────────────
echo "==> Updating system packages..."
sudo yum update -y

# ── Install Docker ─────────────────────────────────────────────────────
echo "==> Installing Docker..."
sudo yum install -y docker
sudo systemctl start docker
sudo systemctl enable docker
sudo usermod -aG docker ec2-user
newgrp docker

# ── Install AWS CLI v2 ─────────────────────────────────────────────────
echo "==> Installing AWS CLI..."
curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o "awscliv2.zip"
unzip -o awscliv2.zip
sudo ./aws/install --update
rm -rf aws awscliv2.zip

# ── Verify installations ───────────────────────────────────────────────
echo "==> Verifying installations..."
docker --version
aws --version

# ── Configure firewall (open app port) ────────────────────────────────
echo "==> Configuring firewall for port 8086..."
sudo firewall-cmd --zone=public --add-port=8086/tcp --permanent 2>/dev/null || true
sudo firewall-cmd --reload 2>/dev/null || true

echo "========================================================"
echo "  ✅ EC2 setup complete!"
echo "  IMPORTANT: Log out and back in for docker group to take effect"
echo "========================================================"
