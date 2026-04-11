# Docker Compose Deployment Workflow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the backend image on GitHub-hosted runners and deploy it on the production server through a GitHub Actions `self-hosted` runner.

**Architecture:** The workflow builds a SHA-tagged Docker image with Jib, then a `self-hosted` deploy job writes `.env.prod` from the `ENV_PROD` secret, syncs the compose assets into the server deployment directory, and runs `docker compose` locally on the server. The deploy path updates the `app` service every time, waits until the container healthcheck succeeds against `/actuator/health`, and only recreates `caddy` when `compose.app.prod.yaml` or `infra/caddy/Caddyfile` changed in the pushed revision.

**Tech Stack:** GitHub Actions, self-hosted runner, Jib, Docker Hub, Docker Compose, Caddy

---

### Task 1: Define the production compose stack for explicit deployment

**Files:**
- Modify: `compose.app.prod.yaml`
- Inspect: `src/main/resources/application-prod.yaml`

- [ ] **Step 1: Keep the production compose focused on runtime services**

  Ensure `compose.app.prod.yaml` contains `app` and `caddy`, with no `watchtower` service.

- [ ] **Step 2: Restore runtime image injection**

  Set the app image reference to `${APP_IMAGE}` so the deploy job can pin the exact SHA-tagged image that was built in the workflow.

- [ ] **Step 3: Keep caddy configuration minimal**

  Pass only `APP_DOMAIN` into `caddy` rather than the full `.env.prod` file.

- [ ] **Step 4: Add an app healthcheck**

  Use a compose `healthcheck` that calls `http://127.0.0.1:8080/actuator/health` from inside the app container.

### Task 2: Restore the self-hosted deployment workflow

**Files:**
- Modify: `.github/workflows/deploy-compose.yml`

- [ ] **Step 1: Build on GitHub-hosted runners**

  Keep the `build` job on `ubuntu-latest`, derive the Docker Hub image name from `GITHUB_REPOSITORY`, and push a SHA-tagged image with Jib.

- [ ] **Step 2: Deploy on the default self-hosted runner label**

  Add a `deploy` job that runs on `self-hosted`, restores `.env.prod` from the `ENV_PROD` secret, and copies `compose.app.prod.yaml` plus `infra/caddy/Caddyfile` into the deployment directory.

- [ ] **Step 3: Limit restarts by service**

  Always run `docker compose pull app` and `docker compose up -d app`, and only run `docker compose up -d caddy` when `compose.app.prod.yaml` or `infra/caddy/Caddyfile` changed.

- [ ] **Step 4: Fail the deploy when the app never becomes healthy**

  After `up -d app`, poll the container health status and print the app logs before failing when the healthcheck does not reach `healthy`.

### Task 3: Keep deployment documentation aligned

**Files:**
- Inspect: `docs/ai/decisions/20260409-1659-self-hosted-runner-deploy.md`
- Modify: `docs/superpowers/specs/2026-04-10-watchtower-compose-deploy-design.md`
- Modify: `docs/superpowers/plans/2026-04-10-watchtower-compose-deploy.md`

- [ ] **Step 1: Preserve the active source of truth**

  Keep the self-hosted deployment decision document as the active reference.

- [ ] **Step 2: Mark watchtower-only documents as superseded**

  Update the watchtower design and implementation plan docs so they no longer contradict the active deployment model.

### Task 4: Verify the workflow and compose wiring

**Files:**
- Inspect: `.github/workflows/deploy-compose.yml`
- Inspect: `compose.app.prod.yaml`
- Inspect: `docs/superpowers/specs/2026-04-10-watchtower-compose-deploy-design.md`
- Inspect: `docs/superpowers/plans/2026-04-10-watchtower-compose-deploy.md`

- [ ] **Step 1: Validate the workflow structure**

  Confirm the workflow contains both `build` and `deploy` jobs, and that `deploy` uses `self-hosted` plus the `ENV_PROD` secret.

- [ ] **Step 2: Validate the compose structure**

  Confirm `compose.app.prod.yaml` renders only `app` and `caddy`, and that `APP_IMAGE` is required at deploy time.

- [ ] **Step 3: Validate document consistency**

  Confirm the watchtower plan/spec files are clearly marked as superseded and now point readers to the self-hosted decision document.
