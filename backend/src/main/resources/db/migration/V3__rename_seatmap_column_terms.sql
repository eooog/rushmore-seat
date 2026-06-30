ALTER TABLE tile
    RENAME COLUMN column_start_no TO col_start_no;

ALTER TABLE tile
    RENAME COLUMN column_end_no TO col_end_no;

ALTER TABLE tile
    RENAME CONSTRAINT ck_tile_column_range TO ck_tile_col_range;

ALTER TABLE seat
    RENAME COLUMN seat_no TO col_label;

ALTER TABLE seat
    RENAME COLUMN column_no TO col_no;

ALTER TABLE seat
    RENAME CONSTRAINT ck_seat_column_no TO ck_seat_col_no;
