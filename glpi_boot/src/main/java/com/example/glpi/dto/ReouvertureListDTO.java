package com.example.glpi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

// Ligne de la liste des réouvertures (nouvelle page « liste des coûts »).
// Affiche la référence du ticket et surtout le POURCENTAGE D'ORIGINE (pas la
// valeur calculée). `total` (somme des lignes cout_reouverture du lot) est
// fourni à titre informatif. `id` est l'identifiant de l'événement, utilisé
// pour l'édition.
public class ReouvertureListDTO {

    @JsonProperty("id")
    private Long id;

    @JsonProperty("id_ticket")
    private Long idTicket;

    @JsonProperty("pourcentage")
    private Double pourcentage;

    @JsonProperty("mode")
    private Integer mode;

    @JsonProperty("total")
    private Double total;

    public ReouvertureListDTO(Long id, Long idTicket, Double pourcentage, Integer mode, Double total) {
        this.id = id;
        this.idTicket = idTicket;
        this.pourcentage = pourcentage;
        this.mode = mode;
        this.total = total;
    }

    public Long getId() {
        return id;
    }

    public Long getIdTicket() {
        return idTicket;
    }

    public Double getPourcentage() {
        return pourcentage;
    }

    public Integer getMode() {
        return mode;
    }

    public Double getTotal() {
        return total;
    }
}
