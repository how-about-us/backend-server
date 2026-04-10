# Watchtower Compose Deploy Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the self-hosted deployment job with a Docker Hub push flow and a watchtower-managed production app stack.

**Architecture:** GitHub Actions will build and publish a stable Docker image tag named `prod`. The production server will run a single compose file containing `app`, `caddy`, and `watchtower`, and watchtower will refresh only the labeled `app` service when the registry digest changes.

**Tech Stack:** GitHub Actions, Docker Hub, Jib, Docker Compose, watchtower, Caddy

---

### Task 1: Simplify the GitHub Actions workflow

**Files:**
- Modify: `.github/workflows/deploy-compose.yml`

- [ ] **Step 1: Read the current workflow and confirm the self-hosted deployment section exists**

Run:

```bash
sed -n '1,260p' .github/workflows/deploy-compose.yml
```

Expected: A `deploy` job exists with `runs-on: self-hosted` and writes `.env.prod` plus runs `docker compose`.

- [ ] **Step 2: Replace the workflow with a build/push-only version**

Write this content into `.github/workflows/deploy-compose.yml`:

```yaml
#file: noinspection SpellCheckingInspection
name: Publish API Image

on:
  push:
    branches:
      - main
      - feature/ci-cd
  workflow_dispatch:

permissions:
  contents: read

jobs:
  build:
    runs-on: ubuntu-latest

    steps:
      - name: Check out source
        uses: actions/checkout@v4

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
          cache: gradle

      - name: Derive image metadata
        id: meta
        shell: bash
        run: |
          image_repo="${GITHUB_REPOSITORY,,}"
          echo "image=${image_repo}:prod" >> "$GITHUB_OUTPUT"

      - name: Make Gradle executable
        run: chmod +x ./gradlew

      - name: Build and push image
        env:
          DOCKERHUB_USERNAME: ${{ secrets.DOCKERHUB_USERNAME }}
          DOCKERHUB_TOKEN: ${{ secrets.DOCKERHUB_TOKEN }}
        run: |
          ./gradlew jib \
            -Djib.to.image="${{ steps.meta.outputs.image }}" \
            -Djib.to.auth.username="$DOCKERHUB_USERNAME" \
            -Djib.to.auth.password="$DOCKERHUB_TOKEN"
```

- [ ] **Step 3: Verify the workflow no longer includes server-side deployment**

Run:

```bash
rg -n "self-hosted|ENV_PROD|DEPLOY_PATH|docker compose" .github/workflows/deploy-compose.yml
```

Expected: No matches.

### Task 2: Add watchtower to the production app compose stack

**Files:**
- Modify: `compose.app.prod.yaml`

- [ ] **Step 1: Read the current compose file**

Run:

```bash
sed -n '1,260p' compose.app.prod.yaml
```

Expected: `app` uses `${APP_IMAGE}` and there is no `watchtower` service.

- [ ] **Step 2: Replace image wiring and add watchtower-managed update scope**

Write this content into `compose.app.prod.yaml`:

```yaml
name: how-about-us-app

services:
  app:
    image: ${APP_IMAGE:-minbros/how-about-us-backend:prod}
    env_file:
      - .env.prod
    environment:
      SPRING_PROFILES_ACTIVE: prod
      SERVER_FORWARD_HEADERS_STRATEGY: framework
    labels:
      com.centurylinklabs.watchtower.enable: "true"
    expose:
      - '8080'
    restart: unless-stopped
    logging:
      driver: "json-file"
      options:
        max-size: "10m"
        max-file: "3"

  caddy:
    image: caddy:2.8-alpine
    environment:
      APP_DOMAIN: ${APP_DOMAIN}
    ports:
      - '80:80'
      - '443:443'
    volumes:
      - ./infra/caddy/Caddyfile:/etc/caddy/Caddyfile:ro
      - caddy-data:/data
      - caddy-config:/config
    depends_on:
      - app
    restart: unless-stopped
    logging:
      driver: "json-file"
      options:
        max-size: "10m"
        max-file: "3"

  watchtower:
    image: containrrr/watchtower:1.7.1
    command:
      - --label-enable
      - --interval
      - "30"
      - --cleanup
    volumes:
      - /var/run/docker.sock:/var/run/docker.sock
    restart: unless-stopped
    logging:
      driver: "json-file"
      options:
        max-size: "10m"
        max-file: "3"

volumes:
  caddy-data:
  caddy-config:
```

