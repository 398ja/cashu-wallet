package cashu.wallet.db.repository;

import cashu.wallet.db.model.MeltQuoteResponseEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface MeltQuoteResponseEntityRepository extends JpaRepository<MeltQuoteResponseEntity, Integer> {
    @Query("select m from MeltQuoteResponseEntity m where m.quote = ?1")
    Optional<MeltQuoteResponseEntity> findByQuote(String quote);

    @Query("select m from MeltQuoteResponseEntity m where m.correlationId = ?1")
    Optional<MeltQuoteResponseEntity> findByCorrelationId(UUID correlationId);
}