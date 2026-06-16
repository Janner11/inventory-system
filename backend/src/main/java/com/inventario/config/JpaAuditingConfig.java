package com.inventario.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Aislada del {@code @SpringBootApplication} principal para que los tests de
 * slice (p. ej. {@code @WebMvcTest}) no carguen la infraestructura de JPA
 * Auditing, ya que {@code @EnableJpaAuditing} en la clase principal se
 * procesa igual aunque la auto-configuracion de JPA este excluida.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
