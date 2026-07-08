package com.example.glpi.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "statut")
public class Statut {

    @Id
    private Integer id;

    @Column(name = "nom_francais", nullable = false)
    private String nomFrancais;

    @Column(name = "nom_malgache")
    private String nomMalgache;

    @Column(name = "couleur_fond", nullable = false, columnDefinition = "TEXT DEFAULT '#FFFFFF'")
    private String couleurFond = "#FFFFFF";

    public Statut() {
    }

    public Statut(Integer id, String nomFrancais, String nomMalgache, String couleurFond) {
        this.id = id;
        this.nomFrancais = nomFrancais;
        this.nomMalgache = nomMalgache;
        this.couleurFond = couleurFond;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getNomFrancais() {
        return nomFrancais;
    }

    public void setNomFrancais(String nomFrancais) {
        this.nomFrancais = nomFrancais;
    }

    public String getNomMalgache() {
        return nomMalgache;
    }

    public void setNomMalgache(String nomMalgache) {
        this.nomMalgache = nomMalgache;
    }

    public String getCouleurFond() {
        return couleurFond;
    }

    public void setCouleurFond(String couleurFond) {
        this.couleurFond = couleurFond;
    }
}