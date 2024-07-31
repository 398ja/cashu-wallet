package cashu.wallet.db.controller;

import cashu.wallet.db.model.MintRequestEntity;
import cashu.wallet.db.repository.MintRequestEntityRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/mint/request")
public class MintRequestController {

    @Autowired
    private MintRequestEntityRepository repository;

    @PostMapping
    public ResponseEntity<MintRequestEntity> create(@RequestBody MintRequestEntity newMintRequestEntity) {
        var mintRequestEntity = repository.save(newMintRequestEntity);
        return ResponseEntity.ok(mintRequestEntity);
    }

    @GetMapping("/{correlation_id}")
    public ResponseEntity<List<MintRequestEntity>> getByCorrelationId(@PathVariable("correlation_id") String correlationId) {
        var mintRequestEntities = repository.findByCorrelationId(UUID.fromString(correlationId));
        return ResponseEntity.ok(mintRequestEntities);
    }

    @GetMapping("/{correlation_id}/{blind_message}")
    public ResponseEntity<MintRequestEntity> getByCorrelationIdAndBlindMessage(@PathVariable("correlation_id") String correlationId, @PathVariable("blind_message") String blindMessage) {
        var mintRequestEntity = repository.findByCorrelationIdAndBlindMessage(UUID.fromString(correlationId), blindMessage);
        return mintRequestEntity.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/")
    public ResponseEntity<Void> deleteAll() {
        repository.deleteAll();
        return ResponseEntity.ok().build();
    }

}
