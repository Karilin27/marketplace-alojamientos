-- SOLO DESARROLLO / PRUEBAS — HU-004 (perfil y cuenta). Ejecuta, dentro de una transacción que termina en ROLLBACK,
-- las mismas consultas que usa PerfilRepositorio contra las migraciones V1 a V4 y el seed db/dev/seed_s2_minimo.sql.
-- No deja datos. Uso: psql -f prueba_hu004.sql  (cada caso imprime NOTICE "PASS ..." o falla con EXCEPTION).
BEGIN;

-- Huésped de prueba (sin perfil todavía)
INSERT INTO identidad.usuario (id, nombre, correo, contrasena_hash, rol)
VALUES ('00000000-0000-0000-0000-00000000c001', 'Huésped HU-004', 'huesped.hu004@example.com', 'x', 'HUESPED');

DO $$
DECLARE n int; tel text; pais text;
BEGIN
  -- CA-01: consulta con LEFT JOIN y bloqueo de fila, también sin fila de perfil
  PERFORM 1 FROM identidad.usuario u LEFT JOIN identidad.perfil p ON p.id_usuario = u.id
   WHERE u.id = '00000000-0000-0000-0000-00000000c001' FOR UPDATE OF u;
  IF NOT FOUND THEN RAISE EXCEPTION 'FAIL CA-01 no encuentra al usuario'; END IF;
  RAISE NOTICE 'PASS CA-01 consulta con LEFT JOIN y FOR UPDATE OF u';

  -- CA-02: el upsert crea la fila de perfil y luego la actualiza (una sola fila)
  INSERT INTO identidad.perfil (id_usuario, telefono, documento, pais, foto_url)
  VALUES ('00000000-0000-0000-0000-00000000c001', '3001234567', 'CC-12345', 'Colombia', NULL)
  ON CONFLICT (id_usuario) DO UPDATE SET telefono = EXCLUDED.telefono, documento = EXCLUDED.documento,
         pais = EXCLUDED.pais, foto_url = EXCLUDED.foto_url;
  INSERT INTO identidad.perfil (id_usuario, telefono, documento, pais, foto_url)
  VALUES ('00000000-0000-0000-0000-00000000c001', '3109998888', 'CC-12345', 'México', 'https://x.com/a.png')
  ON CONFLICT (id_usuario) DO UPDATE SET telefono = EXCLUDED.telefono, documento = EXCLUDED.documento,
         pais = EXCLUDED.pais, foto_url = EXCLUDED.foto_url;
  SELECT count(*), max(telefono), max(p.pais) INTO n, tel, pais FROM identidad.perfil p
   WHERE id_usuario = '00000000-0000-0000-0000-00000000c001';
  IF n <> 1 OR tel <> '3109998888' OR pais <> 'México' THEN RAISE EXCEPTION 'FAIL CA-02 upsert de perfil'; END IF;
  RAISE NOTICE 'PASS CA-02 el upsert de perfil crea y luego actualiza una sola fila';
END $$;

-- Reservas de prueba sobre A-01 (anfitrión a001): una CONFIRMADA futura del huésped c001
INSERT INTO reservas.reserva (id, id_alojamiento, id_huesped, fecha_llegada, fecha_salida, huespedes, estado,
  subtotal, descuento, tarifa_limpieza, tarifa_servicio, total, politica_nombre, politica_dias, politica_pct,
  llave_idempotencia, expira_en)
VALUES ('00000000-0000-0000-0000-0000000d0001', '00000000-0000-0000-0000-0000000a0001',
  '00000000-0000-0000-0000-00000000c001', current_date + 30, current_date + 33, 2, 'CONFIRMADA',
  600000, 0, 50000, 60000, 710000, 'MODERADA', 7, 100, 'k-hu004-1', now() + interval '15 minutes');

