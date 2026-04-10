# Watchtower Compose Deploy Design

## Status

Superseded on 2026-04-10.

## Note

This design is no longer the active deployment direction for this repository.

- The current deployment model uses a GitHub Actions `self-hosted` runner for the deploy step.
- The current source of truth is [docs/ai/decisions/20260409-1659-self-hosted-runner-deploy.md](/home/minbros/projects/java/how-about-us-backend/docs/ai/decisions/20260409-1659-self-hosted-runner-deploy.md).
- The active workflow is [deploy-compose.yml](/home/minbros/projects/java/how-about-us-backend/.github/workflows/deploy-compose.yml).

## Reason

The repository was temporarily moved to a watchtower-based deployment design, but the active implementation has been returned to the self-hosted runner approach so deployment timing stays explicit in GitHub Actions.
