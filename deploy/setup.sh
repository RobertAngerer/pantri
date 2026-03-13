#!/bin/bash
# Run this on the Oracle VM after SSH-ing in
set -e

# Install uv
curl -LsSf https://astral.sh/uv/install.sh | sh
source ~/.bashrc

# Install caddy
sudo apt update
sudo apt install -y debian-keyring debian-archive-keyring apt-transport-https curl
curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/gpg.key' | sudo gpg --dearmor -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg
curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt' | sudo tee /etc/apt/sources.list.d/caddy-stable.list
sudo apt update
sudo apt install -y caddy

# Copy project (run from your PC first):
# rsync -avz --exclude .venv --exclude __pycache__ . ubuntu@YOUR_IP:~/pantri/

# Setup systemd service
sudo cp ~/pantri/deploy/pantri.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable pantri
sudo systemctl start pantri

# Setup caddy — edit Caddyfile first with your domain/IP
sudo cp ~/pantri/deploy/Caddyfile /etc/caddy/Caddyfile
sudo systemctl restart caddy

echo "Done. API should be live."
