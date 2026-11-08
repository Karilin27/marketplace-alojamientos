-- =====================================================================================
-- V1 — S1 Identidad y Usuarios (esquema "identidad"). Alineado con la página 12 de Confluence
-- (modelo de dominio, §4 ajustes 9 y 10) y con la decisión D-17: la autenticación la implementa S1,
-- la contraseña se guarda cifrada en la tabla usuario y NO se usa auth.users de Supabase.
-- Orden de ejecución: V1, V2, V3, V3_1, V3_2, V3_3, V3_4, V4.
-- =====================================================================================
CREATE EXTENSION IF NOT EXISTS pgcrypto;          -- gen_random_uuid()

CREATE SCHEMA IF NOT EXISTS identidad;

CREATE TABLE identidad.usuario (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    nombre              text        NOT NULL,
    correo              text        NOT NULL,
    contrasena_hash     text        NOT NULL,                       -- hash (p. ej. BCrypt); nunca texto plano
    rol                 text        NOT NULL CHECK (rol IN ('HUESPED', 'ANFITRION', 'ADMINISTRADOR')),
    estado              text        NOT NULL DEFAULT 'ACTIVO' CHECK (estado IN ('ACTIVO', 'DESACTIVADO', 'RESTRINGIDO')),
    motivo_restriccion  text,                                       -- RN-24, HU-022
    fecha_registro      timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT usuario_restringido_con_motivo CHECK (estado <> 'RESTRINGIDO' OR motivo_restriccion IS NOT NULL)
);
-- correo único sin distinguir mayúsculas
CREATE UNIQUE INDEX usuario_correo_uk ON identidad.usuario (lower(correo));
CREATE INDEX usuario_rol_estado_idx ON identidad.usuario (rol, estado);

CREATE TABLE identidad.perfil (                                     -- 1 a 0..1 con usuario (RF-004)
    id_usuario  uuid PRIMARY KEY REFERENCES identidad.usuario (id) ON DELETE CASCADE,
    telefono    text,
    documento   text,
    pais        text,
    foto_url    text
);

CREATE TABLE identidad.token_recuperacion (                         -- HU-003: recuperación simulada de acceso
    valor       text PRIMARY KEY,                                   -- un solo uso
    id_usuario  uuid        NOT NULL REFERENCES identidad.usuario (id) ON DELETE CASCADE,
    expira_en   timestamptz NOT NULL,                               -- 30 minutos
    usado       boolean     NOT NULL DEFAULT false
);
CREATE INDEX token_recuperacion_usuario_idx ON identidad.token_recuperacion (id_usuario);
