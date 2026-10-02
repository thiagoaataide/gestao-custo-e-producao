package br.com.taas.saas.gestaoproducao.operations.infrastructure.persistence.catalog.ingredient;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.taas.saas.gestaoproducao.operations.domain.model.catalog.ingredient.Ingredient;

public interface IngredientJpaRepository extends JpaRepository<Ingredient, UUID> {

    Optional<Ingredient> findByTenantIdAndNormalizedName(UUID tenantId, String normalizedName);

    Optional<Ingredient> findByTenantIdAndId(UUID tenantId, UUID ingredientId);

    @Query("""
            SELECT ingredient FROM Ingredient ingredient
             WHERE ingredient.tenantId = :tenantId
               AND ingredient.normalizedName <> :normalizedName
               AND (POSITION(:normalizedName IN ingredient.normalizedName) > 0
                    OR POSITION(ingredient.normalizedName IN :normalizedName) > 0)
             ORDER BY ingredient.normalizedName
            """)
    List<Ingredient> findSimilarByTenantId(
            @Param("tenantId") UUID tenantId,
            @Param("normalizedName") String normalizedName,
            Pageable pageable);
}
