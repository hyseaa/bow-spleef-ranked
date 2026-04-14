CREATE TABLE rank_title (
    min_elo  INT         PRIMARY KEY,
    name     VARCHAR(50) NOT NULL
);

INSERT INTO rank_title (min_elo, name) VALUES
    (100,  'Bronze I'),
    (200,  'Bronze II'),
    (300,  'Bronze III'),
    (400,  'Silver I'),
    (500,  'Silver II'),
    (600,  'Silver III'),
    (700,  'Gold I'),
    (800,  'Gold II'),
    (900,  'Gold III'),
    (1000, 'Platinum I'),
    (1100, 'Platinum II'),
    (1250, 'Platinum III'),
    (1400, 'Diamond I'),
    (1550, 'Diamond II'),
    (1700, 'Diamond III'),
    (1900, 'Champion I'),
    (2100, 'Champion II'),
    (2300, 'Champion III'),
    (2550, 'Grand Champion I'),
    (2800, 'Grand Champion II'),
    (3100, 'Grand Champion III'),
    (3500, 'Rep God');
