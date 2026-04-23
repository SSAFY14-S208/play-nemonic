# Jenkins Auto Deploy

## Why this layout

Given the EC2 restrictions, the safest setup is:

- Jenkins runs on a separate machine or shared Jenkins server.
- Jenkins connects to the EC2 host using SSH.
- The backend EC2 exposes only `22`, `80`, and `443` through UFW.
- Jenkins is not exposed on the backend EC2 on port `8080`.

This avoids adding a new daemon port to UFW and keeps the backend host simpler to recover.

## Jenkins prerequisites

Install or configure these on Jenkins:

- Pipeline plugin
- Git plugin
- SSH Agent plugin
- JDK 21 tool named `jdk21`

Create this credential in Jenkins:

- Kind: `SSH Username with private key`
- ID: `nemonic-ec2-ssh`
- Username: your EC2 SSH user, usually `ubuntu`
- Private key: the key that can SSH into `k14s208.p.ssafy.io`

## EC2 prerequisites

1. Run [deploy/bootstrap-ec2.sh](/C:/SSAFY/S14P31S208/deploy/bootstrap-ec2.sh:1) once on the EC2 host.
2. Create `/opt/nemonic/shared/.env.prod`.
3. Put production secrets into that file.
4. Confirm the Jenkins SSH key can log in to the EC2 host.

Example:

```bash
cat >/opt/nemonic/shared/.env.prod <<'EOF'
DOMAIN=k14s208.p.ssafy.io
NGINX_PORT=80
SERVER_PORT=8080
COMPOSE_PROJECT_NAME=nemonic-prod
DB_NAME=nemonic_prod
DB_USERNAME=nemonic
DB_PASSWORD=change_me
REDIS_PASSWORD=
EOF
```

## Job setup

Recommended job type:

- Multibranch Pipeline, or
- Pipeline job pointing at this repository

Recommended trigger:

- webhook on push to `dev` or `be/dev`

The included [Jenkinsfile](/C:/SSAFY/S14P31S208/Jenkinsfile:1) deploys only from `dev` and `be/dev` by default.

## Pipeline parameters

- `DEPLOY_HOST`: defaults to `k14s208.p.ssafy.io`
- `DEPLOY_USER`: defaults to `ubuntu`
- `DEPLOY_BASE_DIR`: defaults to `/opt/nemonic`
- `RUN_DEPLOY`: set `false` if you want test-only verification

## Deployment flow

1. Jenkins runs `./gradlew test` in `backend`.
2. Jenkins creates `release-<build>.tar.gz`.
3. Jenkins uploads the archive to `/opt/nemonic/incoming`.
4. Jenkins executes [deploy/remote-deploy.sh](/C:/SSAFY/S14P31S208/deploy/remote-deploy.sh:1) over SSH.
5. The EC2 host unpacks the release, runs Docker Compose, and executes smoke tests.
6. If the smoke test fails, the script redeploys the previous release automatically.

## Firewall notes

- Keep UFW enabled at all times.
- Allow only `22/tcp`, `80/tcp`, `443/tcp` on the backend EC2 unless you intentionally add another daemon.
- If you later decide to run Jenkins on EC2 anyway, do not expose the default `8080` port directly.
  Use a custom internal port and put it behind `443`, or add a deliberate UFW rule after validating the risk.
