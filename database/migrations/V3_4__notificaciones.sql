-- V3.4 — S5 Notificaciones (HU-016)
-- Autor: Kevin Rojas · PostgreSQL 15+ (Supabase) · Modelo: página 12 de Confluence · Rollback: rollback/V3__rollback.sql

-- ------------------------------------------------------------------ S5: notificaciones (HU-016)
CREATE TABLE experiencia.notificacion (
    id          uuid PRIMARY KEY,
    id_usuario  uuid NOT NULL,
    tipo        text NOT NULL,
    mensaje     text NOT NULL,
    id_reserva  uuid REFERENCES reservas.reserva (id) ON DELETE SET NULL,
    leida       boolean NOT NULL DEFAULT false,
    fecha       timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX notificacion_usuario_idx ON experiencia.notificacion (id_usuario, leida, fecha DESC);
