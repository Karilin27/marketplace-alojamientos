-- V3.2 — S3 Reservas, cancelaciones e historial (HU-012, HU-014, HU-015)
-- Autor: Kevin Rojas · PostgreSQL 15+ (Supabase) · Modelo: página 12 de Confluence · Rollback: rollback/V3__rollback.sql

-- ------------------------------------------------------------------ S3: reserva (HU-012)
CREATE TABLE reservas.reserva (
    id                  uuid PRIMARY KEY,
    id_alojamiento      uuid NOT NULL,
    id_huesped          uuid NOT NULL,
    fecha_llegada       date NOT NULL,
    fecha_salida        date NOT NULL,
    huespedes           integer NOT NULL CHECK (huespedes > 0),
    estado              text NOT NULL CHECK (estado IN
                          ('PENDIENTE_PAGO', 'CONFIRMADA', 'RECHAZADA', 'CANCELADA', 'COMPLETADA')),
    subtotal            numeric(12, 2) NOT NULL CHECK (subtotal >= 0),
    descuento           numeric(12, 2) NOT NULL CHECK (descuento >= 0),
    tarifa_limpieza     numeric(12, 2) NOT NULL CHECK (tarifa_limpieza >= 0),
    tarifa_servicio     numeric(12, 2) NOT NULL CHECK (tarifa_servicio >= 0),
    total               numeric(12, 2) NOT NULL CHECK (total >= 0),
    -- política vigente al reservar (se copian sus valores: HU-023 CA-04, ajuste 13 del modelo)
    politica_nombre     text NOT NULL,
    politica_dias       integer NOT NULL CHECK (politica_dias >= 0),
    politica_pct        integer NOT NULL CHECK (politica_pct BETWEEN 0 AND 100),
    llave_idempotencia  text NOT NULL,
    revision_manual     boolean NOT NULL DEFAULT false,      -- RN-19: pagos/reembolsos con resultado dudoso
    expira_en           timestamptz NOT NULL,                -- fin de la retención de 15 min (RN-10)
    creada_en           timestamptz NOT NULL DEFAULT now(),
    actualizada_en      timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT reserva_fechas_ck CHECK (fecha_salida > fecha_llegada),
    -- idempotencia por usuario (RN-12)
    CONSTRAINT reserva_llave_uk UNIQUE (id_huesped, llave_idempotencia),
    -- SIN SOLAPAMIENTO (RN-06, RNF-005): decide la base de datos, no la aplicación
    CONSTRAINT reserva_sin_solape EXCLUDE USING gist (
        id_alojamiento WITH =,
        daterange(fecha_llegada, fecha_salida, '[)') WITH &&)
        WHERE (estado IN ('PENDIENTE_PAGO', 'CONFIRMADA'))
);
CREATE INDEX reserva_huesped_idx ON reservas.reserva (id_huesped, creada_en DESC);
CREATE INDEX reserva_alojamiento_idx ON reservas.reserva (id_alojamiento, fecha_llegada);
CREATE INDEX reserva_pendientes_idx ON reservas.reserva (expira_en) WHERE estado = 'PENDIENTE_PAGO';
CREATE INDEX reserva_confirmadas_idx ON reservas.reserva (fecha_salida) WHERE estado = 'CONFIRMADA';

CREATE TABLE reservas.reserva_servicio_adicional (
    id_reserva   uuid NOT NULL REFERENCES reservas.reserva (id) ON DELETE CASCADE,
    id_servicio  uuid NOT NULL,
    nombre       text NOT NULL,                              -- se copia el nombre y costo vigentes al reservar
    costo        numeric(12, 2) NOT NULL CHECK (costo >= 0),
    PRIMARY KEY (id_reserva, id_servicio)
);

CREATE TABLE reservas.cancelacion (
    id                  uuid PRIMARY KEY,
    id_reserva          uuid NOT NULL UNIQUE REFERENCES reservas.reserva (id),   -- 1 a 0..1
    id_cancelado_por    uuid NOT NULL,
    motivo              text,
    monto_reembolso     numeric(12, 2) NOT NULL CHECK (monto_reembolso >= 0),
    llave_idempotencia  text NOT NULL,
    fecha               timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT cancelacion_llave_uk UNIQUE (id_cancelado_por, llave_idempotencia)
);

-- Trazabilidad de cambios de estado (apoya HU-019/HU-026)
CREATE TABLE reservas.reserva_historial (
    id               uuid PRIMARY KEY,
    id_reserva       uuid NOT NULL REFERENCES reservas.reserva (id) ON DELETE CASCADE,
    estado_anterior  text,
    estado_nuevo     text NOT NULL,
    id_actor         uuid,                                   -- NULL = el sistema (jobs)
    motivo           text,
    fecha            timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX reserva_historial_idx ON reservas.reserva_historial (id_reserva, fecha);

