# Repository Guidelines

## Project Structure & Module Organization
`ragent` is a multi-module Java 17 repository with a separate React frontend. Backend modules live at the repo root: `bootstrap/` contains the main Spring Boot app and business flows, `framework/` holds shared infrastructure and config, `infra-ai/` wraps model and rerank integrations, and `mcp-server/` exposes MCP tools. Frontend code lives in `frontend/` with app code under `frontend/src/`, shared UI in `frontend/@/components/ui`, and static assets in `frontend/public/`. Docs, SQL, and deployment files are under `docs/`, `resources/`, and `assets/`.

## Build, Test, and Development Commands
- `./mvnw clean test`: run backend unit and integration tests across all Maven modules.
- `./mvnw -pl bootstrap spring-boot:run`: start the main RAG application locally.
- `./mvnw -pl mcp-server spring-boot:run`: run the MCP server module only.
- `cd frontend && npm install`: install frontend dependencies.
- `cd frontend && npm run dev`: start the Vite dev server on `localhost:5173` with `/api` proxying to `http://localhost:9090`.
- `cd frontend && npm run build`: create a production frontend build.
- `cd frontend && npm run lint` / `npm run format`: enforce ESLint and Prettier rules.

## Coding Style & Naming Conventions
Java uses 4-space indentation, package names under `com.nageoffer.ai.ragent`, and standard Spring naming such as `*Controller`, `*Service`, `*Mapper`, and `*Properties`. Spotless runs during Maven `compile`; do not fight generated formatting or license headers. Frontend TypeScript uses 2-space indentation, semicolons, double quotes, and `printWidth: 100` from `frontend/.prettierrc`. Prefer PascalCase for React components, camelCase for hooks/services, and keep shared aliases imported through `@/`.

## Testing Guidelines
Backend tests are in `*/src/test/java` and mostly use JUnit with Spring Boot test support and Mockito. Name tests `*Test` or `*Tests`, matching existing files such as `ConversationMessageServiceTests` and `ScheduleRefreshProcessorTest`. Run focused checks with `./mvnw -pl bootstrap -Dtest=ClassName test`. The frontend currently emphasizes linting and manual verification; use `frontend/TESTING.md` for proxy and login checks when validating UI changes.

## Commit & Pull Request Guidelines
Recent history follows Conventional Commits with scoped prefixes, for example `feat(core): ...`, `fix(chat): ...`, and `refactor(infra): ...`. Keep commit messages short, imperative, and module-scoped where possible. PRs should describe the user-visible change, list affected modules, mention config or schema updates, and include screenshots for `frontend/` work. Link the related issue or task when one exists.

## Configuration & Environment Tips
Check `resources/docker/` for local dependency stacks and `resources/database/` for schema and seed SQL. Keep secrets and vendor credentials out of source control; use local Spring configuration files or environment variables instead.
