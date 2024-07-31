package cashu.wallet.db.repository;

import cashu.wallet.db.model.MintRequestEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MintRequestEntityRepository extends JpaRepository<MintRequestEntity, Integer> {
    List<MintRequestEntity> findByCorrelationId(UUID correlationId);

    Optional<MintRequestEntity> findByCorrelationIdAndBlindMessage(UUID correlationId, String blindMessage);
}