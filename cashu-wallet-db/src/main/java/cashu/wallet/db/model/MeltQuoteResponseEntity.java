package cashu.wallet.db.model;

import cashu.common.model.rest.PostMeltQuoteResponse;
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
@Table(name = "t_melt_quote_response")
@ToString
public class MeltQuoteResponseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    @JsonProperty
    private Integer id;

    @Column(name = "quote", nullable = false, length = Integer.MAX_VALUE)
    @JsonProperty
    private String quote;

    @Column(name = "correlation_id", nullable = false)
    @JsonProperty("correlation_id")
    private UUID correlationId;

    @Column(name = "amount", nullable = false)
    @JsonProperty
    private Integer amount;

    @Column(name = "fee_reserve", nullable = false)
    @JsonProperty("fee_reserve")
    private Integer feeReserve;

    public static PostMeltQuoteResponse toPostMeltQuoteResponse(@NonNull MeltQuoteResponseEntity entity) {
        PostMeltQuoteResponse postMeltQuoteResponse = new PostMeltQuoteResponse();
        postMeltQuoteResponse.setQuoteId(entity.getQuote());
        postMeltQuoteResponse.setAmount(entity.getAmount());
        postMeltQuoteResponse.setFeeReserve(entity.getFeeReserve());
        return postMeltQuoteResponse;
    }

}