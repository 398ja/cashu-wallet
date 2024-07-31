package cashu.wallet.db.controller;

import cashu.wallet.db.model.MeltQuoteRequestEntity;
import cashu.wallet.db.repository.MeltQuoteRequestEntityRepository;
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
@RequestMapping("/melt/quote/request")
public class MeltQuoteRequestController {

    @Autowired
    private MeltQuoteRequestEntityRepository repository;

    @PostMapping
    public ResponseEntity<MeltQuoteRequestEntity> create(@RequestBody MeltQuoteRequestEntity newMeltQuoteRequestEntity) {
        var meltQuoteRequestEntity = repository.save(newMeltQuoteRequestEntity);
        return ResponseEntity.ok(meltQuoteRequestEntity);
    }

    @GetMapping("/correlation/{correlation_id}")
    public ResponseEntity<MeltQuoteRequestEntity> getByCorrelationId(@PathVariable("correlation_id") String correlationId) {
        var meltQuoteRequestEntity = repository.findByCorrelationId(UUID.fromString(correlationId));
        return meltQuoteRequestEntity.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/")
    public ResponseEntity<Void> deleteAll() {
        repository.deleteAll();
        return ResponseEntity.ok().build();
    }

}
