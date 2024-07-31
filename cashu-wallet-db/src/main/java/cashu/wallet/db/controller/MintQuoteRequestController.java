package cashu.wallet.db.controller;

import cashu.wallet.db.model.MintQuoteRequestEntity;
import cashu.wallet.db.repository.MintQuoteRequestEntityRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/mint/quote/request")
public class MintQuoteRequestController {

    @Autowired
    private MintQuoteRequestEntityRepository repository;

    @PostMapping
    public ResponseEntity<MintQuoteRequestEntity> create(@RequestBody MintQuoteRequestEntity newMintQuoteRequestEntity) {
        var mintQuoteRequestEntity = repository.save(newMintQuoteRequestEntity);
        return ResponseEntity.ok(mintQuoteRequestEntity);
    }

    @GetMapping("/correlation/{correlation_id}")
    public ResponseEntity<MintQuoteRequestEntity> getByCorrelationId(@PathVariable("correlation_id") String correlationId) {
        var mintQuoteRequestEntity = repository.findByCorrelationId(UUID.fromString(correlationId));
        return mintQuoteRequestEntity.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/")
    public ResponseEntity<Void> deleteAll() {
        repository.deleteAll();
        return ResponseEntity.ok().build();
    }

}
