package cashu.wallet.db.repository;

import cashu.wallet.db.model.MintQuoteResponseEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MintQuoteResponseEntityRepository extends JpaRepository<MintQuoteResponseEntity, Integer> {
    Optional<MintQuoteResponseEntity> findByCorrelationId(UUID correlationId);

    Optional<MintQuoteResponseEntity> findByQuote(String quote);
}