package cashu.wallet.db.repository;

import cashu.wallet.db.model.MeltQuoteRequestEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface MeltQuoteRequestEntityRepository extends JpaRepository<MeltQuoteRequestEntity, Integer> {
    @Query("select m from MeltQuoteRequestEntity m where m.correlationId = ?1")
    Optional<MeltQuoteRequestEntity> findByCorrelationId(UUID correlationId);
}