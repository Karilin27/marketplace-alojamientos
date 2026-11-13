-- Reversión de V5__catalogo_ajustes.sql
DROP INDEX IF EXISTS catalogo.servicio_adicional_nombre_uk;
DROP TABLE IF EXISTS catalogo.publicacion_historial;
ALTER TABLE catalogo.alojamiento DROP CONSTRAINT IF EXISTS alojamiento_activa_con_politica;
-- Solo se puede volver a NOT NULL si todas las publicaciones tienen política.
UPDATE catalogo.alojamiento
   SET id_politica = (SELECT id FROM catalogo.politica_cancelacion WHERE nombre = 'MODERADA')
 WHERE id_politica IS NULL;
ALTER TABLE catalogo.alojamiento ALTER COLUMN id_politica SET NOT NULL;
