-- =====================================================================================
-- V4 — S5 reseñas y S6 administración (moderación, auditoría, exportaciones).
-- Alineado con la página 12: resena con id_reserva único, calificacion 1-5, visible y motivo_oculta
-- (ajuste 7); auditoria con resultado (ajuste 1); moderacion; reporte exportado.
-- Requiere V1, V2 y V3_2 (reservas.reserva). El esquema "experiencia" lo crea V3.
-- =====================================================================================
CREATE SCHEMA IF NOT EXISTS administracion;

CREATE TABLE experiencia.resena (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    id_reserva      uuid     NOT NULL UNIQUE REFERENCES reservas.reserva (id),     -- RN-13: una reseña por reserva
    id_alojamiento  uuid     NOT NULL REFERENCES catalogo.alojamiento (id),
    id_usuario      uuid     NOT NULL REFERENCES identidad.usuario (id),
    calificacion    smallint NOT NULL CHECK (calificacion BETWEEN 1 AND 5),
    comentario      text,
    visible         boolean  NOT NULL DEFAULT true,                                -- RN-20: oculta, no se borra
    motivo_oculta   text,
    fecha           timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT resena_oculta_con_motivo CHECK (visible OR motivo_oculta IS NOT NULL)
);
CREATE INDEX resena_alojamiento_idx ON experiencia.resena (id_alojamiento, visible);

CREATE TABLE administracion.moderacion (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    id_admin        uuid NOT NULL REFERENCES identidad.usuario (id),
    id_alojamiento  uuid REFERENCES catalogo.alojamiento (id),
    id_resena       uuid REFERENCES experiencia.resena (id),
    accion          text NOT NULL CHECK (accion IN ('APROBAR', 'PAUSAR', 'BLOQUEAR', 'DESBLOQUEAR', 'ADVERTIR', 'OCULTAR_RESENA')),
    motivo          text,
    fecha           timestamptz NOT NULL DEFAULT now(),
    CHECK (id_alojamiento IS NOT NULL OR id_resena IS NOT NULL)
);
CREATE INDEX moderacion_alojamiento_idx ON administracion.moderacion (id_alojamiento);

CREATE TABLE administracion.auditoria (                              -- RN-14, HU-019
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    id_usuario  uuid REFERENCES identidad.usuario (id) ON DELETE SET NULL,
    entidad     text NOT NULL,
    id_entidad  text,
    accion      text NOT NULL,
    resultado   text NOT NULL,                                       -- p. ej. EXITO o FALLIDO
    detalle     jsonb NOT NULL DEFAULT '{}'::jsonb,
    fecha       timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX auditoria_entidad_fecha_idx ON administracion.auditoria (entidad, id_entidad, fecha DESC);

CREATE TABLE administracion.reporte_exportado (                      -- HU-021
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    id_usuario  uuid NOT NULL REFERENCES identidad.usuario (id),     -- administrador
    tipo        text NOT NULL,
    formato     text NOT NULL CHECK (formato IN ('PDF', 'EXCEL')),
    fecha       timestamptz NOT NULL DEFAULT now()
);
