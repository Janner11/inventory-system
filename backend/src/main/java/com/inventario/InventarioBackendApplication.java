package com.inventario;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class InventarioBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(InventarioBackendApplication.class, args);
    }
}
