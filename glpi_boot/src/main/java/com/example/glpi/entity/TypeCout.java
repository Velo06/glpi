package com.example.glpi.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "type_cout")
public class TypeCout {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    // Ex : super_cout, cout_reouverture, cout_glpi
    @Column(name = "libelle")
    private String libelle;

    public TypeCout() {
    }

    public TypeCout(Integer id, String libelle) {
        this.id = id;
        this.libelle = libelle;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getLibelle() {
        return libelle;
    }

    public void setLibelle(String libelle) {
        this.libelle = libelle;
    }
}
