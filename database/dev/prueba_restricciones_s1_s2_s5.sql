-- Prueba automática de las restricciones de V1, V2 y V4 (se ejecuta con psql; imprime PASS/FAIL). Requiere el seed de dev/seed_s2_minimo.sql.
\set ON_ERROR_STOP 0
CREATE TEMP TABLE r(caso text, ok boolean);
DO $$
DECLARE h uuid := gen_random_uuid(); res uuid; ok boolean;
BEGIN
  INSERT INTO identidad.usuario(id,nombre,correo,contrasena_hash,rol) VALUES (h,'Ana','Ana@Mail.com','x','HUESPED');
  BEGIN INSERT INTO identidad.usuario(nombre,correo,contrasena_hash,rol) VALUES ('B','ana@mail.com','x','HUESPED'); ok:=false; EXCEPTION WHEN unique_violation THEN ok:=true; END;
  INSERT INTO r VALUES ('correo único sin distinguir mayúsculas', ok);
  BEGIN INSERT INTO identidad.usuario(nombre,correo,contrasena_hash,rol,estado) VALUES ('C','c@mail.com','x','HUESPED','RESTRINGIDO'); ok:=false; EXCEPTION WHEN check_violation THEN ok:=true; END;
  INSERT INTO r VALUES ('RESTRINGIDO exige motivo', ok);
  BEGIN INSERT INTO identidad.usuario(nombre,correo,contrasena_hash,rol) VALUES ('D','d@mail.com','x','SUPERUSUARIO'); ok:=false; EXCEPTION WHEN check_violation THEN ok:=true; END;
  INSERT INTO r VALUES ('rol fuera de catálogo rechazado', ok);
  BEGIN UPDATE catalogo.alojamiento SET estado_publicacion='PUBLICADO' WHERE id='00000000-0000-0000-0000-0000000a0001'; ok:=false; EXCEPTION WHEN check_violation THEN ok:=true; END;
  INSERT INTO r VALUES ('estado PUBLICADO ya no existe (RN-15)', ok);
  BEGIN INSERT INTO catalogo.alojamiento(id_anfitrion,id_politica,titulo,descripcion,tipo,ciudad,direccion,capacidad,precio_base) SELECT '00000000-0000-0000-0000-00000000a001',id,'t','d','CASA','c','d',0,100 FROM catalogo.politica_cancelacion LIMIT 1; ok:=false; EXCEPTION WHEN check_violation THEN ok:=true; END;
  INSERT INTO r VALUES ('capacidad > 0 (RN-16)', ok);
  -- reseña única por reserva y oculta con motivo
  INSERT INTO reservas.reserva (id,id_alojamiento,id_huesped,fecha_llegada,fecha_salida,huespedes,estado,subtotal,descuento,tarifa_limpieza,tarifa_servicio,total,politica_nombre,politica_dias,politica_pct,llave_idempotencia,expira_en)
   VALUES (gen_random_uuid(),'00000000-0000-0000-0000-0000000a0001',h,current_date-10,current_date-7,2,'COMPLETADA',600000,0,50000,60000,710000,'MODERADA',7,100,'kr1',now()) RETURNING id INTO res;
  INSERT INTO experiencia.resena(id_reserva,id_alojamiento,id_usuario,calificacion) VALUES (res,'00000000-0000-0000-0000-0000000a0001',h,5);
  BEGIN INSERT INTO experiencia.resena(id_reserva,id_alojamiento,id_usuario,calificacion) VALUES (res,'00000000-0000-0000-0000-0000000a0001',h,4); ok:=false; EXCEPTION WHEN unique_violation THEN ok:=true; END;
  INSERT INTO r VALUES ('una reseña por reserva (RN-13)', ok);
  BEGIN UPDATE experiencia.resena SET visible=false WHERE id_reserva=res; ok:=false; EXCEPTION WHEN check_violation THEN ok:=true; END;
  INSERT INTO r VALUES ('ocultar reseña exige motivo (RN-20)', ok);
  BEGIN INSERT INTO experiencia.resena(id_reserva,id_alojamiento,id_usuario,calificacion) SELECT id,id_alojamiento,id_huesped,6 FROM reservas.reserva WHERE llave_idempotencia='kr1'; ok:=false; EXCEPTION WHEN check_violation OR unique_violation THEN ok:=true; END;
  INSERT INTO r VALUES ('calificación entre 1 y 5', ok);
END $$;
SELECT CASE WHEN ok THEN 'PASS ' ELSE 'FAIL ' END || caso FROM r;
