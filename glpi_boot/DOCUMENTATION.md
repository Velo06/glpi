# Documentation — Projet GLPI Boot (gestion des coûts de tickets)

API Spring Boot (port **8082**) adossée à une base **SQLite** (`glpi.db`).
Elle gère les **statuts** de tickets et les **coûts** associés aux tickets GLPI
(super coût, coût de réouverture, coût importé de GLPI), avec une vue agrégée
par type d'élément (la « page vaovao »).

Le front (Vite/React) tourne sur `http://localhost:5173` ; le CORS est ouvert
pour cette origine.

---

## 1. Modèle de données

### Table `statut`
| Colonne | Type | Notes |
|---|---|---|
| `id` | INTEGER PK | |
| `nom_francais` | TEXT NOT NULL | libellé FR |
| `nom_malgache` | TEXT | libellé MG (optionnel) |
| `couleur_fond` | TEXT NOT NULL DEFAULT `#FFFFFF` | couleur d'affichage |

### Table `type_cout` (référentiel des types de coût)
| Colonne | Type | Notes |
|---|---|---|
| `id` | INTEGER PK | |
| `libelle` | TEXT | `super_cout` (1), `cout_reouverture` (2), `cout_glpi` (3) |

### Table `ticket_cost` (modèle normalisé : 1 ligne = 1 coût typé)
| Colonne | Type | Notes |
|---|---|---|
| `id` | INTEGER PK | |
| `id_ticket` | INTEGER NOT NULL | ticket GLPI concerné |
| `type_cout` | INTEGER NOT NULL | clé étrangère logique → `type_cout.id` |
| `cout` | NUMERIC NOT NULL | montant |
| `id_item` | INTEGER | élément GLPI concerné (optionnel) |
| `id_item_type` | TEXT | libellé du type d'élément GLPI (`"Computer"`, `"Monitor"`…) |
| `insertion` | TIMESTAMP | horodatage du lot |

**Notion clé — « les dernières lignes » :** il n'y a **pas de `LIMIT 1`**. Les
dernières lignes d'un ticket sont **toutes celles partageant l'`insertion` la
plus récente**. Plusieurs lignes créées en lot (même `insertion`) sont donc
toutes « les dernières ». C'est le rôle de l'endpoint `/batch` : un lot = une
seule valeur d'`insertion`.

---

## 2. Entités (`com.example.glpi.entity`)

### `Statut`
Mappe la table `statut`. Champs : `id`, `nomFrancais`, `nomMalgache`,
`couleurFond` (défaut `#FFFFFF`). Getters/setters standards.

### `TypeCout`
Mappe la table `type_cout`. Champs : `id`, `libelle`. Sert de référentiel pour
valider le `type_cout` envoyé dans `ticket_cost`.

### `TicketCost`
Mappe la table `ticket_cost`. Les clés JSON sont alignées sur les noms de
colonnes via `@JsonProperty` (`id_ticket`, `type_cout`, `id_item`,
`id_item_type`). `insertion` est initialisé à `LocalDateTime.now()` par défaut.

### `CostByItemType` (DTO, pas une entité JPA)
Objet de sortie pour la vue agrégée par type d'élément. Pour un `itemtype`
donné il cumule les trois natures de coût et le total.

| Méthode | Rôle |
|---|---|
| `CostByItemType(String itemtype)` | construit une ligne pour un type d'élément |
| `add(int typeCout, double montant)` | ajoute un montant dans le bon compartiment (`1`→supercost, `2`→reouverture, `3`→cout_glpi) puis recalcule `total` |
| getters | exposent `itemtype`, `cout` (=cout_glpi), `supercost`, `reouverture`, `total` |

---

## 3. Repositories (`com.example.glpi.repository`)

### `StatutRepository`
`JpaRepository<Statut, Integer>` — CRUD standard, aucune requête custom.

### `TypeCoutRepository`
`JpaRepository<TypeCout, Integer>` — CRUD standard. Utilisé surtout via
`existsById` pour valider un `type_cout`.

### `TicketCostRepository` (`JpaRepository<TicketCost, Long>`)

| Méthode | Description |
|---|---|
| `findByIdTicket(Long idTicket)` | toutes les lignes de coût d'un ticket |
| `findByIdTicketAndTypeCout(Long idTicket, Integer typeCout)` | toutes les lignes d'un ticket pour un type de coût donné |
| `findLastByIdTicket(Long idTicket)` | **dernier lot** d'un ticket : toutes les lignes dont `insertion = MAX(insertion)` du ticket (sous-requête, sans `LIMIT`) |
| `findLastByIdTicketAndType(Long idTicket, Integer typeCout)` | dernier lot **pour un type donné** : lignes de ce type avec `insertion = MAX(insertion)` parmi ce type |
| `sumCostByItemType()` | agrégat `SUM(cout)` groupé par (`id_item_type`, `type_cout`), en ignorant les lignes sans `id_item_type` ; renvoie des `Object[]` `[itemtype, typeCout, somme]` que le contrôleur pivote |

---

## 4. Contrôleur `StatutController` — `/api/statut`

| Méthode | Verbe + route | Description |
|---|---|---|
| `getAllStatuts()` | `GET /api/statut` | liste tous les statuts |
| `getStatutById(id)` | `GET /api/statut/{id}` | un statut, 404 si absent |
| `createStatut(payload)` | `POST /api/statut` | crée un statut ; applique `#FFFFFF` si `couleur_fond` absent ; renvoie `201` |
| `updateStatut(id, payload)` | `PUT /api/statut/{id}` | mise à jour partielle (ne touche que les champs non nuls), 404 si absent |
| `deleteStatut(id)` | `DELETE /api/statut/{id}` | supprime, `204`, 404 si absent |

