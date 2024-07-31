package cashu.wallet.db.repository;

import cashu.wallet.db.model.MintQuoteRequestEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MintQuoteRequestEntityRepository extends JpaRepository<MintQuoteRequestEntity, Integer> {
    Optional<MintQuoteRequestEntity> findByCorrelationId(UUID correlationId);
}