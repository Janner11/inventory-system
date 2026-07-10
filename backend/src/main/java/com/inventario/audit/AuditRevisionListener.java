package com.inventario.audit;

import com.inventario.security.CurrentUserResolver;
import org.hibernate.envers.RevisionListener;

/**
 * Puebla {@link AuditRevisionEntity#getUsername()} con el usuario autenticado en el
 * momento de cada revision. Hibernate instancia esta clase por reflexion (no es un
 * bean de Spring), asi que resuelve el usuario via {@link CurrentUserResolver}
 * (metodo estatico) en lugar de recibirlo por inyeccion de dependencias.
 */
public class AuditRevisionListener implements RevisionListener {

    @Override
    public void newRevision(Object revisionEntity) {
        AuditRevisionEntity auditRevisionEntity = (AuditRevisionEntity) revisionEntity;
        auditRevisionEntity.setUsername(CurrentUserResolver.resolve().orElse(null));
    }
}
