package com.example.glpi.controllers;

import org.springframework.web.bind.annotation.*;

import com.example.glpi.repository.TicketCostRepository;
import com.example.glpi.repository.TypeCoutRepository;
import com.example.glpi.repository.ReouvertureEventRepository;
import com.example.glpi.entity.TicketCost;
import com.example.glpi.entity.TypeCout;
import com.example.glpi.entity.ReouvertureEvent;
import com.example.glpi.entity.CostByItemType;
import com.example.glpi.entity.CostByTicket;
import com.example.glpi.dto.ReouvertureListDTO;
import com.example.glpi.dto.SupercostListDTO;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(value = "/api/ticketcost", produces = MediaType.APPLICATION_JSON_VALUE)
@CrossOrigin(origins = "http://localhost:5173")
public class TicketCostController {

    // Identifiants des types de coût (cf. table type_cout / script.sql).
    private static final int TYPE_SUPER_COUT = 1;
    private static final int TYPE_REOUVERTURE = 2;

    @Autowired
    private TicketCostRepository repository;

    @Autowired
    private TypeCoutRepository typeCoutRepository;

    @Autowired
    private ReouvertureEventRepository reouvertureRepository;

    // ---------- CRUD ----------

    // CREATE
    //   POST /api/ticketcost
    //   body : { "id_ticket": 12, "type_cout": 1, "cout": 150.0,
    //            "id_item": 5, "id_item_type": 1 }
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public TicketCost create(@RequestBody TicketCost ticketCost) {
        ticketCost.setTypeCout(resolveTypeCout(ticketCost.getTypeCout()));
        if (ticketCost.getInsertion() == null) {
            ticketCost.setInsertion(LocalDateTime.now());
        }
        ticketCost.setAnnule(false);
        return repository.save(ticketCost);
    }

