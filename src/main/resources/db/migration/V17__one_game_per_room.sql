-- A room is the idempotency key for DraftCompleted and has at most one match.
CREATE UNIQUE INDEX ux_games_room_id ON games(room_id);
