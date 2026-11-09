-- Rollback de V3 y V3.1 (en este orden). ¡Borra reservas, pagos y notificaciones! Usar solo en desarrollo o
-- con respaldo previo (pg_dump) — ver docs/despliegue-kevin.md.
DROP VIEW  IF EXISTS reservas.v_parametro_sistema;
DROP VIEW  IF EXISTS reservas.v_servicio_adicional;
DROP VIEW  IF EXISTS reservas.v_alojamiento;
DROP TABLE IF EXISTS experiencia.notificacion;
DROP TABLE IF EXISTS pagos.pago;
DROP TABLE IF EXISTS reservas.reserva_historial;
DROP TABLE IF EXISTS reservas.cancelacion;
DROP TABLE IF EXISTS reservas.reserva_servicio_adicional;
DROP TABLE IF EXISTS reservas.reserva;
DROP TABLE IF EXISTS reservas.disponibilidad;
DROP SCHEMA IF EXISTS experiencia;
DROP SCHEMA IF EXISTS pagos;
DROP SCHEMA IF EXISTS reservas;
-- La extensión btree_gist se deja instalada (otros servicios podrían usarla).