    // CREATE en lot — toutes les lignes partagent la MÊME insertion.
    //   POST /api/ticketcost/batch
    //   body : [ { id_ticket, type_cout, cout, id_item, id_item_type }, ... ]
    // Indispensable pour la clôture (lignes super_cout + cout_glpi par élément)
    // et la réouverture : un lot = une insertion, ce qui permet ensuite de
    // retrouver/supprimer "le(s) dernier(s)" coût(s) par égalité d'insertion.
    @PostMapping(value = "/batch", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public List<TicketCost> createBatch(@RequestBody List<TicketCost> lignes) {
        LocalDateTime now = LocalDateTime.now();
        for (TicketCost tc : lignes) {
            tc.setTypeCout(resolveTypeCout(tc.getTypeCout()));
            tc.setInsertion(now);
            // Une création en lot = des lignes NEUVES : jamais annulées, quelle que
            // soit la valeur envoyée (cohérent avec le POST unitaire ci-dessus).
            tc.setAnnule(false);
        }
        return repository.saveAll(lignes);
    }

    // READ all
    //   GET /api/ticketcost
    @GetMapping
    public List<TicketCost> findAll() {
        return repository.findAll();
    }

    // READ one
    //   GET /api/ticketcost/{id}
    @GetMapping("/{id}")
    public TicketCost findById(@PathVariable Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    // READ by ticket
    //   GET /api/ticketcost/ticket/{idTicket}
    @GetMapping("/ticket/{idTicket}")
    public List<TicketCost> findByTicket(@PathVariable Long idTicket) {
        return repository.findByIdTicketAndAnnuleFalse(idTicket);
    }

    // READ by ticket + type de coût
    //   GET /api/ticketcost/ticket/{idTicket}/type/{typeCout}
    @GetMapping("/ticket/{idTicket}/type/{typeCout}")
    public List<TicketCost> findByTicketAndType(@PathVariable Long idTicket,
                                                @PathVariable Integer typeCout) {
        return repository.findByIdTicketAndTypeCout_IdAndAnnuleFalse(idTicket, typeCout);
    }

    // UPDATE (mise à jour partielle par id)
    //   PUT /api/ticketcost/{id}
    @PutMapping("/{id}")
    public TicketCost update(@PathVariable Long id, @RequestBody TicketCost payload) {
        TicketCost tc = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (payload.getIdTicket() != null) {
            tc.setIdTicket(payload.getIdTicket());
        }
        if (payload.getTypeCout() != null) {
            tc.setTypeCout(resolveTypeCout(payload.getTypeCout()));
        }
        if (payload.getCout() != null) {
            tc.setCout(payload.getCout());
        }
        if (payload.getIdItem() != null) {
            tc.setIdItem(payload.getIdItem());
        }
        if (payload.getIdItemType() != null) {
            tc.setIdItemType(payload.getIdItemType());
        }
        return repository.save(tc);
    }

    // DELETE all — vide entièrement la table ticket_cost (réinitialisation des
    // données). Idempotent : pas de 404 si la table est déjà vide.
    //   DELETE /api/ticketcost
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAll() {
        repository.deleteAllInBatch();
    }

    // DELETE par id
    //   DELETE /api/ticketcost/{id}
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        TicketCost tc = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        repository.deleteById(tc.getId());
    }

    // ---------- Fonctions spécifiques ----------

    // Renvoie les DERNIÈRES lignes d'un ticket = toutes celles partageant
    // l'insertion la plus récente (il peut y en avoir plusieurs).
    //   GET /api/ticketcost/ticket/{idTicket}/last
    @GetMapping("/ticket/{idTicket}/last")
    public List<TicketCost> findLastByTicket(@PathVariable Long idTicket) {
        List<TicketCost> last = repository.findLastByIdTicket(idTicket);
        if (last.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return last;
    }

    // Supprime les DERNIÈRES lignes d'un ticket (tout le lot de l'insertion
    // la plus récente, pas une seule ligne).
    //   DELETE /api/ticketcost/ticket/{idTicket}/last
    @DeleteMapping("/ticket/{idTicket}/last")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteLastByTicket(@PathVariable Long idTicket) {
        List<TicketCost> last = repository.findLastByIdTicket(idTicket);
        if (last.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        repository.deleteAll(last);
    }

    // Supprime TOUTES les lignes d'un type de coût pour un ticket (idempotent,
    // pas de 404 si vide). Sert à rafraîchir le coût import GLPI à chaque
    // clôture : on remplace l'ancien instantané plutôt que de l'accumuler
    // (le coût import est intrinsèque au ticket, il ne doit compter qu'une fois).
    //   DELETE /api/ticketcost/ticket/{idTicket}/type/{typeCout}
    @DeleteMapping("/ticket/{idTicket}/type/{typeCout}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteByTicketAndType(@PathVariable Long idTicket,
                                      @PathVariable Integer typeCout) {
        repository.deleteAll(repository.findByIdTicketAndTypeCout_IdAndAnnuleFalse(idTicket, typeCout));
    }

    // Dernier LOT d'un type de coût donné (toutes les lignes de ce type
    // partageant l'insertion la plus récente parmi ce type). Sert au calcul
    // de la réouverture (pourcentage sur le dernier super_cout, par élément).
    //   GET /api/ticketcost/ticket/{idTicket}/type/{typeCout}/last
    @GetMapping("/ticket/{idTicket}/type/{typeCout}/last")
    public List<TicketCost> findLastByTicketAndType(@PathVariable Long idTicket,
                                                    @PathVariable Integer typeCout) {
        List<TicketCost> last = repository.findLastByIdTicketAndType(idTicket, typeCout);
        if (last.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return last;
    }

    @GetMapping("/ticket/{idTicket}/type/{typeCout}/first")
    public List<TicketCost> findFirstByTicketAndType(@PathVariable Long idTicket,
                                                    @PathVariable Integer typeCout) {
        List<TicketCost> last = repository.findFirstByIdTicketAndType(idTicket, typeCout);
        if (last.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return last;
    }

    // Supprime le dernier LOT d'un type de coût donné. Pour l'annulation :
    // on retire uniquement le(s) dernier(s) super_cout, les lignes cout_glpi
    // de la même clôture restent.
    //   DELETE /api/ticketcost/ticket/{idTicket}/type/{typeCout}/last
    @DeleteMapping("/ticket/{idTicket}/type/{typeCout}/last")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteLastByTicketAndType(@PathVariable Long idTicket,
                                          @PathVariable Integer typeCout) {
        List<TicketCost> last = repository.findLastByIdTicketAndType(idTicket, typeCout);
        if (last.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        repository.deleteAll(last);
    }

    @PutMapping("/ticket/{idTicket}/type/{typeCout}/last")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void updateLastByTicketAndType(@PathVariable Long idTicket,
                                          @PathVariable Integer typeCout) {
        List<TicketCost> last = repository.findLastByIdTicketAndType(idTicket, typeCout);
        if (last.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        for (TicketCost tc : last) {
            tc.setAnnule(true);
        }
        repository.saveAll(last);

        // Annuler un super cost retire ce montant de la base : les réouvertures qui
        // s'appuyaient dessus doivent être recalculées (base plus faible).
        if (typeCout != null && typeCout == TYPE_SUPER_COUT) {
            recomputeAllReouvertures(idTicket);
        }
    }

    // Coûts agrégés par type d'élément (page vaovao) : pour chaque id_item_type,
    // la somme des cout_glpi, super_cout et cout_reouverture, plus le total.
    //   GET /api/ticketcost/by-itemtype
    @GetMapping("/by-itemtype")
    public List<CostByItemType> costByItemType() {
        Map<String, CostByItemType> parType = new LinkedHashMap<>();
        for (Object[] row : repository.sumCostByItemType()) {
            String itemtype = (String) row[0];
            int typeCout = ((Number) row[1]).intValue();
            double montant = ((Number) row[2]).doubleValue();
            parType.computeIfAbsent(itemtype, CostByItemType::new).add(typeCout, montant);
        }
        return new ArrayList<>(parType.values());
    }

    @GetMapping("/by-itemtype/avg")
    public List<CostByItemType> avgCostByItemType() {
        Map<String, CostByItemType> parType = new LinkedHashMap<>();
        for (Object[] row : repository.avgCostByItemType()) {
            String itemtype = (String) row[0];
            int typeCout = ((Number) row[1]).intValue();
            double montant = ((Number) row[2]).doubleValue();
            parType.computeIfAbsent(itemtype, CostByItemType::new).add(typeCout, montant);
        }
        return new ArrayList<>(parType.values());
    }

    @GetMapping("/by-itemtype/sum")
    public List<CostByItemType> sommeCostByItemType() {
        Map<String, CostByItemType> parType = new LinkedHashMap<>();
        for (Object[] row : repository.sumCostSpecByItemType()) {
            String itemtype = (String) row[0];
            int typeCout = ((Number) row[1]).intValue();
            double montant = ((Number) row[2]).doubleValue();
            parType.computeIfAbsent(itemtype, CostByItemType::new).add(typeCout, montant);
        }
        return new ArrayList<>(parType.values());
    }

    // ---------- Helpers ----------

    // Le front envoie type_cout sous forme d'objet ({"id": 1}). On valide l'id
    // et on renvoie l'entité managée correspondante, à rattacher au TicketCost.
    private TypeCout resolveTypeCout(TypeCout typeCout) {
        if (typeCout == null || typeCout.getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "type_cout invalide ou inexistant");
        }
        return typeCoutRepository.findById(typeCout.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "type_cout invalide ou inexistant"));
    }

    @GetMapping("/detail/{item_type}")
    public List<TicketCost> getDetailCostByItemType(@PathVariable String item_type) {
        List<TicketCost> list = repository.getDetailCostByItemType(item_type);
        for(TicketCost tc : list) {
            System.out.println("LIST:" + tc.getTypeCout().getId());
        }
        return list;
    }

    @GetMapping("/by-ticket/{idTicket}/{idTypeCout}")
    public double costByIdTicket(@PathVariable Long idTicket, @PathVariable Long idTypeCout) {
        double sum_cost = repository.sumCostByTicket(idTicket, idTypeCout);
        return sum_cost;
    }

    // =====================================================================
    //  NOUVELLE PAGE « liste des coûts » : réouvertures + super costs
    // =====================================================================

    // Liste des réouvertures avec le POURCENTAGE D'ORIGINE (et non la valeur
    // calculée), son mode, et le total calculé du lot (informatif).
    //   GET /api/ticketcost/reouvertures
    @GetMapping("/reouvertures")
    public List<ReouvertureListDTO> listReouvertures() {
        // total calculé par lot (ticket, insertion), indexé pour jointure.
        Map<String, Double> totals = new LinkedHashMap<>();
        for (Object[] row : repository.sumReouvertureByBatch(TYPE_REOUVERTURE)) {
            Long idTicket = ((Number) row[0]).longValue();
            LocalDateTime insertion = (LocalDateTime) row[1];
            double total = ((Number) row[2]).doubleValue();
            totals.put(batchKey(idTicket, insertion), total);
        }

        List<ReouvertureListDTO> out = new ArrayList<>();
        for (ReouvertureEvent ev : reouvertureRepository.findAll()) {
            Double total = totals.get(batchKey(ev.getIdTicket(), ev.getInsertion()));
            out.add(new ReouvertureListDTO(ev.getId(), ev.getIdTicket(),
                    ev.getPourcentage(), ev.getMode(), total != null ? total : 0.0));
        }
        return out;
    }

    // Liste des super costs : un lot par (ticket, insertion), avec le montant
    // total et une poignée d'édition (id d'une ligne du lot).
    //   GET /api/ticketcost/supercosts
    @GetMapping("/supercosts")
    public List<SupercostListDTO> listSupercosts() {
        List<SupercostListDTO> out = new ArrayList<>();
        for (Object[] row : repository.listBatchesByType(TYPE_SUPER_COUT)) {
            Long lineId = ((Number) row[0]).longValue();
            Long idTicket = ((Number) row[1]).longValue();
            double total = ((Number) row[2]).doubleValue();
            out.add(new SupercostListDTO(lineId, idTicket, total));
        }
        return out;
    }

    // Création CENTRALISÉE d'une réouverture (remplace la logique des 4 modes
    // côté front, qui était dupliquée et fausse pour le mode 3). Le backend :
    //   1. calcule, par élément, la base selon le mode (sur les super costs
    //      EXISTANTS du ticket) puis cout_reouverture = base × %/100 ;
    //   2. insère le lot cout_reouverture (une ligne par élément) ;
    //   3. enregistre le reouverture_event (pourcentage + mode) avec la MÊME
    //      insertion, pour pouvoir afficher le % d'origine et recalculer plus
    //      tard.
    //   POST /api/ticketcost/reouverture
    //   body : { "id_ticket": 12, "pourcentage": 10, "mode": 3 }
    @PostMapping(value = "/reouverture", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public ReouvertureEvent createReouverture(@RequestBody Map<String, Object> body) {
        Long idTicket = asLong(body.get("id_ticket"));
        Double pourcentage = asDouble(body.get("pourcentage"));
        Integer mode = asInt(body.get("mode"));
        if (idTicket == null || pourcentage == null || mode == null || mode < 1 || mode > 4) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "id_ticket, pourcentage et mode (1..4) sont requis");
        }

        LocalDateTime now = LocalDateTime.now();

        // Base = TOUS les super costs actuels du ticket (insertion <= now).
        List<TicketCost> supers = repository
                .findByIdTicketAndTypeCout_IdAndAnnuleFalseAndInsertionLessThanEqual(idTicket, TYPE_SUPER_COUT, now);

        // Pas encore de super cost : pas de base → on enregistre quand même
        // l'événement (% + mode) pour garder une trace ; aucune ligne de coût.
        List<TicketCost> lignes = computeReouvertureLines(supers, mode, pourcentage, idTicket, now);
        if (!lignes.isEmpty()) {
            for(TicketCost tc : lignes) {
                tc.setAnnule(false);
            }
            repository.saveAll(lignes);
        }

        return reouvertureRepository.save(new ReouvertureEvent(idTicket, now, pourcentage, mode));
    }

    // Édition d'une réouverture : nouveau pourcentage et/ou mode. On recalcule
    // ses lignes cout_reouverture à partir de la MÊME base (super costs <=
    // insertion de la réouverture) — l'insertion ne change pas, donc la position
    // sur la timeline reste identique.
    //   PUT /api/ticketcost/reouverture/{eventId}
    //   body : { "pourcentage": 15, "mode": 2 }
    @PutMapping(value = "/reouverture/{eventId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Transactional
    public ReouvertureEvent updateReouverture(@PathVariable Long eventId,
                                              @RequestBody Map<String, Object> body) {
        ReouvertureEvent ev = reouvertureRepository.findById(eventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "réouverture introuvable"));

        Double pourcentage = asDouble(body.get("pourcentage"));
        Integer mode = asInt(body.get("mode"));
        if (pourcentage != null) ev.setPourcentage(pourcentage);
        if (mode != null) {
            if (mode < 1 || mode > 4) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "mode invalide (1..4)");
            }
            ev.setMode(mode);
        }
        reouvertureRepository.save(ev);

        // Le % / mode de CETTE réouverture change son montant : on recalcule ses
        // lignes (on recalcule tout le ticket, c'est idempotent pour les autres).
        recomputeAllReouvertures(ev.getIdTicket());
        return ev;
    }

    // Édition d'un super cost (par l'id d'une de ses lignes). Le nouveau montant
    // total est RÉPARTI à l'identique sur les éléments du lot, puis TOUTES les
    // réouvertures POSTÉRIEURES (insertion strictement supérieure) sont
    // recalculées : ce sont les seules dont la base peut dépendre de ce super
    // cost. C'est le point clé demandé : « modifier un super cost impacte le
    // calcul des réouvertures qui suivent ».
    //   PUT /api/ticketcost/supercost/{lineId}
    //   body : { "montant": 120 }
    @PutMapping(value = "/supercost/{lineId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Transactional
    public List<TicketCost> updateSupercost(@PathVariable Long lineId,
                                            @RequestBody Map<String, Object> body) {
        Double montant = asDouble(body.get("montant"));
        if (montant == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "montant requis");
        }

        TicketCost ref = repository.findById(lineId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "ligne de super cost introuvable"));
        if (ref.getTypeCout() == null || ref.getTypeCout().getId() != TYPE_SUPER_COUT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "cette ligne n'est pas un super cost");
        }

        Long idTicket = ref.getIdTicket();
        LocalDateTime insertion = ref.getInsertion();

        // Lignes du lot (réparties par élément) → on redistribue le nouveau
        // montant à parts égales (même règle qu'à la création).
        List<TicketCost> lot = repository
                .findByIdTicketAndTypeCout_IdAndInsertionAndAnnuleFalse(idTicket, TYPE_SUPER_COUT, insertion);
        int n = lot.size();
        if (n > 0) {
            double part = montant / n;
            for (TicketCost tc : lot) {
                tc.setCout(part);
            }
            repository.saveAll(lot);
        }

        // Recalcul de TOUTES les réouvertures du ticket, dans l'ordre chronologique :
        // changer ce super cost modifie la base des réouvertures postérieures.
        recomputeAllReouvertures(idTicket);

        return lot;
    }

