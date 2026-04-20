# AGENTS.md

## Repo boundaries
- Root `pom.xml` is a Maven reactor (`packaging=pom`), not the runnable app. Modules: `temu-upin-sdk`, `temu-upin-common`, `temu-upin-sync`, `temu-upin-backend`.
- The only Spring Boot entrypoint is `temu-upin-backend/src/main/java/com/tminos/productscene/ProductSceneApplication.java`.
- `temu-upin-backend` is the runtime process, but `temu-upin-common` and `temu-upin-sync` both contribute Spring controllers/services/entities into that same process via backend dependencies. Do not treat them as isolated services.
- Frontend is separate in `temu-upin-frontend` and talks to the backend through `/api`.

## Entry points that explain wiring
- Frontend bootstrap: `temu-upin-frontend/src/main.js`
- Frontend routes: `temu-upin-frontend/src/router/index.js`
- Frontend API client: `temu-upin-frontend/src/api/index.js` (`baseURL: '/api'`)
- Main product import/publish flow: `temu-upin-backend/src/main/java/com/tminos/productscene/controller/ProductCollectionController.java`
- Sync task APIs: `temu-upin-sync/src/main/java/com/tminos/productscene/sync/controller/SyncTaskController.java`
- Sync auto scheduling: `temu-upin-sync/src/main/java/com/tminos/productscene/sync/service/SyncAutoScheduler.java`

## Commands agents are likely to guess wrong
- Build backend with dependent modules from repo root: `mvn -pl temu-upin-backend -am clean package -DskipTests`
- Run backend locally: `mvn spring-boot:run -Dspring-boot.run.profiles=local`
- Run built backend jar: `java -jar temu-upin-backend/target/product-scene-1.0.0.jar --spring.profiles.active=local`
- Frontend dev server: `npm run dev` in `temu-upin-frontend`
- Frontend prod build: `npm run build` in `temu-upin-frontend`
- Deploy entrypoint: `./deploy.sh [all|backend|frontend]`

## Runtime and deploy quirks
- Use reactor builds for backend work. Building a leaf module in isolation will often miss required sibling modules.
- `deploy.sh all` runs in this order: backend build -> backend deploy -> frontend build -> frontend deploy.
- `deploy.sh` requires a local `.deploy.env` copied from `.deploy.env.example` and depends on `ssh`, `scp`, `sshpass`, `mvn`, and `npm`.
- Default deploy settings assume `BACKEND_MODULE=temu-upin-backend`, `BACKEND_JAR_NAME=product-scene-1.0.0.jar`, and `SPRING_PROFILE=dev` unless `.deploy.env` overrides them.
- Frontend Vite dev proxy forwards `/api` and `/uploads` to `http://localhost:8080`; that is dev-server behavior only, not proof of production routing.

## Behavior that is easy to misread
- `ProductSceneApplication` enables both async work and scheduling. Background workers are part of normal runtime behavior, not optional add-ons.
- `temu-upin-sync` exposes `/api/sync/*`; many other backend/common controllers expose `/api/platform/*`, plus `/api/products`, `/api/channels`, `/api/upload`, `/api/ocr`, etc. Check controller annotations before assuming a module owns an endpoint namespace.
- `SyncAutoScheduler` runs every minute and decides whether to create tasks by matching each shop's configured `sync_cron`.
- `application.yml` disables Spring Security auto-configuration. Do not assume standard Spring Security behavior exists here.
- JPA uses `spring.jpa.hibernate.ddl-auto: update`; schema changes are not managed by Flyway/Liquibase in this repo.

## Verification reality
- Frontend `package.json` only defines `dev`, `build`, and `preview`. There is no repo-defined frontend `test`, `lint`, or `typecheck` script.
- Backend test coverage is minimal from what is checked in. The only discovered file under `src/test` is `temu-upin-backend/src/test/java/com/tminos/productscene/OssDebugMainTest.java`, which is a debug `main`, not a normal JUnit suite.
- `.github/` does not contain normal CI workflows; it only contains a `java-upgrade/` artifact directory. Do not assume CI will catch mistakes for you.

## Sensitive files
- `temu-upin-backend/src/main/resources/application-local.yml`, `application-dev.yml`, and `application.yml` contain concrete-looking secrets/default credentials. Never copy their values into commits, PR text, or chat.
- Channel-key bootstrap files live under `temu-upin-backend/`, not repo root: `temu-upin-backend/.secrets.env.example` and `temu-upin-backend/init-channel-keys.example.sh`.
