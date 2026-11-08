-- V3.3 — S4 Pagos (HU-013)
-- Autor: Kevin Rojas · PostgreSQL 15+ (Supabase) · Modelo: página 12 de Confluence · Rollback: rollback/V3__rollback.sql

-- ------------------------------------------------------------------ S4: pagos (HU-013)
CREATE TABLE pagos.pago (
    id                    uuid PRIMARY KEY,
    id_reserva            uuid NOT NULL REFERENCES reservas.reserva (id),
    id_huesped            uuid NOT NULL,
    proveedor             text NOT NULL,                     -- SIMULADA | STRIPE_TEST
    id_operacion_externa  text,
    monto                 numeric(12, 2) NOT NULL CHECK (monto >= 0),
    estado                text NOT NULL CHECK (estado IN
                            ('PENDIENTE', 'APROBADO', 'RECHAZADO', 'REEMBOLSADO', 'REEMBOLSO_PARCIAL')),
    motivo_rechazo        text,
    monto_reembolsado     numeric(12, 2) NOT NULL DEFAULT 0 CHECK (monto_reembolsado >= 0),
    llave_idempotencia    text NOT NULL,
    fecha                 timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pago_llave_uk UNIQUE (id_huesped, llave_idempotencia)
);
-- una reserva solo puede tener un pago vivo a la vez (permite reintentar tras un rechazo)
CREATE UNIQUE INDEX pago_unico_vivo_idx ON pagos.pago (id_reserva)
    WHERE estado IN ('PENDIENTE', 'APROBADO', 'REEMBOLSO_PARCIAL', 'REEMBOLSADO');

