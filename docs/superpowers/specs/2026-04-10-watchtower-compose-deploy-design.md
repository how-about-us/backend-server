# Watchtower Compose Deploy Design

## Goal

Remove the GitHub Actions dependency on a self-hosted runner for deployment. GitHub Actions should only build and publish the backend image, and the production server should update the application automatically through `watchtower`.

## Current Context

- The repository already has a draft workflow at `.github/workflows/deploy-compose.yml`.
- The current draft performs both image publishing and deployment through a `self-hosted` runner.
- Production runtime is split so that the application stack uses `compose.app.prod.yaml`.
- The application image is currently derived from the commit SHA, which is inconvenient for `watchtower` because automatic updates are simpler against a stable tag.

## Chosen Approach

Use a single production app compose file that includes `app`, `caddy`, and `watchtower`.

- GitHub Actions builds and pushes a Docker Hub image tagged as `prod`.
- The server runs `compose.app.prod.yaml` persistently.
- `watchtower` polls Docker Hub on a schedule and updates only the `app` container.
- `caddy` remains in the same compose file for operational simplicity, but it is not updated automatically by `watchtower`.

## Rejected Approaches

### Keep self-hosted deployment job

- Rejected because the current problem is runner availability.
- This keeps the most failure-prone part of the flow in place.

### Split watchtower into a separate compose file

- Rejected because it adds another server-side artifact to manage.
- The user explicitly preferred a simpler operational model.

## File-Level Design

### `.github/workflows/deploy-compose.yml`

Convert the workflow into build/push only.

- Keep the trigger on `main` and manual dispatch.
- Remove the `deploy` job that runs on `self-hosted`.
- Publish a stable Docker Hub image tag ending in `:prod`.
- Keep Docker Hub authentication through GitHub secrets.

### `compose.app.prod.yaml`

Use one compose file for the production app stack.

- `app` uses the stable `:prod` image reference.
- `app` keeps `env_file: .env.prod` and the existing Spring production settings.
- `app` receives a `com.centurylinklabs.watchtower.enable=true` label so only this container is auto-updated.
- `caddy` remains unchanged except for any minimal compatibility changes required by the combined stack.
- Add a `watchtower` service using `containrrr/watchtower`.
- Mount `/var/run/docker.sock` into `watchtower`.
- Configure `watchtower` to run with label filtering so it only updates labeled containers.

### `.env.prod`

Continue to hold runtime application configuration for the production app stack.

- Must include `APP_DOMAIN`.
- Must include database and Mongo credentials already required by Spring.
- Must include `DB_HOST`.
- May optionally include `REDIS_HOST` and `MONGO_HOST` when those hosts differ from `DB_HOST`.
- Does not need image metadata because the compose file will reference a stable tag directly.

### Documentation

Update related docs so the deployment model matches the code.

- Update `AGENTS.md` only if the project-level command or deployment guidance becomes inaccurate.
- Update the existing draft plan under `docs/superpowers/plans/2026-04-08-docker-compose-deploy.md` if it is kept as an active implementation reference.
- Add a short deployment note describing the server bootstrap command for the app stack if no existing doc already covers it.

## Runtime Flow

1. A commit is pushed to `main`.
2. GitHub Actions builds the application image and pushes `dockerhub-user/repository:prod`.
3. The production server is already running `compose.app.prod.yaml`.
4. `watchtower` checks the registry on its configured interval.
5. When a new `:prod` image digest is detected, `watchtower` pulls the image and recreates only the labeled `app` container.
6. `caddy` continues proxying to the restarted `app` container.

## Configuration Decisions

### Stable tag

Use `prod` as the deployment tag.

- This matches the user's preference.
- It avoids rewriting server-side compose files on every deployment.
- It works naturally with `watchtower`.

### Update scope

Only `app` is updated automatically.

- `caddy` should stay pinned and change only when intentionally modified.
- `watchtower` should run with label filtering enabled.

### Watchtower placement

Keep `watchtower` in the same `compose.app.prod.yaml`.

- The server can be bootstrapped with one compose command.
- The repository remains the source of truth for the running production app stack.

## Failure Handling

### Image push fails

- GitHub Actions fails early.
- The server keeps running the current image because `watchtower` sees no new digest.

### Registry authentication is missing on the server

- `watchtower` cannot pull the updated private image.
- The design assumes the server is logged in to Docker Hub ahead of time with `docker login`, or the image is public.

### App startup fails after update

- `watchtower` will have replaced the container with the new image attempt.
- Recovery is operational: fix the image and push a corrected `:prod` tag, or manually pin and restart.
- No automatic rollback is introduced in this design.

## Testing Strategy

### Configuration verification

- Validate the workflow YAML syntax.
- Validate the compose file with `docker compose -f compose.app.prod.yaml config`.

### Behavioral verification

- Confirm the workflow pushes the `:prod` tag.
- Confirm the compose file renders `watchtower` with label filtering enabled.
- Confirm only `app` has the watchtower enable label.

### Manual production bootstrap

The server-side bootstrap after this change should be a one-time command sequence:

1. Place `.env.prod` on the server.
2. Run `docker login` for Docker Hub if the repository is private.
3. Run `docker compose --env-file .env.prod -f compose.app.prod.yaml up -d`.

## Open Questions

- None for the requested scope.
