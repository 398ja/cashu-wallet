package cashu.wallet.db.controller;

import cashu.wallet.db.model.MintQuoteResponseEntity;
import cashu.wallet.db.repository.MintQuoteResponseEntityRepository;
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
@RequestMapping("/mint/quote/response")
public class MintQuoteResponseController {

    @Autowired
    private MintQuoteResponseEntityRepository repository;

    @PostMapping
    public ResponseEntity<MintQuoteResponseEntity> create(@RequestBody MintQuoteResponseEntity newMintQuoteResponseEntity) {
        var mintQuoteResponseEntity = repository.save(newMintQuoteResponseEntity);
        return ResponseEntity.ok(mintQuoteResponseEntity);
    }

    @GetMapping("/quote/{quote}")
    public ResponseEntity<MintQuoteResponseEntity> getByQuote(@PathVariable("quote") String quote) {
        var mintQuoteResponseEntity = repository.findByQuote(quote);
        return mintQuoteResponseEntity.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/correlation/{correlation_id}")
    public ResponseEntity<MintQuoteResponseEntity> getByCorrelationId(@PathVariable("correlation_id") String correlationId) {
        var mintQuoteResponseEntity = repository.findByCorrelationId(UUID.fromString(correlationId));
        return mintQuoteResponseEntity.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/")
    public ResponseEntity<Void> deleteAll() {
        repository.deleteAll();
        return ResponseEntity.ok().build();
    }

}
