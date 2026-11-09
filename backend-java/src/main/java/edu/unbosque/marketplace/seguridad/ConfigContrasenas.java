package edu.unbosque.marketplace.seguridad;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** S1: las contraseñas se guardan con BCrypt en identidad.usuario.contrasena_hash (nunca en texto plano). */
@Configuration
public class ConfigContrasenas {

    @Bean
    public PasswordEncoder codificadorContrasenas() {
        return new BCryptPasswordEncoder();
    }
}
