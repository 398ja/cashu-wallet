package cashu.wallet.db.model;

import cashu.common.model.Proof;
import cashu.common.model.Secret;
import cashu.common.model.Signature;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@Entity
@Table(name = "t_proof")
@ToString
public class ProofEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    @JsonProperty
    private Integer id;

    @Column(name = "amount", nullable = false)
    @JsonProperty
    private Integer amount;

    @Column(name = "secret", nullable = false, length = Integer.MAX_VALUE)
    @JsonProperty
    private String secret;

    @Column(name = "signature", nullable = false, length = Integer.MAX_VALUE)
    @JsonProperty
    private String signature;

    @Column(name = "keyset_id", nullable = false, length = Integer.MAX_VALUE)
    @JsonProperty("keyset_id")
    private String keysetId;

    public static Proof toProof(@NonNull ProofEntity entity) {
        Proof proof = new Proof();
        proof.setAmount(entity.getAmount());
        proof.setSecret(Secret.fromString(entity.getSecret()));
        proof.setUnblindedSignature(Signature.fromString(entity.getSignature()));
        proof.setKeySetId(entity.getKeysetId());
        return proof;
    }

    public static ProofEntity fromProof(@NonNull Proof proof) {
        ProofEntity proofEntity = new ProofEntity();
        proofEntity.setAmount(proof.getAmount());
        proofEntity.setSecret(proof.getSecret().toString());
        proofEntity.setSignature(proof.getUnblindedSignature().toString());
        proofEntity.setKeysetId(proof.getKeySetId());
        return proofEntity;
    }

}