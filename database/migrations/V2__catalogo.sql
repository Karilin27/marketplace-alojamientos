-- =====================================================================================
-- V2 — S2 Alojamientos (esquema "catalogo"). Alineado con la página 12 de Confluence:
--   alojamiento con tarifa_limpieza, descuento_pct y descuento_min_noches (§4 ajuste 2);
--   servicio_adicional (ajuste 3); parametro_sistema (ajuste 4); sin tabla "tarifa" (ajuste 11, D-11);
--   políticas FLEXIBLE 2 días/100 %, MODERADA 7/100 %, ESTRICTA 14/50 % (D-09);
--   estados de publicación de RN-15: BORRADOR, PENDIENTE_REVISION, ACTIVA, PAUSADA, BLOQUEADA.
-- Requiere V1 (identidad.usuario).
-- =====================================================================================
CREATE SCHEMA IF NOT EXISTS catalogo;

CREATE TABLE catalogo.politica_cancelacion (
    id                    uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    nombre                text    NOT NULL UNIQUE CHECK (nombre IN ('FLEXIBLE', 'MODERADA', 'ESTRICTA')),
    dias_antelacion       integer NOT NULL CHECK (dias_antelacion >= 0),
    porcentaje_reembolso  integer NOT NULL CHECK (porcentaje_reembolso BETWEEN 0 AND 100)
);

CREATE TABLE catalogo.alojamiento (
    id                    uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    id_anfitrion          uuid    NOT NULL REFERENCES identidad.usuario (id),
    id_politica           uuid    NOT NULL REFERENCES catalogo.politica_cancelacion (id),
    titulo                text    NOT NULL,
    descripcion           text    NOT NULL,
    tipo                  text    NOT NULL CHECK (tipo IN ('APARTAMENTO', 'CASA', 'HABITACION', 'CABANA', 'OTRO')),
    ciudad                text    NOT NULL,
    direccion             text    NOT NULL,
    capacidad             integer NOT NULL CHECK (capacidad > 0),                  -- RN-16
    precio_base           numeric(12,2) NOT NULL CHECK (precio_base > 0),          -- RN-16, por noche
    reglas                text,
    estado_publicacion    text    NOT NULL DEFAULT 'BORRADOR'
                          CHECK (estado_publicacion IN ('BORRADOR', 'PENDIENTE_REVISION', 'ACTIVA', 'PAUSADA', 'BLOQUEADA')),
    tarifa_limpieza       numeric(12,2) NOT NULL DEFAULT 0 CHECK (tarifa_limpieza >= 0),
    descuento_pct         numeric(5,2)  NOT NULL DEFAULT 0 CHECK (descuento_pct BETWEEN 0 AND 100),
    descuento_min_noches  integer       NOT NULL DEFAULT 0 CHECK (descuento_min_noches >= 0),
    fecha_creacion        timestamptz   NOT NULL DEFAULT now(),
    fecha_actualizacion   timestamptz   NOT NULL DEFAULT now()
);
CREATE INDEX alojamiento_anfitrion_idx ON catalogo.alojamiento (id_anfitrion);
CREATE INDEX alojamiento_busqueda_idx ON catalogo.alojamiento (ciudad, estado_publicacion, precio_base, capacidad, tipo);

CREATE TABLE catalogo.foto_alojamiento (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    id_alojamiento  uuid    NOT NULL REFERENCES catalogo.alojamiento (id) ON DELETE CASCADE,
    url             text    NOT NULL,
    orden           integer NOT NULL DEFAULT 1 CHECK (orden > 0),
    UNIQUE (id_alojamiento, orden)
);

CREATE TABLE catalogo.servicio (                                    -- amenity (WiFi, cocina...)
    id      smallint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre  text NOT NULL UNIQUE
);
CREATE TABLE catalogo.alojamiento_servicio (                        -- N a N
    id_alojamiento  uuid     NOT NULL REFERENCES catalogo.alojamiento (id) ON DELETE CASCADE,
    id_servicio     smallint NOT NULL REFERENCES catalogo.servicio (id),
    PRIMARY KEY (id_alojamiento, id_servicio)
);

CREATE TABLE catalogo.servicio_adicional (                          -- servicio con costo (RF-012, HU-024)
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    id_alojamiento  uuid    NOT NULL REFERENCES catalogo.alojamiento (id) ON DELETE CASCADE,
    nombre          text    NOT NULL,
    costo           numeric(12,2) NOT NULL CHECK (costo >= 0),
    activo          boolean NOT NULL DEFAULT true
);
CREATE INDEX servicio_adicional_aloj_idx ON catalogo.servicio_adicional (id_alojamiento);

CREATE TABLE catalogo.parametro_sistema (                           -- RN-17: tarifa de servicio configurable
    clave  text PRIMARY KEY,
    valor  text NOT NULL
);

-- Datos iniciales
INSERT INTO catalogo.politica_cancelacion (nombre, dias_antelacion, porcentaje_reembolso) VALUES
    ('FLEXIBLE', 2, 100), ('MODERADA', 7, 100), ('ESTRICTA', 14, 50)
ON CONFLICT (nombre) DO NOTHING;
INSERT INTO catalogo.servicio (nombre) VALUES
    ('WiFi'), ('Cocina'), ('Parqueadero'), ('Aire acondicionado'),
    ('Televisión'), ('Lavadora'), ('Piscina'), ('Admite mascotas')
ON CONFLICT (nombre) DO NOTHING;
INSERT INTO catalogo.parametro_sistema (clave, valor) VALUES ('tarifa_servicio_pct', '10')
ON CONFLICT (clave) DO NOTHING;
