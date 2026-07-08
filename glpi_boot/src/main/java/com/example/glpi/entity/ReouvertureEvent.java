package com.example.glpi.entity;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.persistence.*;
import java.time.LocalDateTime;

// Métadonnées d'UNE réouverture (un lot cout_reouverture).
//
// Pourquoi une table dédiée ?
//   La table ticket_cost ne stocke que la VALEUR RÉSOLUE d'une réouverture
//   (cout = base × pourcentage/100). Or la nouvelle page doit afficher le
//   POURCENTAGE D'ORIGINE et pouvoir RECALCULER une réouverture quand un super
//   cost antérieur change. Recalculer exige de connaître le pourcentage ET le
//   mode de calcul — impossible à retrouver de façon fiable depuis le seul
//   « cout » (plusieurs couples (mode, %) donnent la même valeur).
//
//   On persiste donc ici les ENTRÉES du calcul (pourcentage + mode), sans
//   polluer ticket_cost : « cout » y reste la valeur résolue. Le lien entre un
//   reouverture_event et ses lignes cout_reouverture se fait par
//   (id_ticket, insertion) : toutes les lignes d'un même lot partagent
//   l'insertion de l'événement.
@Entity
@Table(name = "reouverture_event")
public class ReouvertureEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonProperty("id_ticket")
    @Column(name = "id_ticket", nullable = false)
    private Long idTicket;

    // Horodatage partagé avec le lot cout_reouverture correspondant
    // (ticket_cost.insertion). C'est aussi la position de la réouverture sur la
    // timeline du ticket : la base de calcul = les super costs d'insertion <=
    // celle-ci.
    @Column(name = "insertion", nullable = false)
    private LocalDateTime insertion;

    // Pourcentage SAISI par l'utilisateur (donnée d'origine, jamais recalculée).
    @JsonProperty("pourcentage")
    @Column(name = "pourcentage", nullable = false)
    private Double pourcentage;

    // Mode de calcul : 1 = dernier, 2 = premier, 3 = moyenne, 4 = somme.
    @JsonProperty("mode")
    @Column(name = "mode", nullable = false)
    private Integer mode;

    public ReouvertureEvent() {
    }

    public ReouvertureEvent(Long idTicket, LocalDateTime insertion, Double pourcentage, Integer mode) {
        this.idTicket = idTicket;
        this.insertion = insertion;
        this.pourcentage = pourcentage;
        this.mode = mode;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getIdTicket() {
        return idTicket;
    }

    public void setIdTicket(Long idTicket) {
        this.idTicket = idTicket;
    }

    public LocalDateTime getInsertion() {
        return insertion;
    }

    public void setInsertion(LocalDateTime insertion) {
        this.insertion = insertion;
    }

    public Double getPourcentage() {
        return pourcentage;
    }

    public void setPourcentage(Double pourcentage) {
        this.pourcentage = pourcentage;
    }

    public Integer getMode() {
        return mode;
    }

    public void setMode(Integer mode) {
        this.mode = mode;
    }
}
