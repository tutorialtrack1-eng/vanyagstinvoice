# Hosting the sync server at api.blitzbook.co.in

The app (`Sync.SERVER_URL`) and the published portal (`DEFAULT_SERVER_URL` in `webportal/js/sync.js`) are set
to `https://api.blitzbook.co.in`. Whatever machine answers there with `server/server.js` becomes the sync
server for everyone. It must be reachable from the internet, stay on, and keep `server/data` safe.

## Quickest: Render, deployed from the GitHub repository

`render.yaml` at the repository root describes the service. In the Render dashboard choose **New -> Blueprint**,
connect `tutorialtrack1-eng/vanyagstinvoice`, and Apply: Render builds from `master`, redeploys on every push,
keeps the books on a persistent disk and issues the https certificate. Then add at GoDaddy:
`CNAME  api  ->  blitzbook-api.onrender.com` (Render shows the exact target under the service's Custom Domains).
Check with `curl https://api.blitzbook.co.in/api/ping`. The persistent disk needs the paid Starter plan;
the free plan has no disk and loses the data.

## Your own machine instead

### 1. A machine

Any small Linux VPS works (1 vCPU, 1 GB RAM is plenty): Hostinger, DigitalOcean, Linode, AWS Lightsail,
Hetzner... Pick Ubuntu 22.04 or 24.04 and note its public IP.

## 2. DNS at GoDaddy

Add one record: `A   api   <the VPS IP>`. Wait until `nslookup api.blitzbook.co.in` shows it.

## 3. Install (Docker, the simplest way)

On the VPS:

```
curl -fsSL https://get.docker.com | sh
git clone https://github.com/tutorialtrack1-eng/vanyagstinvoice.git /opt/blitzbook
cd /opt/blitzbook
docker compose -f server/deploy/docker-compose.yml up -d --build
```

That starts the server and Caddy, which fetches the https certificate for `api.blitzbook.co.in` by itself.
Check: `curl https://api.blitzbook.co.in/api/ping` → `{"app":"BlitzBook","ok":true,...}`.

Update later: `cd /opt/blitzbook && git pull && docker compose -f server/deploy/docker-compose.yml up -d --build`.
Data is in the Docker volume `blitzbook-data`; back it up with
`docker run --rm -v deploy_blitzbook-data:/data -v $PWD:/out alpine tar czf /out/blitzbook-data.tgz /data`.

## 3b. Without Docker

```
sudo apt install -y nodejs git caddy
sudo useradd -r -s /usr/sbin/nologin blitzbook
sudo git clone https://github.com/tutorialtrack1-eng/vanyagstinvoice.git /opt/blitzbook
sudo mkdir -p /var/lib/blitzbook && sudo chown blitzbook /var/lib/blitzbook
sudo cp /opt/blitzbook/server/deploy/blitzbook.service /etc/systemd/system/ && sudo systemctl enable --now blitzbook
```

and in `/etc/caddy/Caddyfile`:

```
api.blitzbook.co.in {
    reverse_proxy localhost:8080
}
```

then `sudo systemctl reload caddy`.

## 4. Check from the outside

- `https://api.blitzbook.co.in/api/ping` answers.
- Portal: https://blitzbook.co.in → the chip in the top bar says **Synced** after login.
- App: Sync in the side menu says **Synced** (no address needs to be typed once `SERVER_URL` is built in).

## Admin

```
docker compose -f server/deploy/docker-compose.yml exec blitzbook node server/server.js users
docker compose -f server/deploy/docker-compose.yml exec blitzbook node server/server.js passwd <mobile> <new-password>
```

## Running it from a PC instead (temporary)

`server/windows/install-task.ps1` keeps it running on a Windows PC, but a home PC has no public https
address. A Cloudflare Tunnel (free) can publish it as `api.blitzbook.co.in` while the PC is on: move the
domain's nameservers to Cloudflare, install `cloudflared`, then
`cloudflared tunnel --hostname api.blitzbook.co.in --url http://localhost:8090`. The books are only
reachable while that PC and tunnel are running, so use a VPS for real use.