DO $$
DECLARE n bigint;
BEGIN
  -- CA-06: bloquea al huésped y también al anfitrión dueño del alojamiento
  SELECT count(*) INTO n FROM reservas.reserva r WHERE r.estado = 'CONFIRMADA' AND r.fecha_salida > current_date
     AND (r.id_huesped = '00000000-0000-0000-0000-00000000c001'
          OR r.id_alojamiento IN (SELECT a.id FROM catalogo.alojamiento a
                                   WHERE a.id_anfitrion = '00000000-0000-0000-0000-00000000c001'));
  IF n <> 1 THEN RAISE EXCEPTION 'FAIL CA-06 huésped con reserva confirmada futura: % (esperado 1)', n; END IF;
  SELECT count(*) INTO n FROM reservas.reserva r WHERE r.estado = 'CONFIRMADA' AND r.fecha_salida > current_date
     AND (r.id_huesped = '00000000-0000-0000-0000-00000000a001'
          OR r.id_alojamiento IN (SELECT a.id FROM catalogo.alojamiento a
                                   WHERE a.id_anfitrion = '00000000-0000-0000-0000-00000000a001'));
  IF n <> 1 THEN RAISE EXCEPTION 'FAIL CA-06 anfitrión con reserva confirmada futura: % (esperado 1)', n; END IF;
  RAISE NOTICE 'PASS CA-06 la reserva confirmada futura cuenta para el huésped y para el anfitrión';

  -- No cuentan: cancelada, pendiente de pago o ya terminada
  UPDATE reservas.reserva SET estado = 'CANCELADA' WHERE id = '00000000-0000-0000-0000-0000000d0001';
  SELECT count(*) INTO n FROM reservas.reserva r WHERE r.estado = 'CONFIRMADA' AND r.fecha_salida > current_date
     AND r.id_huesped = '00000000-0000-0000-0000-00000000c001';
  IF n <> 0 THEN RAISE EXCEPTION 'FAIL una reserva CANCELADA no debe bloquear la desactivación'; END IF;
  UPDATE reservas.reserva SET estado = 'CONFIRMADA', fecha_llegada = current_date - 10, fecha_salida = current_date - 7
   WHERE id = '00000000-0000-0000-0000-0000000d0001';
  SELECT count(*) INTO n FROM reservas.reserva r WHERE r.estado = 'CONFIRMADA' AND r.fecha_salida > current_date
     AND r.id_huesped = '00000000-0000-0000-0000-00000000c001';
  IF n <> 0 THEN RAISE EXCEPTION 'FAIL una reserva ya terminada no debe bloquear la desactivación'; END IF;
  RAISE NOTICE 'PASS CA-05 las reservas canceladas o ya terminadas no bloquean la desactivación';
END $$;

DO $$
DECLARE n int; activas int; pausadas int; borradores int;
BEGIN
  -- CA-05: las publicaciones ACTIVAS del anfitrión pasan a PAUSADA; las demás no se tocan
  UPDATE catalogo.alojamiento SET estado_publicacion = 'BORRADOR' WHERE id = '00000000-0000-0000-0000-0000000a0003';
  UPDATE catalogo.alojamiento SET estado_publicacion = 'PAUSADA'
   WHERE id_anfitrion = '00000000-0000-0000-0000-00000000a001' AND estado_publicacion = 'ACTIVA';
  GET DIAGNOSTICS n = ROW_COUNT;
  SELECT count(*) FILTER (WHERE estado_publicacion = 'ACTIVA'), count(*) FILTER (WHERE estado_publicacion = 'PAUSADA'),
         count(*) FILTER (WHERE estado_publicacion = 'BORRADOR')
    INTO activas, pausadas, borradores FROM catalogo.alojamiento WHERE id_anfitrion = '00000000-0000-0000-0000-00000000a001';
  IF n <> 2 OR activas <> 0 OR pausadas <> 2 OR borradores <> 1 THEN
    RAISE EXCEPTION 'FAIL CA-05 pausa de publicaciones (n=%, activas=%, pausadas=%, borradores=%)', n, activas, pausadas, borradores;
  END IF;
  RAISE NOTICE 'PASS CA-05 solo las publicaciones ACTIVAS pasan a PAUSADA (BORRADOR no cambia)';

  -- CA-07: reactivar la cuenta no reactiva las publicaciones
  UPDATE identidad.usuario SET estado = 'DESACTIVADO' WHERE id = '00000000-0000-0000-0000-00000000a001';
  UPDATE identidad.usuario SET estado = 'ACTIVO' WHERE id = '00000000-0000-0000-0000-00000000a001';
  SELECT count(*) INTO n FROM catalogo.alojamiento
   WHERE id_anfitrion = '00000000-0000-0000-0000-00000000a001' AND estado_publicacion = 'ACTIVA';
  IF n <> 0 THEN RAISE EXCEPTION 'FAIL CA-07 las publicaciones deben seguir PAUSADAS'; END IF;
  RAISE NOTICE 'PASS CA-07 reactivar la cuenta deja las publicaciones PAUSADAS';

  -- Un estado inválido lo rechaza la base de datos
  BEGIN
    UPDATE identidad.usuario SET estado = 'INACTIVO' WHERE id = '00000000-0000-0000-0000-00000000c001';
    RAISE EXCEPTION 'FAIL estado inválido aceptado';
  EXCEPTION WHEN check_violation THEN
    RAISE NOTICE 'PASS la base rechaza estados fuera de ACTIVO / DESACTIVADO / RESTRINGIDO';
  END;
END $$;

ROLLBACK;
