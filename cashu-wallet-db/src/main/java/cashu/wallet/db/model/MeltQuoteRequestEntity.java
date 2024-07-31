package cashu.wallet.db.model;

import cashu.common.model.rest.PostMeltQuoteRequest;
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
@Table(name = "t_melt_quote_request")
@ToString
public class MeltQuoteRequestEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    @JsonProperty
    private Integer id;

    @Column(name = "request", nullable = false, length = Integer.MAX_VALUE)
    @JsonProperty
    private String request;

    @Column(name = "correlation_id", nullable = false)
    @JsonProperty("correlation_id")
    private UUID correlationId;

    @Column(name = "unit", nullable = false, length = Integer.MAX_VALUE)
    @JsonProperty
    private String unit;

    @Column(name = "payment_method", nullable = false, length = Integer.MAX_VALUE)
    @JsonProperty("payment_method")
    private String paymentMethod;

    public static PostMeltQuoteRequest toPostMeltQuoteRequest(@NonNull MeltQuoteRequestEntity entity) {
        PostMeltQuoteRequest postMeltQuoteRequest = new PostMeltQuoteRequest();
        postMeltQuoteRequest.setRequestId(entity.getRequest());
        return postMeltQuoteRequest;
    }
}