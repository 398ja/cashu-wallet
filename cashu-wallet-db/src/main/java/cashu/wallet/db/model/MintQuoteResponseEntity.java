package cashu.wallet.db.model;

import cashu.common.model.rest.PostMintQuoteResponse;
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
@Table(name = "t_mint_quote_response")
@ToString
public class MintQuoteResponseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    @JsonProperty
    private Integer id;
    @Column(name = "quote", nullable = false, length = Integer.MAX_VALUE)
    @JsonProperty
    private String quote;
    @Column(name = "request", nullable = false, length = Integer.MAX_VALUE)
    @JsonProperty
    private String request;

    @Column(name = "correlation_id", nullable = false)
    @JsonProperty("correlation_id")
    private UUID correlationId;

    public static PostMintQuoteResponse toPostMintQuoteResponse(@NonNull MintQuoteResponseEntity entity) {
        PostMintQuoteResponse postMintQuoteResponse = new PostMintQuoteResponse();
        postMintQuoteResponse.setQuoteId(entity.getQuote());
        postMintQuoteResponse.setRequest(entity.getRequest());
        return postMintQuoteResponse;
    }
}