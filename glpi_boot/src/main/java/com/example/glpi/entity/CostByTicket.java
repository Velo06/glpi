package com.example.glpi.entity;

import com.fasterxml.jackson.annotation.JsonProperty;

// Ligne agrégée de la page vaovao : pour un type d'élément, la somme des
// coûts GLPI, super cost et réouverture (+ le total des trois).
// Les clés JSON sont alignées sur ce que lit le front (PageVaovao.jsx).
public class CostByTicket {

    @JsonProperty("type_cout")
    private String type_cout;

    @JsonProperty("cout")
    private double cout; // cout_glpi

    @JsonProperty("total")
    private double total;

    public CostByTicket(String type_cout) {
        this.type_cout = type_cout;
    }

    public void add(int typeCout, double montant) {
        // 1 = super_cout, 2 = cout_reouverture, 3 = cout_glpi (cf. type_cout)
        switch (typeCout) {
            case 3 -> this.cout += montant;
            default -> { /* type inconnu : ignoré */ }
        }
        this.total = this.cout;
    }

    public String getTypecout() {
        return type_cout;
    }

    public double getCout() {
        return cout;
    }

    public double getTotal() {
        return total;
    }
}
