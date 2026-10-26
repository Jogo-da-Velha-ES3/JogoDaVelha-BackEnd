ALTER TABLE rooms
    ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now();

CREATE INDEX ix_rooms_waiting_created_at ON rooms(created_at)
    WHERE status = 'WAITING' AND player2_id IS NULL;

-- Salas já existentes recebem a data da migration como created_at.