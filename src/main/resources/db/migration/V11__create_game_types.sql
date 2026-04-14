CREATE TABLE game_types (
    name         VARCHAR(50)  PRIMARY KEY,
    display_name VARCHAR(100) NOT NULL,
    ranked       BOOLEAN      NOT NULL DEFAULT FALSE
);
