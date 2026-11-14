---
name: verify
description: Recette pour lancer et vérifier l'API Bow Spleef Ranked de bout en bout (PostgreSQL docker + Spring Boot local + appels HTTP).
---

# Vérifier l'API Bow Spleef Ranked

## Lancer

1. Docker Desktop doit tourner (`docker info`). Sinon : `Start-Process "C:\Program Files\Docker\Docker\Docker Desktop.exe"` et attendre ~30s.
2. Une fois, depuis `../workspace` : `node tools/local-stack/stack.mjs init` (crée `../local/api.env`), puis `node tools/local-stack/stack.mjs db` — PostgreSQL local `bsr-local-db` sur le port **5401** (ADR-0017, ADR-0018).
3. Lancer l'API : `./mvnw spring-boot:run` (profil dev : `application-dev.properties` importe `../local/api.env`, aucune variable à exporter). Elle écoute sur **8090**. Démarrage ~5s ; les migrations Flyway s'appliquent au boot (visible dans le log). Puis `node tools/local-stack/stack.mjs seed` (types de jeu et saison de test).

## Piloter

- Tous les endpoints exigent le header **`X-Api-Key`** (valeur = `API_KEY` de `../local/api.env`, `local-dev-key` par défaut). Sans lui : 403 vide.
- Swagger : `http://localhost:8090/swagger-ui.html`.
- `GET /api/v1/players/leaderboard` prend **`seasonId`** (pas `gameType`).
- Créer des joueurs : ils sont auto-créés par `POST /api/v1/matches` (insertIfAbsent). Pour les flows Discord (duel challenges), leur donner un `discord_id` directement en SQL :
  `docker exec bsr-local-db psql -U bowspleef_ranked -d bowspleef_ranked -c "UPDATE player SET discord_id = '...' WHERE uuid = '...';"`

## Pièges

- Le webhook (`webhook.url`, par défaut le bot local `http://localhost:3000`) est injoignable quand le bot local ne tourne pas : l'envoi échoue en arrière-plan après le commit (connexion 3 s, lecture 10 s) et est seulement journalisé : la requête n'attend plus (KI-07 corrigé). Prévoir des timeouts HTTP ≥ 120s.
- Au premier boot après une migration qui pose `elo_dirty`, le scheduler recompute les saisons (~1s pour 130 matchs) — attendre la ligne "ELO recompute complete" avant de mesurer des elos.
- Nettoyer les données de test à la fin (matchs, saison, game types, joueurs `verif-%`) : les FK cascade depuis `match` et `duel_challenge`.
