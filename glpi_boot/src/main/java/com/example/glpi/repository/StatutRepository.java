package com.example.glpi.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.example.glpi.entity.Statut;

public interface StatutRepository extends JpaRepository<Statut, Integer> {
}
