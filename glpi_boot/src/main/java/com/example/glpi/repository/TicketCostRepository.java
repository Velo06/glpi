package com.example.glpi.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.example.glpi.entity.TicketCost;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface TicketCostRepository extends JpaRepository<TicketCost, Long> {

    List<TicketCost> findByIdTicketAndAnnuleFalse(Long idTicket);

    // typeCout est désormais une relation : on filtre sur son id (type_cout.id).
    List<TicketCost> findByIdTicketAndTypeCout_IdAndAnnuleFalse(Long idTicket, Integer typeCoutId);

    // Dernières lignes d'un ticket = toutes celles dont l'insertion est la plus
    // récente. Plusieurs lignes peuvent partager la même insertion : elles sont
    // alors toutes "les dernières" (pas de LIMIT).
    @Query("SELECT tc FROM TicketCost tc " +
           "WHERE tc.idTicket = :id " +
           "AND tc.insertion = (SELECT MAX(tc2.insertion) FROM TicketCost tc2 " +
           "                    WHERE tc2.idTicket = :id AND tc2.annule = false) AND tc.annule = false")
    List<TicketCost> findLastByIdTicket(@Param("id") Long idTicket);

    // Dernier LOT d'un type de coût donné pour un ticket : toutes les lignes de
    // ce type partageant l'insertion la plus récente PARMI CE TYPE. Utilisé pour
    // l'annulation (supprimer le dernier super_cout) et la réouverture (calculer
    // le pourcentage sur le dernier super_cout, par élément).
    @Query("SELECT tc FROM TicketCost tc " +
           "WHERE tc.idTicket = :id AND tc.typeCout.id = :type " +
           "AND tc.insertion = (SELECT MAX(tc2.insertion) FROM TicketCost tc2 " +
           "                    WHERE tc2.idTicket = :id AND tc2.typeCout.id = :type AND tc2.annule = false) AND tc.annule = false")
    List<TicketCost> findLastByIdTicketAndType(@Param("id") Long idTicket,
                                               @Param("type") Integer typeCout);

    @Query("SELECT tc FROM TicketCost tc " +
           "WHERE tc.idTicket = :id AND tc.typeCout.id = :type " +
           "AND tc.insertion = (SELECT MIN(tc2.insertion) FROM TicketCost tc2 " +
           "                    WHERE tc2.idTicket = :id AND tc2.typeCout.id = :type AND tc2.annule = false) AND tc.annule = false")
    List<TicketCost> findFirstByIdTicketAndType(@Param("id") Long idTicket,
                                               @Param("type") Integer typeCout);


    // Somme des coûts regroupés par type d'élément et par type de coût.
    // Ligne = [ id_item_type, type_cout, SUM(cout) ]. Le controller pivote
    // ce résultat en une ligne par type d'élément (page vaovao).
    @Query("SELECT tc.idItemType, tc.typeCout.id, SUM(tc.cout) FROM TicketCost tc " +
           "WHERE tc.idItemType IS NOT NULL AND tc.annule = false " +
           "GROUP BY tc.idItemType, tc.typeCout.id")
    List<Object[]> sumCostByItemType();

    @Query("SELECT tc.idItemType, tc.typeCout.id, SUM(tc.cout) FROM TicketCost tc " +
           "WHERE tc.idItemType IS NOT NULL AND tc.annule = false " +
           "GROUP BY tc.idItemType, tc.typeCout.id")
    List<Object[]> sumCostSpecByItemType();

    @Query("SELECT tc.idItemType, tc.typeCout.id, AVG(tc.cout) FROM TicketCost tc " +
           "WHERE tc.idItemType IS NOT NULL AND tc.annule = false " +
           "GROUP BY tc.idItemType, tc.typeCout.id")
    List<Object[]> avgCostByItemType();

    @Query("SELECT tc FROM TicketCost tc WHERE tc.idItemType = :item_type AND tc.annule = false")
    List<TicketCost> getDetailCostByItemType(@Param("item_type") String item_type);

    @Query("SELECT SUM(tc.cout) FROM TicketCost tc WHERE tc.idTicket = :id_ticket AND tc.typeCout.id = :idTypeCout AND tc.annule = false")
    Double sumCostByTicket(@Param("id_ticket") Long idticket, @Param("idTypeCout") Long idTypeCout);

    @Query("SELECT tc.id, SUM(tc.cout), tc.insertion FROM TicketCost tc WHERE tc.typeCout.id = :idTypeCout AND tc.annule = false GROUP BY tc.insertion")
    List<Object[]> getCoutByTypeCout(@Param("idTypeCout") Long idTypeCout);

    // ── Recalcul / liste de la nouvelle page ──────────────────────────────────

    // Lignes d'un type de coût, jusqu'à un horodatage inclus. Pour une
    // réouverture, la base de calcul = les super costs (type 1) d'insertion <=
    // celle de la réouverture (les super costs qui EXISTAIENT à ce moment).
    List<TicketCost> findByIdTicketAndTypeCout_IdAndAnnuleFalseAndInsertionLessThanEqual(
            Long idTicket, Integer typeCoutId, LocalDateTime insertion);

    // Lignes d'un lot précis (toutes celles partageant l'insertion, pour un
    // type donné) : sert à retrouver le lot d'un super cost (édition) ou d'une
    // réouverture (remplacement lors du recalcul).
    List<TicketCost> findByIdTicketAndTypeCout_IdAndInsertionAndAnnuleFalse(
            Long idTicket, Integer typeCoutId, LocalDateTime insertion);

    // Liste des super costs (page « liste des coûts ») : un lot par
    // (ticket, insertion), avec un id de ligne représentatif (poignée d'édition)
    // et le montant total du lot.
    @Query("SELECT MIN(tc.id), tc.idTicket, SUM(tc.cout) FROM TicketCost tc " +
           "WHERE tc.typeCout.id = :type AND tc.annule = false " +
           "GROUP BY tc.idTicket, tc.insertion " +
           "ORDER BY MIN(tc.id)")
    List<Object[]> listBatchesByType(@Param("type") Integer typeCout);

    // Somme des lignes cout_reouverture par lot (ticket, insertion). Sert à
    // afficher le total calculé en regard du pourcentage d'origine.
    @Query("SELECT tc.idTicket, tc.insertion, SUM(tc.cout) FROM TicketCost tc " +
           "WHERE tc.typeCout.id = :type AND tc.annule = false " +
           "GROUP BY tc.idTicket, tc.insertion")
    List<Object[]> sumReouvertureByBatch(@Param("type") Integer typeCout);
}