- [ ] **Step 3: Verify only the app service is watchtower-enabled**

Run:

```bash
rg -n "watchtower.enable|containrrr/watchtower|APP_IMAGE" compose.app.prod.yaml
```

Expected:
- One `watchtower.enable` label under `app`
- One `containrrr/watchtower` service
- `APP_IMAGE` uses the `minbros/how-about-us-backend:prod` default

### Task 3: Align runtime env and deployment documentation

**Files:**
- Modify: `.env.prod`
- Modify: `docs/superpowers/plans/2026-04-08-docker-compose-deploy.md`

- [ ] **Step 1: Add the missing production runtime keys to `.env.prod`**

Update `.env.prod` so it includes these keys:

```env
APP_DOMAIN=
DB_HOST=
DB_NAME=
DB_USER=
DB_PASSWORD=
MONGO_DB=
MONGO_USER=
MONGO_PASSWORD=
# Optional when Redis is not on DB_HOST
REDIS_HOST=
# Optional when MongoDB is not on DB_HOST
MONGO_HOST=
```

- [ ] **Step 2: Update the old draft plan so it no longer documents self-hosted deployment**

Rewrite the goal and task descriptions in `docs/superpowers/plans/2026-04-08-docker-compose-deploy.md` so they describe:

```markdown
- GitHub Actions publishes `:prod` to Docker Hub
- `compose.app.prod.yaml` contains `watchtower`
- No `self-hosted`, SSH, or SCP deployment steps remain
```

- [ ] **Step 3: Verify the runtime env template and docs no longer imply the old deployment model**

Run:

```bash
rg -n "self-hosted|SSH|SCP|ENV_PROD|APP_DOMAIN|DB_HOST|REDIS_HOST|MONGO_HOST" .env.prod docs/superpowers/plans/2026-04-08-docker-compose-deploy.md
```

Expected:
- `.env.prod` contains `APP_DOMAIN` and `DB_HOST`
- the old plan no longer contains self-hosted deployment instructions

### Task 4: Validate configuration and capture the final diff

**Files:**
- Inspect: `.github/workflows/deploy-compose.yml`
- Inspect: `compose.app.prod.yaml`
- Inspect: `.env.prod`
- Inspect: `docs/superpowers/plans/2026-04-08-docker-compose-deploy.md`

- [ ] **Step 1: Render the compose configuration with safe local overrides**

Run:

```bash
APP_IMAGE=minbros/how-about-us-backend:prod \
APP_DOMAIN=example.com \
DB_HOST=db \
DB_NAME=app \
DB_USER=user \
DB_PASSWORD=pass \
MONGO_DB=app \
MONGO_USER=user \
MONGO_PASSWORD=pass \
docker compose --env-file .env.prod -f compose.app.prod.yaml config
```

Expected: Exit code `0` and rendered services for `app`, `caddy`, and `watchtower`.

- [ ] **Step 2: Confirm the workflow references only Docker Hub credentials**

Run:

```bash
rg -n "DOCKERHUB_USERNAME|DOCKERHUB_TOKEN|ENV_PROD|self-hosted" .github/workflows/deploy-compose.yml
```

Expected:
- `DOCKERHUB_USERNAME` and `DOCKERHUB_TOKEN` remain
- `ENV_PROD` and `self-hosted` do not appear

- [ ] **Step 3: Review the final diff**

Run:

```bash
git diff -- .github/workflows/deploy-compose.yml compose.app.prod.yaml .env.prod docs/superpowers/plans/2026-04-08-docker-compose-deploy.md
```

Expected: The diff shows only the watchtower-based deployment migration.
