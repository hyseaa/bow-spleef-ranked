# Bow Spleef Ranked — API

Ranked API for Hypixel Bow Spleef played through Discord: players, verification, matches, OpenSkill ratings, seasons, rank titles, queue, duel challenges, parties. Its main client is the Discord bot (`../bot`); a `hysea-server` source also posts matches (see `../docs/architecture/integration.md`).

**Documentation and decisions live in the workspace repository** (`../workspace/`, reached through the links of the parent folder: `../AGENTS.md`, `../docs/`, ADR-0016): read `../AGENTS.md` first — golden rules, workflow, conventions and the documentation index. API reference: `../docs/api/`; bot ↔ API contract: `../docs/architecture/integration.md`; decisions: `../docs/architecture/adr/`; latest context: the newest file in `../docs/journal/`.

If `../AGENTS.md` is missing, this repository was cloned on its own: put it in a folder next to the workspace repository (`workspace/`) and the other repositories, then run `node workspace/tools/workspace/layout.mjs link` there (`docs/operations/workspace-layout.md` of the workspace).

## Quick reference
- Java 21, Spring Boot 3.3, Maven wrapper, MyBatis (annotations, no XML), Flyway, PostgreSQL 16, springdoc.
- Build and test: `./mvnw verify` (Checkstyle in `validate`; integration tests need Docker). One class: `./mvnw test -Dtest=MatchServiceTest`.
- Run: `docker compose up -d postgres`, load `.env` into the shell, `./mvnw spring-boot:run` (details: `../docs/api/operations.md`, or the `verify` skill).
- Never edit a released migration (next: see `../docs/api/domain-model.md`). Never rename an error `code`.
- Commits: English, `feat(NNN): …` / `fix(KI-NN): …` / `docs: …`, referencing the spec, issue or ADR.
