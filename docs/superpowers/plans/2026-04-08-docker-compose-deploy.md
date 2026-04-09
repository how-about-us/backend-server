# Docker Compose Deployment Workflow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create a draft GitHub Actions workflow that builds and publishes the backend image, then deploys it to a server where the application stack is separated from the data stack in Docker Compose.

**Architecture:** Use Spring Boot's `bootBuildImage` task to publish an OCI image to GHCR. Keep `postgres`, `redis`, and `mongodb` in a data-only Compose stack, and deploy the backend through a separate app-only Compose file that joins the shared production Docker network.

**Tech Stack:** GitHub Actions, Spring Boot Gradle plugin, GHCR, Docker Compose, SSH/SCP

---

### Task 1: Define the production runtime target

**Files:**
- Modify: `compose.prod.yaml`
- Create: `compose.app.prod.yaml`
- Modify: `src/main/resources/application-prod.yaml`

- [ ] **Step 1: Keep the data stack in `compose.prod.yaml`**

  Leave `postgres`, `redis`, and `mongodb` in the production overlay and pin the default network name so a separate app stack can join it.

- [ ] **Step 2: Add an app-only production Compose file**

  Create `compose.app.prod.yaml` with only the `app` service, `${APP_IMAGE}`, `prod` profile wiring, and an external network that matches the data stack network.

- [ ] **Step 3: Make production config support per-service hosts**

  Update `application-prod.yaml` so Redis and MongoDB can use `REDIS_HOST` and `MONGO_HOST`, while preserving `DB_HOST` as a fallback.

### Task 2: Add the deployment workflow draft

**Files:**
- Create: `.github/workflows/deploy-compose.yml`

- [ ] **Step 1: Build and publish the backend image**

  Create a workflow that triggers on pushes to `main`, logs in to GHCR, and runs `./gradlew bootBuildImage --imageName=... --publishImage`.

- [ ] **Step 2: Transfer Compose manifests to the server**

  Add an SCP step that uploads only the app deployment manifest to the remote deployment directory.

- [ ] **Step 3: Pull and restart only the `app` service**

  Add an SSH step that logs in to GHCR on the server, exports `APP_IMAGE`, and runs `docker compose --env-file .env.prod -f compose.app.prod.yaml pull app` followed by `docker compose ... up -d app`.

### Task 3: Verify draft integrity

**Files:**
- Inspect: `.github/workflows/deploy-compose.yml`
- Inspect: `compose.prod.yaml`
- Inspect: `compose.app.prod.yaml`
- Inspect: `src/main/resources/application-prod.yaml`

- [ ] **Step 1: Review secret names and remote paths**

  Confirm the workflow references a consistent set of GitHub secrets and variables.

- [ ] **Step 2: Review Compose variable wiring**

  Confirm `${APP_IMAGE}`, the shared Docker network, and the per-service host variables line up between the separated Compose stacks and Spring config.
