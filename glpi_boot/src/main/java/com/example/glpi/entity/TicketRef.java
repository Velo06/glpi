package com.example.glpi.entity;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.persistence.*;

// Correspondance entre la référence d'un ticket dans le CSV du premier import
// (ref_ticket, ex: "1", "T-12"...) et l'id GLPI réellement attribué à la
// création (ex: 759). Renseignée au moment du premier import, relue par
// l'import des mouvements (cancel / open / close) qui ne connaît que la
// référence, pas l'id GLPI.
@Entity
@Table(name = "ticket_ref")
public class TicketRef {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Référence telle qu'écrite dans le CSV (chaîne libre).
    @JsonProperty("ref")
    @Column(name = "ref", nullable = false)
    private String ref;

    // Id GLPI du ticket créé pour cette référence.
    @JsonProperty("id_ticket")
    @Column(name = "id_ticket", nullable = false)
    private Long idTicket;

    public TicketRef() {
    }

    public TicketRef(Long id, String ref, Long idTicket) {
        this.id = id;
        this.ref = ref;
        this.idTicket = idTicket;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRef() {
        return ref;
    }

    public void setRef(String ref) {
        this.ref = ref;
    }

    public Long getIdTicket() {
        return idTicket;
    }

    public void setIdTicket(Long idTicket) {
        this.idTicket = idTicket;
    }
}
