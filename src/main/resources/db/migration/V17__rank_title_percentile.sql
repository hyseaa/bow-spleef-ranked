DROP TABLE rank_title;

CREATE TABLE rank_title (
    min_percentile NUMERIC(5, 2) PRIMARY KEY,
    name           VARCHAR(50)   NOT NULL
);

INSERT INTO rank_title (min_percentile, name) VALUES
    (0.00,  'Bronze I'),
    (5.00,  'Bronze II'),
    (10.00, 'Bronze III'),
    (15.00, 'Silver I'),
    (20.00, 'Silver II'),
    (26.00, 'Silver III'),
    (32.00, 'Gold I'),
    (37.00, 'Gold II'),
    (42.00, 'Gold III'),
    (47.00, 'Platinum I'),
    (53.00, 'Platinum II'),
    (59.00, 'Platinum III'),
    (65.00, 'Diamond I'),
    (71.00, 'Diamond II'),
    (77.00, 'Diamond III'),
    (82.00, 'Champion I'),
    (86.00, 'Champion II'),
    (90.00, 'Champion III'),
    (93.00, 'Grand Champion I'),
    (96.00, 'Grand Champion II'),
    (98.50, 'Grand Champion III'),
    (99.95, 'Rep God');

ALTER TABLE player_season_elo ADD COLUMN rank_title VARCHAR(50);
