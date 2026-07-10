package com.inventario.audit;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.envers.DefaultRevisionEntity;
import org.hibernate.envers.RevisionEntity;

/**
 * Entidad de revision de Hibernate Envers, en lugar de la {@code DefaultRevisionEntity}
 * (que solo guarda {@code rev}/{@code revtstmp}). Agrega {@code username} para poder
 * responder "quien hizo este cambio" en {@code GET /api/audit/products/{id}/revisions},
 * no solo "cuando". Sigue mapeando la tabla {@code revinfo} (V3), ampliada con la
 * columna {@code username} en V7.
 *
 * {@code DefaultRevisionEntity} es {@code @MappedSuperclass} y sus campos {@code id}/
 * {@code timestamp} no tienen {@code @Column} propio (Envers los resuelve a REV/REVTSTMP
 * internamente solo para la entidad por defecto); al heredarlos aqui hay que remapearlos
 * explicitamente a las columnas reales de la tabla (V3) con {@code @AttributeOverride}.
 */
@Entity
@Table(name = "revinfo")
@RevisionEntity(AuditRevisionListener.class)
@AttributeOverride(name = "id", column = @Column(name = "rev"))
@AttributeOverride(name = "timestamp", column = @Column(name = "revtstmp"))
public class AuditRevisionEntity extends DefaultRevisionEntity {

    @Column(name = "username", length = 150)
    private String username;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }
}
