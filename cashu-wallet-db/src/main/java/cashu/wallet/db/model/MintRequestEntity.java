package cashu.wallet.db.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "t_mint_request")
@ToString
public class MintRequestEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    @JsonProperty
    private Integer id;

    @Column(name = "secret", nullable = false, length = Integer.MAX_VALUE)
    @JsonProperty
    private String secret;

    @Column(name = "blinding_factor", nullable = false, length = Integer.MAX_VALUE)
    @JsonProperty("blinding_factor")
    private String blindingFactor;

    @Column(name = "correlation_id", nullable = false)
    @JsonProperty("correlation_id")
    private UUID correlationId;

    @Column(name = "amount", nullable = false)
    @JsonProperty("amount")
    private Integer amount;

    @Column(name = "keyset_id", nullable = false, length = Integer.MAX_VALUE)
    @JsonProperty("keyset_id")
    private String keysetId;

    @Column(name = "blind_message", nullable = false, length = Integer.MAX_VALUE)
    @JsonProperty("blind_message")
    private String blindMessage;
}