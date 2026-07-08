package com.example.glpi.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.example.glpi.entity.ReouvertureEvent;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ReouvertureEventRepository extends JpaRepository<ReouvertureEvent, Long> {

    // Toutes les réouvertures d'un ticket, dans l'ordre chronologique.
    List<ReouvertureEvent> findByIdTicketOrderByInsertionAsc(Long idTicket);

    // Réouvertures POSTÉRIEURES à un super cost donné : ce sont elles (et elles
    // seules) dont la base de calcul peut inclure ce super cost. Utilisé pour le
    // recalcul après modification d'un super cost.
    List<ReouvertureEvent> findByIdTicketAndInsertionGreaterThan(Long idTicket, LocalDateTime insertion);

    // Réouverture liée à un lot cout_reouverture précis (id_ticket + insertion).
    Optional<ReouvertureEvent> findByIdTicketAndInsertion(Long idTicket, LocalDateTime insertion);
}
