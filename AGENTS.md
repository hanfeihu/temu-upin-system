# AGENTS.md

## Repo shape
- This repo is split across a Maven multi-module backend and a separate Vite frontend.
- Root `pom.xml` aggregates `temu-upin-sdk`, `temu-upin-common`, `temu-upin-sync`, and `temu-upin-backend`.
- The only Spring Boot runtime entrypoint is `temu-upin-backend/src/main/java/com/tminos/productscene/ProductSceneApplication.java`.
- `temu-upin-backend` is the runnable app; `temu-upin-common` and `temu-upin-sync` contribute Spring-managed code into that same process via backend module dependencies.
- Frontend lives in `temu-upin-frontend` and talks to the backend through `/api`.

## Entry points and wiring
- Frontend bootstrap is `temu-upin-frontend/src/main.js`; routes are centralized in `temu-upin-frontend/src/router/index.js`.
- Frontend API client is `temu-upin-frontend/src/api/index.js` with `baseURL: '/api'`.
- Vite dev proxy in `temu-upin-frontend/vite.config.js` forwards `/api` and `/uploads` to `http://localhost:8080`; this is a dev-server behavior, not production routing.
- Backend serves mixed controller namespaces from multiple modules: `temu-upin-backend` exposes most `/api/platform/*`, `/api/products`, `/api/channels`, `/api/upload`, etc., while `temu-upin-sync` exposes `/api/sync/*`.

## Canonical commands
- Build backend module with its dependent modules: `mvn -pl temu-upin-backend -am clean package -DskipTests`
- Run backend locally with the documented profile: `mvn spring-boot:run -Dspring-boot.run.profiles=local`
- Alternative backend run: `java -jar temu-upin-backend/target/product-scene-1.0.0.jar --spring.profiles.active=local`
- Frontend dev server: `npm run dev` in `temu-upin-frontend`
- Frontend production build: `npm run build` in `temu-upin-frontend`
- Full deploy entrypoint: `./deploy.sh [all|backend|frontend]`

## Command and workflow quirks
- Root is a Maven reactor project (`packaging=pom`), so targeted backend work should usually use `-pl ... -am` instead of building from a leaf module in isolation.
- `deploy.sh all` runs in this order: backend build -> backend deploy -> frontend build -> frontend deploy. Do not assume frontend-first deployment.
- `deploy.sh` requires a local `.deploy.env` copied from `.deploy.env.example` and depends on `ssh`, `scp`, `sshpass`, `mvn`, and `npm`.
- The deploy script expects `BACKEND_MODULE=temu-upin-backend` and `BACKEND_JAR_NAME=product-scene-1.0.0.jar` unless `.deploy.env` overrides them.

## Runtime and config gotchas
- `temu-upin-backend/src/main/resources/application.yml` sets `spring.profiles.active: local` by default. If behavior differs from expectations, check whether CLI or env overrides are active.
- Local/dev profiles expect PostgreSQL and Redis (`application-local.yml` and `application-dev.yml`).
- JPA is configured with `spring.jpa.hibernate.ddl-auto: update`; schema changes are not managed here by Flyway/Liquibase.
- Security auto-configuration is explicitly disabled in `application.yml`; do not assume Spring Security behavior is active.
- Background processing is important in this repo: the backend enables scheduling, and sync/auto-publish behavior is controlled by worker config rather than only request/response flows.

## Testing and verification reality
- The frontend `package.json` defines only `dev`, `build`, and `preview`. There are no repo-defined frontend `test`, `lint`, or `typecheck` scripts.
- Backend testing appears to rely on default Maven/Spring Boot behavior; no custom CI or hook pipeline was found in the repo.
- There is no normal `.github/workflows` CI setup checked in for this repo, so do not infer CI-only safeguards.

## Sensitive files and safety
- `application-local.yml` and `application-dev.yml` contain concrete-looking local credentials/secrets. Treat those files as sensitive and do not copy values into commits, logs, PR text, or chat responses.
- `temu-upin-backend/.secrets.env.example` and `init-channel-keys.example.sh` define a local channel-key bootstrap flow. Review those before changing channel initialization behavior.

## Where to inspect first for common work
- Product collection import/publish flow: `temu-upin-backend/src/main/java/com/tminos/productscene/controller/ProductCollectionController.java`
- Sync task APIs and workflow: `temu-upin-sync/src/main/java/com/tminos/productscene/sync/controller/SyncTaskController.java`
- Auto sync scheduling behavior: `temu-upin-sync/src/main/java/com/tminos/productscene/sync/service/SyncAutoScheduler.java`
