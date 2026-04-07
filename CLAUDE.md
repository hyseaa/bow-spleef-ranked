# API - Player Scores

API REST Spring Boot pour enregistrer et consulter des scores de joueurs.

## Stack
- Java 21, Spring Boot 3.x
- Maven
- PostgreSQL
- MyBatis (mybatis-spring-boot-starter) pour l'accès aux données
- Flyway pour les migrations de base de données
- Swagger / OpenAPI (springdoc-openapi)
- Docker Compose pour l'environnement local
- JUnit 5 + Mockito pour les tests

## Commandes

```bash
# Lancer l'environnement local (PostgreSQL)
docker compose up -d

# Compiler
./mvnw clean package

# Lancer l'API
./mvnw spring-boot:run

# Tests
./mvnw test

# Swagger UI (une fois l'API démarrée)
# http://localhost:8080/swagger-ui.html
```

## Architecture
- `controller/` — endpoints REST
- `service/` — logique métier
- `mapper/` — interfaces MyBatis (accès données)
- `model/` — POJOs représentant les données
- `dto/` — objets de transfert (request/response)
- `resources/mapper/` — fichiers XML de requêtes SQL

## Conventions
- Utiliser des DTOs distincts des modèles
- Les services ne retournent jamais des modèles directement (toujours des DTOs)
- Les requêtes SQL complexes vont dans des fichiers XML (`resources/mapper/*.xml`), les simples peuvent être en annotation `@Select`/`@Insert`/etc.
- Les migrations Flyway sont dans `resources/db/migration/` avec la convention `V{version}__{description}.sql` (ex: `V1__create_players.sql`)
- Les tests d'intégration utilisent une BDD H2 in-memory (profil `test`)
- Nommage : camelCase pour les champs Java, snake_case pour les colonnes SQL
- Endpoints en kebab-case : `/api/v1/player-scores`

## Variables d'environnement
Définies dans `docker-compose.yml` et `application.properties` :
- `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`
- `SPRING_DATASOURCE_URL`

## À ne pas faire
- Ne pas exposer les entités JPA directement dans les réponses HTTP
- Ne pas committer de secrets ou mots de passe réels
