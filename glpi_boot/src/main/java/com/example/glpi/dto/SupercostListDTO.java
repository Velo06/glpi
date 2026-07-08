package com.example.glpi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

// Ligne de la liste des super costs (nouvelle page « liste des coûts »).
// `lineId` est l'id d'UNE ligne représentative du lot (toutes les lignes d'un
// même super cost partagent l'insertion) : il sert de poignée d'édition côté
// front, sans avoir à transporter l'horodatage. `total` = montant du super
// cost (somme des lignes réparties par élément).
public class SupercostListDTO {

    @JsonProperty("lineId")
    private Long lineId;

    @JsonProperty("id_ticket")
    private Long idTicket;

    @JsonProperty("total")
    private Double total;

    public SupercostListDTO(Long lineId, Long idTicket, Double total) {
        this.lineId = lineId;
        this.idTicket = idTicket;
        this.total = total;
    }

    public Long getLineId() {
        return lineId;
    }

    public Long getIdTicket() {
        return idTicket;
    }

    public Double getTotal() {
        return total;
    }
}