---

## 5. Contrôleur `TicketCostController` — `/api/ticketcost`

### CRUD

| Méthode | Verbe + route | Description |
|---|---|---|
| `create(ticketCost)` | `POST /api/ticketcost` | crée une ligne de coût ; valide `type_cout` ; fixe `insertion` si absente ; `201` |
| `createBatch(lignes)` | `POST /api/ticketcost/batch` | crée **plusieurs lignes avec la MÊME `insertion`** ; valide chaque `type_cout` ; `201`. Indispensable pour clôture/réouverture où un lot doit être identifiable comme « le dernier » |
| `findAll()` | `GET /api/ticketcost` | toutes les lignes |
| `findById(id)` | `GET /api/ticketcost/{id}` | une ligne, 404 si absente |
| `findByTicket(idTicket)` | `GET /api/ticketcost/ticket/{idTicket}` | toutes les lignes d'un ticket |
| `findByTicketAndType(idTicket, typeCout)` | `GET /api/ticketcost/ticket/{idTicket}/type/{typeCout}` | lignes d'un ticket filtrées par type de coût |
| `update(id, payload)` | `PUT /api/ticketcost/{id}` | mise à jour partielle (champs non nuls) ; revalide `type_cout` s'il change ; 404 si absente |
| `delete(id)` | `DELETE /api/ticketcost/{id}` | supprime une ligne, `204`, 404 si absente |

### Fonctions spécifiques (logique métier)

| Méthode | Verbe + route | Description |
|---|---|---|
| `findLastByTicket(idTicket)` | `GET …/ticket/{idTicket}/last` | renvoie le **dernier lot** du ticket (toutes les lignes de l'`insertion` la plus récente), 404 si vide |
| `deleteLastByTicket(idTicket)` | `DELETE …/ticket/{idTicket}/last` | supprime **tout le dernier lot** du ticket (pas une seule ligne), `204`, 404 si vide |
| `deleteByTicketAndType(idTicket, typeCout)` | `DELETE …/ticket/{idTicket}/type/{typeCout}` | supprime **toutes** les lignes d'un type pour le ticket (idempotent, **pas de 404**). Sert à rafraîchir le coût importé GLPI : on remplace l'instantané au lieu de l'accumuler |
| `findLastByTicketAndType(idTicket, typeCout)` | `GET …/ticket/{idTicket}/type/{typeCout}/last` | dernier lot **d'un type donné** (ex. dernier `super_cout` par élément, base du calcul de réouverture), 404 si vide |
| `deleteLastByTicketAndType(idTicket, typeCout)` | `DELETE …/ticket/{idTicket}/type/{typeCout}/last` | supprime le dernier lot **d'un type** (ex. annuler le dernier `super_cout` sans toucher aux `cout_glpi` de la même clôture), `204`, 404 si vide |
| `costByItemType()` | `GET /api/ticketcost/by-itemtype` | **vue agrégée (page vaovao)** : pour chaque `id_item_type`, somme de `super_cout`, `cout_reouverture`, `cout_glpi` + total. Pivote `sumCostByItemType()` via une `Map` ordonnée en liste de `CostByItemType` |

### Helper privé

| Méthode | Rôle |
|---|---|
| `validateTypeCout(Integer typeCout)` | lève `400 BAD_REQUEST` si `type_cout` est nul ou n'existe pas dans `type_cout`. Appelé en création/mise à jour |

---

## 6. Scénarios métier (comment les fonctions s'enchaînent)

- **Clôture d'un ticket** → `POST /batch` avec, dans un seul lot (même
  `insertion`), une ligne `super_cout` et les lignes `cout_glpi` par élément.
  Le coût importé GLPI peut d'abord être rafraîchi via
  `DELETE …/type/{typeCout}` (remplacement de l'instantané).
- **Annulation de la dernière clôture** → `DELETE …/type/{super_cout}/last`
  retire uniquement le dernier `super_cout`, les `cout_glpi` du même lot restent.
- **Réouverture** → on lit le dernier `super_cout` par élément via
  `GET …/type/{super_cout}/last`, on applique un pourcentage, puis on insère les
  lignes `cout_reouverture` en un `POST /batch`.
- **Tableau de bord par type d'élément** → `GET /by-itemtype`.

---

## 7. Configuration

- **`CorsConfig`** : autorise toutes les routes (`/**`) depuis
  `http://localhost:5173`, toutes méthodes et tous en-têtes. Chaque contrôleur
  porte aussi `@CrossOrigin(origins = "http://localhost:5173")`.
- **`application.properties`** : SQLite (`jdbc:sqlite:glpi.db`), dialecte
  `SQLiteDialect`, `ddl-auto=update`, port `8082`, `show-sql=true`.
- **`script.sql`** : création des 3 tables + jeux de données `statut` et
  `type_cout`, et requête de **réinitialisation** de `ticket_cost`
  (`DELETE FROM ticket_cost;`).

> Remarque : `ddl-auto=update` n'efface jamais de colonnes. Tout changement de
> schéma de `ticket_cost` exige de recréer la table manuellement (cf.
> `script.sql`).
