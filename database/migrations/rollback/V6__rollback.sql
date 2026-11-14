-- Reversión de V6__resenas_moderacion.sql
DROP INDEX IF EXISTS administracion.moderacion_resena_idx;
DELETE FROM administracion.moderacion WHERE accion = 'RESTABLECER_RESENA';
ALTER TABLE administracion.moderacion DROP CONSTRAINT IF EXISTS moderacion_accion_check;
ALTER TABLE administracion.moderacion ADD CONSTRAINT moderacion_accion_check
    CHECK (accion IN ('APROBAR', 'PAUSAR', 'BLOQUEAR', 'DESBLOQUEAR', 'ADVERTIR', 'OCULTAR_RESENA'));
