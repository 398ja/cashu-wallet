package cashu.wallet.db.repository;

import cashu.wallet.db.model.ProofEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ProofRepository extends JpaRepository<ProofEntity, Integer> {

    @Query("select p from ProofEntity p where p.keysetId = ?1")
    List<ProofEntity> findByKeysetId(String keysetId);

    @Override
    Optional<ProofEntity> findById(Integer integer);

    @Query("select p from ProofEntity p where p.signature = ?1")
    Optional<ProofEntity> findBySignature(String signature);

    Optional<ProofEntity> findBySecret(String secret);

    List<ProofEntity> findByAmountAndKeysetId(Integer amount, String keysetId);

    @Override
    List<ProofEntity> findAll();
}