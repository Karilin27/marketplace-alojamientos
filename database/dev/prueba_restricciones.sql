-- Prueba automática de las restricciones de V3 (se ejecuta con psql -v ON_ERROR_STOP=1). Imprime PASS/FAIL.
\set ON_ERROR_STOP 0
CREATE TEMP TABLE resultado (caso text, ok boolean);

CREATE FUNCTION pg_temp.ins_reserva(p_id uuid, p_aloj uuid, p_huesped uuid, p_lleg date, p_sal date, p_estado text, p_llave text)
RETURNS void LANGUAGE sql AS $$
  INSERT INTO reservas.reserva (id, id_alojamiento, id_huesped, fecha_llegada, fecha_salida, huespedes, estado,
     subtotal, descuento, tarifa_limpieza, tarifa_servicio, total, politica_nombre, politica_dias, politica_pct,
     llave_idempotencia, expira_en)
  VALUES (p_id, p_aloj, p_huesped, p_lleg, p_sal, 2, p_estado, 600000, 0, 50000, 60000, 710000, 'MODERADA', 7, 100,
     p_llave, now() + interval '15 minutes');
$$;

DO $$
DECLARE
  aloj uuid := '00000000-0000-0000-0000-0000000a0001';
  h1 uuid := gen_random_uuid(); h2 uuid := gen_random_uuid();
  r1 uuid := gen_random_uuid(); r2 uuid := gen_random_uuid(); r3 uuid := gen_random_uuid(); r4 uuid := gen_random_uuid();
  estado text; ok boolean;
