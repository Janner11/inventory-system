package com.inventario.security;

import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Alimenta {@code @CreatedBy} ({@link com.inventario.entity.Product#createdBy}) con el
 * usuario del JWT autenticado (claim {@code preferred_username}, o el {@code sub} si ese
 * claim no esta presente). Unico bean {@link AuditorAware} del contexto, detectado
 * automaticamente por {@code @EnableJpaAuditing} (ver {@code JpaAuditingConfig}). Delega
 * la resolucion en {@link CurrentUserResolver}, compartida con
 * {@link com.inventario.audit.AuditRevisionListener}.
 */
@Component
public class SecurityAuditorAware implements AuditorAware<String> {

    @Override
    public Optional<String> getCurrentAuditor() {
        return CurrentUserResolver.resolve();
    }
}