    // ---------- Helpers de calcul des réouvertures ----------

    // Recalcule les lignes cout_reouverture d'UNE réouverture : on repart de la
    // base (super costs du ticket d'insertion <= celle de la réouverture, donc
    // ceux qui existaient à ce moment, avec leurs valeurs ACTUELLES), on
    // supprime l'ancien lot cout_reouverture et on réinsère le nouveau, en
    // conservant la même insertion (le lien event ↔ lot reste valide).
    // Recalcule TOUTES les réouvertures d'un ticket, dans l'ordre CHRONOLOGIQUE.
    // (Dé)annuler ou modifier un super cost change la base des réouvertures qui
    // suivent (celles dont l'insertion est postérieure) ; on recalcule donc tout
    // le ticket pour rester cohérent.
    private void recomputeAllReouvertures(Long idTicket) {
        for (ReouvertureEvent ev : reouvertureRepository.findByIdTicketOrderByInsertionAsc(idTicket)) {
            recomputeReouverture(ev);
        }
    }

    private void recomputeReouverture(ReouvertureEvent ev) {
        List<TicketCost> supers = repository.findByIdTicketAndTypeCout_IdAndAnnuleFalseAndInsertionLessThanEqual(
                ev.getIdTicket(), TYPE_SUPER_COUT, ev.getInsertion());

        List<TicketCost> anciennes = repository.findByIdTicketAndTypeCout_IdAndInsertionAndAnnuleFalse(
                ev.getIdTicket(), TYPE_REOUVERTURE, ev.getInsertion());
        if (!anciennes.isEmpty()) {
            repository.deleteAll(anciennes);
        }

        List<TicketCost> nouvelles = computeReouvertureLines(
                supers, ev.getMode(), ev.getPourcentage(), ev.getIdTicket(), ev.getInsertion());
        if (!nouvelles.isEmpty()) {
            repository.saveAll(nouvelles);
        }
    }

