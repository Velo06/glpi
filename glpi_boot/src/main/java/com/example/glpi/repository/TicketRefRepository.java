package com.example.glpi.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.example.glpi.entity.TicketRef;
import java.util.List;

public interface TicketRefRepository extends JpaRepository<TicketRef, Long> {

    // Toutes les correspondances pour une référence donnée (normalement une
    // seule, mais on renvoie une liste pour rester tolérant aux doublons).
    List<TicketRef> findByRef(String ref);
}
