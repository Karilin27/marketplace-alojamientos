-- =====================================================================================
-- V3 — S3 Disponibilidad (HU-008) y esquemas base de S3, S4 y S5
-- Autor: Kevin Rojas · Base de datos: PostgreSQL 15+ (Supabase)
-- Modelo: página 12 de Confluence (secciones 3 y 4). Las demás migraciones: V3_1 (vistas sobre S2), V3_2 (reservas), V3_3 (pagos), V3_4 (notificaciones).
-- Rollback: db/migrations/rollback/V3__rollback.sql
-- =====================================================================================

CREATE EXTENSION IF NOT EXISTS btree_gist;   -- necesaria para la restricción de no solapamiento (RN-06)

CREATE SCHEMA IF NOT EXISTS reservas;
CREATE SCHEMA IF NOT EXISTS pagos;
CREATE SCHEMA IF NOT EXISTS experiencia;

-- ------------------------------------------------------------------ S3: disponibilidad (HU-008)
-- Periodos declarados por el anfitrión. El tipo RESERVADO no se guarda: se deduce de las reservas activas.
CREATE TABLE reservas.disponibilidad (
    id              uuid PRIMARY KEY,
    id_alojamiento  uuid NOT NULL,
    fecha_inicio    date NOT NULL,
    fecha_fin       date NOT NULL,
    tipo            text NOT NULL CHECK (tipo IN ('DISPONIBLE', 'NO_DISPONIBLE')),
    CONSTRAINT disponibilidad_fechas_ck CHECK (fecha_fin > fecha_inicio),
    -- los periodos de un mismo alojamiento no se superponen (HU-008 CA-03)
    CONSTRAINT disponibilidad_sin_solape EXCLUDE USING gist (
        id_alojamiento WITH =,
        daterange(fecha_inicio, fecha_fin, '[)') WITH &&)
);
CREATE INDEX disponibilidad_alojamiento_idx ON reservas.disponibilidad (id_alojamiento, fecha_inicio);