    // Construit les lignes cout_reouverture (une par élément) à partir des super
    // costs fournis, du mode et du pourcentage. La base par élément suit
    // EXACTEMENT les 4 modes définis (cf. MODE_CALCUL_REOUVERTURE.md) :
    //   1 = dernier lot, 2 = premier lot, 3 = moyenne, 4 = somme.
    private List<TicketCost> computeReouvertureLines(List<TicketCost> supers, int mode,
                                                     double pourcentage, Long idTicket,
                                                     LocalDateTime insertion) {
        List<TicketCost> out = new ArrayList<>();
        if (supers == null || supers.isEmpty()) {
            return out;
        }
        double f = pourcentage / 100.0;
        TypeCout typeReouv = typeCoutRepository.findById(TYPE_REOUVERTURE)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "type_cout cout_reouverture absent"));

        // Pour les modes 1 (dernier) et 2 (premier), la base se limite aux
        // lignes du lot le plus récent / le plus ancien (un lot = une insertion).
        LocalDateTime cible = null;
        if (mode == 1) {
            cible = supers.stream().map(TicketCost::getInsertion).max(Comparator.naturalOrder()).orElse(null);
        } else if (mode == 2) {
            cible = supers.stream().map(TicketCost::getInsertion).min(Comparator.naturalOrder()).orElse(null);
        }

        // Nombre de clôtures (lots distincts) — utile au mode 3 (moyenne).
        long nbCloture = supers.stream().map(TicketCost::getInsertion).distinct().count();
        if (nbCloture == 0) nbCloture = 1;

        // Regroupement par élément, en conservant l'ordre d'apparition.
        Map<String, List<TicketCost>> parElement = new LinkedHashMap<>();
        for (TicketCost s : supers) {
            parElement.computeIfAbsent(elementKey(s), k -> new ArrayList<>()).add(s);
        }

        for (List<TicketCost> lignesElem : parElement.values()) {
            double base;
            switch (mode) {
                case 1: // dernier lot
                case 2: { // premier lot
                    final LocalDateTime t = cible;
                    base = lignesElem.stream()
                            .filter(l -> l.getInsertion() != null && l.getInsertion().equals(t))
                            .mapToDouble(l -> nz(l.getCout()))
                            .sum();
                    // Élément absent du lot ciblé : pas de base → pas de ligne.
                    if (base == 0.0 && lignesElem.stream().noneMatch(
                            l -> l.getInsertion() != null && l.getInsertion().equals(t))) {
                        continue;
                    }
                    break;
                }
                case 3: // moyenne = (somme des coûts de l'élément) / nb de clôtures
                    base = lignesElem.stream().mapToDouble(l -> nz(l.getCout())).sum() / nbCloture;
                    break;
                case 4: // somme de tous les coûts de l'élément
                default:
                    base = lignesElem.stream().mapToDouble(l -> nz(l.getCout())).sum();
                    break;
            }

            TicketCost rep = lignesElem.get(0); // id_item / id_item_type représentatifs
            TicketCost ligne = new TicketCost();
            ligne.setIdTicket(idTicket);
            ligne.setTypeCout(typeReouv);
            ligne.setCout(base * f);
            ligne.setIdItem(rep.getIdItem());
            ligne.setIdItemType(rep.getIdItemType());
            ligne.setInsertion(insertion);
            ligne.setAnnule(false);
            out.add(ligne);
        }
        return out;
    }

    private static String elementKey(TicketCost tc) {
        return tc.getIdItemType() + "#" + tc.getIdItem();
    }

    private static String batchKey(Long idTicket, LocalDateTime insertion) {
        return idTicket + "@" + insertion;
    }

    private static double nz(Double d) {
        return d == null ? 0.0 : d;
    }

    private static Long asLong(Object o) {
        return o == null ? null : ((Number) o).longValue();
    }

    private static Integer asInt(Object o) {
        return o == null ? null : ((Number) o).intValue();
    }

    private static Double asDouble(Object o) {
        if (o == null) return null;
        if (o instanceof Number) return ((Number) o).doubleValue();
        return Double.parseDouble(o.toString().replace(",", "."));
    }
}
