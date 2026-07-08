package com.example.glpi.controllers;

import org.springframework.web.bind.annotation.*;

import com.example.glpi.repository.StatutRepository;
import com.example.glpi.entity.Statut;
import org.springframework.http.MediaType;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@RestController
@RequestMapping(value = "/api/statut", produces = MediaType.APPLICATION_JSON_VALUE)
@CrossOrigin(origins = "http://localhost:5173")
public class StatutController {

    @Autowired
    private StatutRepository statutRepo;

    // READ all
    //   GET /api/statut
    @GetMapping
    public List<Statut> getAllStatuts() {
        return statutRepo.findAll();
    }

    // READ one
    //   GET /api/statut/{id}
    @GetMapping("/{id}")
    public Statut getStatutById(@PathVariable Integer id) {
        return statutRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    // CREATE
    //   POST /api/statut
    //   body : { "nom_francais": "Nouveau", "nom_malgache": "Vaovao", "couleur_fond": "#2cf0c5" }
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public Statut createStatut(@RequestBody Statut payload) {
        if (payload.getCouleurFond() == null) {
            payload.setCouleurFond("#FFFFFF");
        }
        return statutRepo.save(payload);
    }

    // UPDATE
    //   PUT /api/statut/{id}
    @PutMapping("/{id}")
    public Statut updateStatut(@PathVariable Integer id,
                               @RequestBody Statut payload) {
        Statut statut = statutRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        if (payload.getNomFrancais() != null) {
            statut.setNomFrancais(payload.getNomFrancais());
        }
        if (payload.getNomMalgache() != null) {
            statut.setNomMalgache(payload.getNomMalgache());
        }
        if (payload.getCouleurFond() != null) {
            statut.setCouleurFond(payload.getCouleurFond());
        }

        return statutRepo.save(statut);
    }

    // DELETE
    //   DELETE /api/statut/{id}
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteStatut(@PathVariable Integer id) {
        Statut statut = statutRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        statutRepo.delete(statut);
    }
}
