package cashu.wallet.db.controller;

import cashu.wallet.db.model.MeltQuoteResponseEntity;
import cashu.wallet.db.repository.MeltQuoteResponseEntityRepository;
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
@RequestMapping("/melt/quote/response")
public class MeltQuoteResponseController {

    @Autowired
    private MeltQuoteResponseEntityRepository repository;

    @PostMapping
    public ResponseEntity<MeltQuoteResponseEntity> create(@RequestBody MeltQuoteResponseEntity newMeltQuoteResponseEntity) {
        var meltQuoteResponseEntity = repository.save(newMeltQuoteResponseEntity);
        return ResponseEntity.ok(meltQuoteResponseEntity);
    }

    @GetMapping("/correlation/{correlation_id}")
    public ResponseEntity<MeltQuoteResponseEntity> getByCorrelationId(@PathVariable("correlation_id") String correlationId) {
        var meltQuoteResponseEntity = repository.findByCorrelationId(UUID.fromString(correlationId));
        return meltQuoteResponseEntity.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/quote/{quote}")
    public ResponseEntity<MeltQuoteResponseEntity> getByQuote(@PathVariable("quote") String quote) {
        var meltQuoteResponseEntity = repository.findByQuote(quote);
        return meltQuoteResponseEntity.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/")
    public ResponseEntity<Void> deleteAll() {
        repository.deleteAll();
        return ResponseEntity.ok().build();
    }

}
