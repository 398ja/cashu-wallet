package cashu.wallet.db.model;

import cashu.common.model.rest.PostMintQuoteRequest;
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

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "t_mint_quote_request")
@ToString
public class MintQuoteRequestEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    @JsonProperty
    private Integer id;

    @Column(name = "amount")
    @JsonProperty
    private Integer amount;

    @Column(name = "unit", length = Integer.MAX_VALUE)
    @JsonProperty
    private String unit;

    @Column(name = "correlation_id")
    @JsonProperty("correlation_id")
    private UUID correlationId;

    @Column(name = "payment_method", nullable = false, length = Integer.MAX_VALUE)
    @JsonProperty("payment_method")
    private String paymentMethod;

    public static PostMintQuoteRequest toPostMintQuoteRequest(@NonNull MintQuoteRequestEntity entity) {
        PostMintQuoteRequest postMintQuoteRequest = new PostMintQuoteRequest();
        postMintQuoteRequest.setAmount(entity.getAmount());
        postMintQuoteRequest.setUnit(entity.getUnit());
        return postMintQuoteRequest;
    }
}