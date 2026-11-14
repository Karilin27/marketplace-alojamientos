-- =====================================================================================
-- V6 — S5 Reseñas (HU-017, HU-025). Requiere V4.
--   HU-025 CA-05: la moderación de reseñas registra tanto OCULTAR_RESENA como RESTABLECER_RESENA
--   (quién, qué reseña, acción, motivo y fecha) en administracion.moderacion.
-- =====================================================================================
ALTER TABLE administracion.moderacion DROP CONSTRAINT IF EXISTS moderacion_accion_check;
ALTER TABLE administracion.moderacion ADD CONSTRAINT moderacion_accion_check
    CHECK (accion IN ('APROBAR', 'PAUSAR', 'BLOQUEAR', 'DESBLOQUEAR', 'ADVERTIR', 'OCULTAR_RESENA', 'RESTABLECER_RESENA'));

CREATE INDEX IF NOT EXISTS moderacion_resena_idx ON administracion.moderacion (id_resena);