BEGIN
  PERFORM pg_temp.ins_reserva(r1, aloj, h1, current_date + 10, current_date + 13, 'PENDIENTE_PAGO', 'k1');
  INSERT INTO resultado VALUES ('HU-012 inserta reserva PENDIENTE_PAGO', true);

  -- solapamiento (RN-06): mismo periodo y otro huésped
  BEGIN
    PERFORM pg_temp.ins_reserva(r2, aloj, h2, current_date + 11, current_date + 14, 'PENDIENTE_PAGO', 'k2');
    INSERT INTO resultado VALUES ('HU-012 rechaza solapamiento', false);
  EXCEPTION WHEN exclusion_violation THEN INSERT INTO resultado VALUES ('HU-012 rechaza solapamiento (23P01)', true); END;

  -- la noche de salida queda libre: [10,13) y [13,15) no se solapan
  BEGIN
    PERFORM pg_temp.ins_reserva(r3, aloj, h2, current_date + 13, current_date + 15, 'CONFIRMADA', 'k3');
    INSERT INTO resultado VALUES ('reserva contigua (salida = llegada) permitida', true);
  EXCEPTION WHEN OTHERS THEN INSERT INTO resultado VALUES ('reserva contigua (salida = llegada) permitida', false); END;

  -- idempotencia (RN-12): misma llave del mismo huésped
  BEGIN
    PERFORM pg_temp.ins_reserva(r4, aloj, h1, current_date + 40, current_date + 42, 'PENDIENTE_PAGO', 'k1');
    INSERT INTO resultado VALUES ('HU-012 llave repetida rechazada', false);
  EXCEPTION WHEN unique_violation THEN INSERT INTO resultado VALUES ('HU-012 llave repetida rechazada (23505)', true); END;

  -- otro huésped puede usar la misma llave
  BEGIN
    PERFORM pg_temp.ins_reserva(gen_random_uuid(), aloj, h2, current_date + 40, current_date + 42, 'PENDIENTE_PAGO', 'k1');
    INSERT INTO resultado VALUES ('la llave es por usuario', true);
  EXCEPTION WHEN OTHERS THEN INSERT INTO resultado VALUES ('la llave es por usuario', false); END;

  -- una RECHAZADA/CANCELADA libera el periodo
  UPDATE reservas.reserva SET estado = 'RECHAZADA' WHERE id = r1;
  BEGIN
    PERFORM pg_temp.ins_reserva(gen_random_uuid(), aloj, h2, current_date + 10, current_date + 13, 'PENDIENTE_PAGO', 'k5');
    INSERT INTO resultado VALUES ('reserva RECHAZADA libera las fechas', true);
  EXCEPTION WHEN OTHERS THEN INSERT INTO resultado VALUES ('reserva RECHAZADA libera las fechas', false); END;

  -- fechas inválidas
  BEGIN
    PERFORM pg_temp.ins_reserva(gen_random_uuid(), aloj, h2, current_date + 60, current_date + 60, 'PENDIENTE_PAGO', 'k6');
    INSERT INTO resultado VALUES ('salida debe ser posterior a llegada', false);
  EXCEPTION WHEN check_violation THEN INSERT INTO resultado VALUES ('salida debe ser posterior a llegada (23514)', true); END;

  -- disponibilidad: periodos que se superponen
  INSERT INTO reservas.disponibilidad VALUES (gen_random_uuid(), aloj, current_date + 20, current_date + 25, 'NO_DISPONIBLE');
  BEGIN
    INSERT INTO reservas.disponibilidad VALUES (gen_random_uuid(), aloj, current_date + 24, current_date + 28, 'NO_DISPONIBLE');
    INSERT INTO resultado VALUES ('HU-008 periodos superpuestos rechazados', false);
  EXCEPTION WHEN exclusion_violation THEN INSERT INTO resultado VALUES ('HU-008 periodos superpuestos rechazados (23P01)', true); END;

  -- pagos: un solo pago vivo por reserva; tras RECHAZADO se puede reintentar
  INSERT INTO pagos.pago (id, id_reserva, id_huesped, proveedor, monto, estado, llave_idempotencia)
    VALUES (gen_random_uuid(), r3, h2, 'SIMULADA', 710000, 'PENDIENTE', 'p1');
  BEGIN
    INSERT INTO pagos.pago (id, id_reserva, id_huesped, proveedor, monto, estado, llave_idempotencia)
      VALUES (gen_random_uuid(), r3, h2, 'SIMULADA', 710000, 'PENDIENTE', 'p2');
    INSERT INTO resultado VALUES ('HU-013 doble pago simultáneo rechazado', false);
  EXCEPTION WHEN unique_violation THEN INSERT INTO resultado VALUES ('HU-013 doble pago simultáneo rechazado (23505)', true); END;
  BEGIN
    INSERT INTO pagos.pago (id, id_reserva, id_huesped, proveedor, monto, estado, llave_idempotencia)
      VALUES (gen_random_uuid(), r3, h2, 'SIMULADA', 710000, 'PENDIENTE', 'p1');
    INSERT INTO resultado VALUES ('HU-013 misma Idempotency-Key de pago rechazada', false);
  EXCEPTION WHEN unique_violation THEN INSERT INTO resultado VALUES ('HU-013 misma Idempotency-Key de pago rechazada (23505)', true); END;
  UPDATE pagos.pago SET estado = 'RECHAZADO' WHERE id_reserva = r3;
  BEGIN
    INSERT INTO pagos.pago (id, id_reserva, id_huesped, proveedor, monto, estado, llave_idempotencia)
      VALUES (gen_random_uuid(), r3, h2, 'SIMULADA', 710000, 'PENDIENTE', 'p3');
    INSERT INTO resultado VALUES ('reintento de pago tras rechazo permitido', true);
  EXCEPTION WHEN OTHERS THEN INSERT INTO resultado VALUES ('reintento de pago tras rechazo permitido', false); END;

  -- cancelación única por reserva
  INSERT INTO reservas.cancelacion VALUES (gen_random_uuid(), r3, h2, 'x', 710000, 'c1', now());
  BEGIN
    INSERT INTO reservas.cancelacion VALUES (gen_random_uuid(), r3, h2, 'x', 710000, 'c2', now());
    INSERT INTO resultado VALUES ('una cancelación por reserva', false);
  EXCEPTION WHEN unique_violation THEN INSERT INTO resultado VALUES ('una cancelación por reserva (23505)', true); END;
END $$;

SELECT CASE WHEN ok THEN 'PASS' ELSE 'FAIL' END AS res, caso FROM resultado ORDER BY 1 DESC, 2;
SELECT CASE WHEN count(*) FILTER (WHERE NOT ok) = 0 THEN '== TODAS LAS RESTRICCIONES OK' ELSE '== HAY FALLOS' END FROM resultado;
