package com.example.glpi.entity;

import com.fasterxml.jackson.annotation.JsonProperty;

// Ligne agrégée de la page vaovao : pour un type d'élément, la somme des
// coûts GLPI, super cost et réouverture (+ le total des trois).
// Les clés JSON sont alignées sur ce que lit le front (PageVaovao.jsx).
public class CostByItemType {

    @JsonProperty("itemtype")
    private String itemtype;

    @JsonProperty("cout")
    private double cout; // cout_glpi

    @JsonProperty("supercost")
    private double supercost; // super_cout

    @JsonProperty("reouverture")
    private double reouverture; // cout_reouverture

    @JsonProperty("total")
    private double total;

    public CostByItemType(String itemtype) {
        this.itemtype = itemtype;
    }

    public void add(int typeCout, double montant) {
        // 1 = super_cout, 2 = cout_reouverture, 3 = cout_glpi (cf. type_cout)
        switch (typeCout) {
            case 1 -> this.supercost += montant;
            case 2 -> this.reouverture += montant;
            case 3 -> this.cout += montant;
            default -> { /* type inconnu : ignoré */ }
        }
        this.total = this.cout + this.supercost + this.reouverture;
    }

    public String getItemtype() {
        return itemtype;
    }

    public double getCout() {
        return cout;
    }

    public double getSupercost() {
        return supercost;
    }

    public double getReouverture() {
        return reouverture;
    }

    public double getTotal() {
        return total;
    }
}
