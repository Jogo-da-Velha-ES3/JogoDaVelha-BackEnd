ALTER TABLE games
    ADD COLUMN player1_reward_coins INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN player2_reward_coins INTEGER NOT NULL DEFAULT 0;

UPDATE games
SET player1_reward_coins = CASE
        WHEN end_reason = 'FORFEIT_TIMEOUT' AND winner_id = player1_id THEN 30
        WHEN end_reason = 'FORFEIT_TIMEOUT' THEN 0
        WHEN winner_id IS NULL THEN 20
        WHEN winner_id = player1_id THEN 50
        ELSE 10
    END,
    player2_reward_coins = CASE
        WHEN end_reason = 'FORFEIT_TIMEOUT' AND winner_id = player2_id THEN 30
        WHEN end_reason = 'FORFEIT_TIMEOUT' THEN 0
        WHEN winner_id IS NULL THEN 20
        WHEN winner_id = player2_id THEN 50
        ELSE 10
    END
WHERE status = 'FINISHED' AND rewards_granted = TRUE;
