package com.dawnbread.attendance.repository;

import com.dawnbread.attendance.entity.TenantAware;
import jakarta.persistence.EntityManager;
import org.springframework.data.jpa.repository.support.JpaEntityInformation;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;

import java.util.Optional;

/**
 * Spring Data's default repository base class resolves findById/existsById
 * via EntityManager.find() — the primary-key "load by id" fast path, which
 * is a well-documented Hibernate limitation: @Filter only applies to HQL/
 * JPQL/Criteria queries, NOT direct PK loads. Confirmed empirically by
 * TenantIsolationIntegrationTest: a raw findById() returned another tenant's
 * row even with the tenant filter enabled on the session. Routing these
 * through an explicit JPQL query instead makes the same @Filter apply here
 * too, so every tenant-scoped repository gets this for free by extending
 * this base class — no per-repository or per-service-call changes needed.
 *
 * Entities that are NOT tenant-scoped (Tenant itself, and later SuperAdmin)
 * simply don't implement TenantAware, so they fall through to the normal,
 * unfiltered Spring Data behavior unchanged.
 */
public class TenantScopedRepositoryImpl<T, ID> extends SimpleJpaRepository<T, ID> {

    private final JpaEntityInformation<T, ?> entityInformation;
    private final EntityManager entityManager;

    public TenantScopedRepositoryImpl(JpaEntityInformation<T, ?> entityInformation, EntityManager entityManager) {
        super(entityInformation, entityManager);
        this.entityInformation = entityInformation;
        this.entityManager = entityManager;
    }

    @Override
    public Optional<T> findById(ID id) {
        if (!TenantAware.class.isAssignableFrom(entityInformation.getJavaType())) {
            return super.findById(id);
        }
        String jpql = "SELECT e FROM " + entityInformation.getEntityName() + " e WHERE e.id = :id";
        return entityManager.createQuery(jpql, entityInformation.getJavaType())
                .setParameter("id", id)
                .getResultStream()
                .findFirst();
    }

    @Override
    public boolean existsById(ID id) {
        if (!TenantAware.class.isAssignableFrom(entityInformation.getJavaType())) {
            return super.existsById(id);
        }
        return findById(id).isPresent();
    }

    @Override
    public void deleteById(ID id) {
        if (!TenantAware.class.isAssignableFrom(entityInformation.getJavaType())) {
            super.deleteById(id);
            return;
        }
        findById(id).ifPresent(this::delete);
    }
}
