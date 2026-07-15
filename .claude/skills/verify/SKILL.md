---
name: verify
description: Recette pour lancer et vérifier l'API player-scores de bout en bout (PostgreSQL docker + Spring Boot local + appels HTTP).
---

# Vérifier l'API player-scores

## Lancer

1. Docker Desktop doit tourner (`docker info`). Sinon : `Start-Process "C:\Program Files\Docker\Docker\Docker Desktop.exe"` et attendre ~30s.
2. `docker compose up -d postgres` — PostgreSQL sur le port hôte **5400** (profil dev : `application-dev.properties`).
3. Lancer l'API avec les variables du `.env` (Spring ne lit pas `.env` tout seul) :
   ```powershell
   Get-Content .env | ForEach-Object { if ($_ -match '^([^#=]+)=(.*)$') { [System.Environment]::SetEnvironmentVariable($Matches[1].Trim(), $Matches[2].Trim()) } }; .\mvnw.cmd spring-boot:run
   ```
   Démarrage ~5s ; les migrations Flyway s'appliquent au boot (visible dans le log).

## Piloter

- Tous les endpoints exigent le header **`X-Api-Key`** (valeur = `API_KEY` du `.env`). Sans lui : 403 vide.
- Swagger : `http://localhost:8080/swagger-ui.html`.
- `GET /api/v1/players/leaderboard` prend **`seasonId`** (pas `gameType`).
- Créer des joueurs : ils sont auto-créés par `POST /api/v1/matches` (insertIfAbsent). Pour les flows Discord (duel challenges), leur donner un `discord_id` directement en SQL :
  `docker exec playerscores-db psql -U playerscores -d playerscores -c "UPDATE player SET discord_id = '...' WHERE uuid = '...';"`

## Pièges

- Le webhook (`webhook.url` vers backend.ranked.bowspleef.net) est injoignable en dev : chaque `POST /matches` ranked et chaque recompute attend ~20s avant l'erreur (attrapée, sans conséquence). Prévoir des timeouts HTTP ≥ 120s.
- Au premier boot après une migration qui pose `elo_dirty`, le scheduler recompute les saisons (~1s pour 130 matchs) — attendre la ligne "ELO recompute complete" avant de mesurer des elos.
- Nettoyer les données de test à la fin (matchs, saison, game types, joueurs `verif-%`) : les FK cascade depuis `match` et `duel_challenge`.
