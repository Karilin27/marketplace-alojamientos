-- =====================================================================================
-- V5 — Ajustes de S2 (catalogo) para HU-005, HU-007, HU-023 y HU-024. Requiere V1 a V4.
--   1. La política de cancelación puede quedar vacía mientras la publicación está en BORRADOR (caso A-02 de
--      la página 19); para ACTIVAR se exige (RN-16, HU-023 CA-03). Lo valida S2 y lo asegura el CHECK.
--   2. Historial de estados de la publicación: usuario, estado anterior, estado nuevo y fecha (HU-007 CA-08).
--   3. Un servicio adicional no se repite por nombre dentro del mismo alojamiento (HU-024).
-- =====================================================================================

ALTER TABLE catalogo.alojamiento ALTER COLUMN id_politica DROP NOT NULL;

ALTER TABLE catalogo.alojamiento DROP CONSTRAINT IF EXISTS alojamiento_activa_con_politica;
ALTER TABLE catalogo.alojamiento ADD CONSTRAINT alojamiento_activa_con_politica
    CHECK (estado_publicacion IN ('BORRADOR', 'PENDIENTE_REVISION') OR id_politica IS NOT NULL);

CREATE TABLE IF NOT EXISTS catalogo.publicacion_historial (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    id_alojamiento   uuid NOT NULL REFERENCES catalogo.alojamiento (id) ON DELETE CASCADE,
    estado_anterior  text,
    estado_nuevo     text NOT NULL,
    id_actor         uuid REFERENCES identidad.usuario (id),
    motivo           text,
    fecha            timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS publicacion_historial_aloj_idx
    ON catalogo.publicacion_historial (id_alojamiento, fecha);

CREATE UNIQUE INDEX IF NOT EXISTS servicio_adicional_nombre_uk
    ON catalogo.servicio_adicional (id_alojamiento, lower(nombre));

-- RLS: el backend se conecta con el usuario de la base y no se ve afectado; la API pública de Supabase no accede.
ALTER TABLE catalogo.publicacion_historial ENABLE ROW LEVEL SECURITY;
