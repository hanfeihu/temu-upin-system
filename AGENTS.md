# Repository Guidelines

## Project Structure & Module Organization

This repository is a mixed Java and frontend workspace for the TEMU Upin system. The root `pom.xml` is a Maven reactor for `temu-upin-sdk`, `temu-upin-minio-sdk`, `temu-upin-common`, `temu-upin-sync`, and `temu-upin-backend`. Backend source code lives in each module under `src/main/java`; backend resources and YAML configuration are in `temu-upin-backend/src/main/resources`; tests belong under matching `src/test/java` packages.

The current React Ant Design admin is `react-ant-6.3.5-admin/src`. Browser extension files are in `browser-extensions/`, and the 1688 Playwright worker is in `alibaba1688-playwright-worker/`.

## Build, Test, and Development Commands

- `mvn -pl temu-upin-backend -am clean package -DskipTests`: build the backend and required Maven modules.
- `mvn -pl temu-upin-backend -am test`: run backend tests.
- `mvn spring-boot:run -pl temu-upin-backend -Dspring-boot.run.profiles=local`: start the backend with the local profile.
- `cd react-ant-6.3.5-admin && npm run dev`: start the React admin Vite server.
- `cd react-ant-6.3.5-admin && npm run build`: run TypeScript checking and build the admin bundle.
- `cd alibaba1688-playwright-worker && npm run login` or `npm run run`: operate the 1688 browser worker.

## Coding Style & Naming Conventions

Use Java 21 conventions with 4-space indentation. Keep Java packages under `com.tminos.*`; use `PascalCase` for classes and `camelCase` for methods and fields. Name Spring classes by role, such as `*Controller`, `*Service`, and `*Repository`. Follow existing frontend patterns: React components in `PascalCase.tsx`, API helpers under `src/api`, and page code under `src/views`.

## Testing Guidelines

Name Java tests `*Test` and place them beside the related package under `src/test/java`. Prefer focused service, parser, and API-contract tests over debug mains. There is no unified frontend test runner; for UI changes, run the relevant `npm run build` and manually verify changed routes.

## Commit & Pull Request Guidelines

Recent commits use Conventional Commit prefixes such as `feat:`, `fix:`, and `chore:`. Keep commits scoped and behavior-focused, for example `fix: isolate temu publish side effects`. Pull requests should include a summary, validation commands, linked task or issue context, and screenshots for visible UI changes.

## Security & Configuration Tips

Do not commit real secrets from YAML files, browser sessions, or local runtime folders. Use examples or local-only ignored files for machine-specific configuration. Read `DEPLOYMENT.md` before touching deployment scripts or live host settings.
