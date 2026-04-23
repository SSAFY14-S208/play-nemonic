# EC2 Deployment

## Recommended architecture

- Run Jenkins outside the EC2 instance.
- Let Jenkins deploy over SSH to the EC2 host.
- Keep EC2 UFW open only for `22/tcp`, `80/tcp`, and `443/tcp`.
- Do not expose the Jenkins web UI from this backend EC2 unless you intentionally proxy it behind `443`.

This matches the project constraints better than opening Jenkins's default `8080` port on the server.

## 1. Bootstrap the EC2 host

Run this once on the EC2 host:

```bash
chmod +x deploy/bootstrap-ec2.sh
./deploy/bootstrap-ec2.sh
```

What it does:

- installs Docker, the Docker Compose plugin, `curl`, and `ufw`
- enables Docker
- creates `/opt/nemonic/{incoming,releases,shared}`
- keeps UFW enabled and allows only `22`, `80`, and `443`

## 2. Configure production secrets

Create `/opt/nemonic/shared/.env.prod` on the EC2 host using `deploy/.env.prod.example` as the template.

Important values:

- `DOMAIN=k14s208.p.ssafy.io`
- `NGINX_PORT=80`
- `DB_PASSWORD=<strong password>`
- `COMPOSE_PROJECT_NAME=nemonic-prod`

## 3. Manual deploy

If you want to deploy without Jenkins once, from the project root on the EC2 host:

```bash
chmod +x deploy/deploy.sh deploy/smoke-test.sh
ENV_FILE=/opt/nemonic/shared/.env.prod ./deploy/deploy.sh
ENV_FILE=/opt/nemonic/shared/.env.prod ./deploy/smoke-test.sh
```

## 4. Jenkins deploy

Use the repository root [Jenkinsfile](/C:/SSAFY/S14P31S208/Jenkinsfile:1) together with [deploy/remote-deploy.sh](/C:/SSAFY/S14P31S208/deploy/remote-deploy.sh:1).

The pipeline does this:

- checks out the repo
- runs `backend` tests with JDK 21
- creates a release archive from the current commit
- uploads the archive to the EC2 host over SSH
- unpacks the release into `/opt/nemonic/releases/<release-name>`
- switches `/opt/nemonic/current`
- runs Docker Compose build and smoke tests
- rolls back to the previous release automatically if the smoke test fails

Detailed Jenkins setup is documented in [deploy/JENKINS.md](/C:/SSAFY/S14P31S208/deploy/JENKINS.md:1).

## Notes

- PostgreSQL and Redis stay private inside the Docker network.
- The backend is exposed externally only through Nginx.
- Add TLS on `443` later with a reverse proxy or certificate-managed Nginx when you are ready.
