package com.example.glpi.controllers;

import org.springframework.web.bind.annotation.*;

import com.example.glpi.repository.TicketRefRepository;
import com.example.glpi.entity.TicketRef;
import org.springframework.http.MediaType;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import java.util.List;

// Correspondances ref_ticket (CSV du 1er import) -> id GLPI.
// Le 1er import écrit ces paires ; l'import des mouvements les relit pour
// retrouver l'id GLPI à partir de la référence du CSV.
@RestController
@RequestMapping(value = "/api/ticketref", produces = MediaType.APPLICATION_JSON_VALUE)
@CrossOrigin(origins = "http://localhost:5173")
public class TicketRefController {

    @Autowired
    private TicketRefRepository repository;

    // Enregistre un lot de correspondances (remplace celles déjà présentes pour
    // les mêmes références afin de rester idempotent entre deux imports).
    //   POST /api/ticketref/batch
    //   body : [ { "ref": "1", "id_ticket": 759 }, ... ]
    @PostMapping(value = "/batch", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public List<TicketRef> createBatch(@RequestBody List<TicketRef> refs) {
        for (TicketRef r : refs) {
            repository.deleteAll(repository.findByRef(r.getRef()));
        }
        return repository.saveAll(refs);
    }

    // Toutes les correspondances.
    //   GET /api/ticketref
    @GetMapping
    public List<TicketRef> findAll() {
        return repository.findAll();
    }

    // Vide la table (réinitialisation des données). Idempotent.
    //   DELETE /api/ticketref
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAll() {
        repository.deleteAllInBatch();
    }
}
