# Bow Spleef Ranked — API

Ranked API for Hypixel Bow Spleef played through Discord: players, verification, matches, OpenSkill ratings, seasons, rank titles, queue, duel challenges, parties. Its main client is the Discord bot (`../bot`); a `hysea-server` source also posts matches (see `../workspace/docs/architecture/integration.md`).

**Documentation and decisions live in the workspace repository** (`../workspace/`, next to this repository, ADR-0016): read `../workspace/AGENTS.md` first — golden rules, workflow, conventions and the documentation index. API reference: `../workspace/docs/api/`; bot ↔ API contract: `../workspace/docs/architecture/integration.md`; decisions: `../workspace/docs/architecture/adr/`; latest context: the newest file in `../workspace/docs/journal/`.

If `../workspace/` is missing, this repository was cloned on its own: clone the workspace repository next to it as `workspace/` (set-up in its `docs/operations/workspace-layout.md`). For work across repositories, or to use the commands `/spec`, `/journal`…, open the Claude Code session in `../workspace/`.

## Quick reference
- Java 21, Spring Boot 3.3, Maven wrapper, MyBatis (annotations, no XML), Flyway, PostgreSQL 16, springdoc.
- Build and test: `./mvnw verify` (Checkstyle in `validate`; integration tests need Docker). One class: `./mvnw test -Dtest=MatchServiceTest`.
- Run: `docker compose up -d postgres`, load `.env` into the shell, `./mvnw spring-boot:run` (details: `../workspace/docs/api/operations.md`, or the `verify` skill).
- Never edit a released migration (next: see `../workspace/docs/api/domain-model.md`). Never rename an error `code`.
- Commits: English, `feat(NNN): …` / `fix(KI-NN): …` / `docs: …`, referencing the spec, issue or ADR.
