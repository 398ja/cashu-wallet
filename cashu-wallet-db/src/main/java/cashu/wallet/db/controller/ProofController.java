package cashu.wallet.db.controller;


import cashu.wallet.db.model.ProofEntity;
import cashu.wallet.db.repository.ProofRepository;
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

@RestController
@RequestMapping("/proof")
public class ProofController {

    @Autowired
    private ProofRepository repository;

    @PostMapping
    public ResponseEntity<ProofEntity> create(@RequestBody ProofEntity newProofEntity) {
        var proofEntity = repository.save(newProofEntity);
        return ResponseEntity.ok(proofEntity);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") Integer id) {
        if (repository.existsById(id)) {
            repository.deleteById(id);
            return ResponseEntity.ok().build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/amount/{amount}/keyset/{keyset_id}")
    public ResponseEntity<ProofEntity> getByAmountAndKeysetId(@PathVariable("amount") Integer amount, @PathVariable("keyset_id") String keysetId) {
        var proofEntityList = repository.findByAmountAndKeysetId(amount, keysetId);
        if (proofEntityList.isEmpty()) {
            return ResponseEntity.notFound().build();
        } else {
            return ResponseEntity.ok(proofEntityList.get(0));
        }
    }

    @GetMapping("/keyset/{keyset_id}")
    public ResponseEntity<List<ProofEntity>> getByKeysetId(@PathVariable("keyset_id") String keysetId) {
        var proofEntityList = repository.findByKeysetId(keysetId);
        if (proofEntityList.isEmpty()) {
            return ResponseEntity.notFound().build();
        } else {
            return ResponseEntity.ok(proofEntityList);
        }
    }

    @GetMapping("/signature/{signature}")
    public ResponseEntity<ProofEntity> getBySignature(@PathVariable("signature") String signature) {
        var proofEntity = repository.findBySignature(signature);
        return proofEntity.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/")
    public ResponseEntity<List<ProofEntity>> getAll() {
        return ResponseEntity.ok(repository.findAll());
    }

    @DeleteMapping("/")
    public ResponseEntity<Void> deleteAll() {
        repository.deleteAll();
        return ResponseEntity.ok().build();
    }
}
