# Docker Compose Deployment Workflow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Publish the backend image to Docker Hub with a stable `prod` tag and let the production app stack update itself through watchtower.

**Architecture:** GitHub Actions builds the backend image with Jib and pushes `:prod` to Docker Hub. The production server keeps running a single `compose.app.prod.yaml` stack containing `app`, `caddy`, and `watchtower`, and watchtower refreshes only the labeled `app` container.

**Tech Stack:** GitHub Actions, Jib, Docker Hub, Docker Compose, watchtower, Caddy

---

### Task 1: Define the production app stack

**Files:**
- Modify: `compose.app.prod.yaml`
- Modify: `.env.prod`
- Inspect: `src/main/resources/application-prod.yaml`

- [ ] **Step 1: Keep the production compose focused on the app-facing services**

  Ensure `compose.app.prod.yaml` contains `app`, `caddy`, and `watchtower`, and that only `app` is labeled for watchtower updates.

- [ ] **Step 2: Use a stable production image tag**

  Set the app image reference to a stable `:prod` tag so watchtower can detect updates without rewriting server-side files on each deployment.

- [ ] **Step 3: Make the runtime env template explicit**

  Ensure `.env.prod` includes `APP_DOMAIN`, `DB_HOST`, and the existing application credentials, while leaving `REDIS_HOST` and `MONGO_HOST` optional overrides.

### Task 2: Publish images instead of deploying from GitHub Actions

**Files:**
- Modify: `.github/workflows/deploy-compose.yml`

- [ ] **Step 1: Keep only the image build/push flow**

  Keep a single GitHub Actions job that publishes the Docker image.

- [ ] **Step 2: Push the stable `prod` tag**

  Derive the Docker Hub repository name from `GITHUB_REPOSITORY` and publish `${image_repo}:prod`.

- [ ] **Step 3: Limit GitHub secrets to registry credentials**

  Keep the workflow limited to Docker Hub registry credentials.

### Task 3: Verify the watchtower-based deployment flow

**Files:**
- Inspect: `.github/workflows/deploy-compose.yml`
- Inspect: `compose.app.prod.yaml`
- Inspect: `.env.prod`

- [ ] **Step 1: Review workflow references**

  Confirm the workflow only references Docker Hub credentials and the Jib publish command.

- [ ] **Step 2: Review compose wiring**

  Confirm `compose.app.prod.yaml` renders `app`, `caddy`, and `watchtower`, and that only `app` has the watchtower enable label.

- [ ] **Step 3: Review the bootstrap procedure**

  Confirm the server only needs `.env.prod`, `docker login`, and `docker compose --env-file .env.prod -f compose.app.prod.yaml up -d`.
