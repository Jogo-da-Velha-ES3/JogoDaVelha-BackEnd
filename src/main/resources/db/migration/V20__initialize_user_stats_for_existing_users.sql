-- V20: inicializa estatísticas para usuários existentes que não têm user_stats
INSERT INTO user_stats (user_id, matches_played, wins, current_win_streak, longest_win_streak)
SELECT id, 0, 0, 0, 0
FROM users
WHERE id NOT IN (SELECT user_id FROM user_stats);
