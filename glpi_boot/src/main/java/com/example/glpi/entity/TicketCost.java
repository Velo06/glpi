package com.example.glpi.entity;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "ticket_cost")
public class TicketCost {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // @JsonProperty aligne la clé JSON sur le nom de la colonne SQLite
    // (ce que le front envoie en POST et lit en GET).
    @JsonProperty("id_ticket")
    @Column(name = "id_ticket", nullable = false)
    private Long idTicket;

    // Relation vers type_cout (super_cout, cout_reouverture, cout_glpi...).
    // La colonne SQL "type_cout" reste l'INTEGER FK vers type_cout.id.
    @JsonProperty("type_cout")
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "type_cout", nullable = false)
    private TypeCout typeCout;

    @Column(name = "cout", nullable = false)
    private Double cout;

    @JsonProperty("id_item")
    @Column(name = "id_item")
    private Long idItem;

    // Type d'élément GLPI (chaîne : "Computer", "Monitor"...). On stocke le
    // libellé GLPI tel quel pour pouvoir regrouper dessus (page vaovao).
    @JsonProperty("id_item_type")
    @Column(name = "id_item_type")
    private String idItemType;

    @Column(name = "insertion")
    private LocalDateTime insertion = LocalDateTime.now();

    @Column(name = "annule")
    private boolean annule;

    public boolean isAnnule() {
        return annule;
    }

    public void setAnnule(boolean annule) {
        this.annule = annule;
    }

    public TicketCost() {
    }

    public TicketCost(Long id, Long idTicket, TypeCout typeCout, Double cout,
                      Long idItem, String idItemType, LocalDateTime insertion, boolean annule) {
        this.id = id;
        this.idTicket = idTicket;
        this.typeCout = typeCout;
        this.cout = cout;
        this.idItem = idItem;
        this.idItemType = idItemType;
        this.insertion = insertion;
        this.annule = annule;
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

    public TypeCout getTypeCout() {
        return typeCout;
    }

    public void setTypeCout(TypeCout typeCout) {
        this.typeCout = typeCout;
    }

    public Double getCout() {
        return cout;
    }

    public void setCout(Double cout) {
        this.cout = cout;
    }

    public Long getIdItem() {
        return idItem;
    }

    public void setIdItem(Long idItem) {
        this.idItem = idItem;
    }

    public String getIdItemType() {
        return idItemType;
    }

    public void setIdItemType(String idItemType) {
        this.idItemType = idItemType;
    }

    public LocalDateTime getInsertion() {
        return insertion;
    }

    public void setInsertion(LocalDateTime insertion) {
        this.insertion = insertion;
    }
}
