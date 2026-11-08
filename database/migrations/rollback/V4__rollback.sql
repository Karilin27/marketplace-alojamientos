-- Reversión de V4 (reseñas y administración). Se ejecuta PRIMERO: resena depende de reservas.reserva.
-- DESTRUCTIVO: borra reseñas, moderación, auditoría y exportaciones. Hacer pg_dump antes.
DROP SCHEMA IF EXISTS administracion CASCADE;
DROP TABLE  IF EXISTS experiencia.resena;
