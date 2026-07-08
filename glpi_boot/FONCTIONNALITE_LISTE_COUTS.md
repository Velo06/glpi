# Fonctionnalité — Liste des coûts (réouvertures & super costs) + édition

Nouvelle page (`glpi_react/src/BO/ticket/NewPage.jsx`, route `/bo/list-cout`) qui
liste **toutes les réouvertures** et **tous les super costs** enregistrés, avec
pour chacun une action **Modifier**.

Colonnes :

| Tableau       | Colonnes                                                        |
|---------------|-----------------------------------------------------------------|
| Réouvertures  | Réf. ticket · **Pourcentage d'origine** · Mode · Coût calculé · Modifier |
| Super costs   | Réf. ticket · Coût · Modifier                                    |

Les deux exigences délicates de la demande :

1. **Pour les réouvertures, afficher le pourcentage d'ORIGINE** (ex. `10 %`), pas
   la valeur déjà calculée (ex. `5,00`).
2. **Modifier un super cost recalcule les réouvertures qui le suivent** dans le
   temps.

Ce document explique la **solution retenue** pour ces deux points.

---

## 1. Le problème de fond

La table `ticket_cost` ne stocke qu'une **valeur résolue** :

```
cout_reouverture = base × (pourcentage / 100)
```

où `base` dépend du **mode de calcul** (dernier / premier / moyenne / somme des
super costs — cf. `MODE_CALCUL_REOUVERTURE.md`). Conséquences :

- Le **pourcentage n'est stocké nulle part** : seul le produit l'est.
- Le **mode non plus** (ancienne « règle absolue » : pas de colonne `mode` dans
  `ticket_cost`).

On **ne peut donc pas** retrouver le pourcentage de façon fiable à partir du seul
`cout` : il faudrait connaître la base, donc le mode, et plusieurs couples
`(mode, %)` donnent la même valeur. Exemple : super costs `100` puis `50`,

- mode 4 (somme) à `10 %` → `(100+50) × 0,10 = 15`
- mode 1 (dernier) à `30 %` → `50 × 0,30 = 15`

→ même `cout = 15`, impossible de deviner lequel. **La dérivation inverse est
ambiguë.**

---

## 2. Solution retenue — persister les ENTRÉES du calcul

> **Décision** : on persiste le **pourcentage** et le **mode** de chaque
> réouverture dans une **table dédiée `reouverture_event`**, et **non** dans
> `ticket_cost`. La règle « pas de colonne `mode` dans `ticket_cost` » reste
> respectée : `ticket_cost.cout` demeure la valeur résolue ; le mode vit
> ailleurs.

### Schéma

```sql
CREATE TABLE reouverture_event (
    id INTEGER PRIMARY KEY,
    id_ticket INTEGER NOT NULL,
    insertion TIMESTAMP NOT NULL,  -- = insertion du lot cout_reouverture
    pourcentage NUMERIC NOT NULL,  -- pourcentage SAISI (donnée d'origine)
    mode INTEGER NOT NULL          -- 1=dernier, 2=premier, 3=moyenne, 4=somme
);
```

### Le lien event ↔ lignes de coût : `insertion`

Une réouverture = **un lot** de lignes `cout_reouverture` (une par élément du
ticket) qui partagent toutes la **même `insertion`** (déjà le cas aujourd'hui).
Le `reouverture_event` porte **cette même `insertion`**. Le couple
`(id_ticket, insertion)` relie donc l'événement (pct + mode) à ses lignes
calculées — **sans clé étrangère ni jointure lourde**.

`insertion` joue un double rôle :

- **identité du lot** (regrouper les lignes d'une même réouverture) ;
- **position sur la timeline** du ticket : la base d'une réouverture = les super
  costs d'`insertion` **≤** celle de la réouverture (ceux qui existaient à ce
  moment-là).

### Affichage du pourcentage d'origine

Trivial une fois les entrées persistées : la page lit `pourcentage` directement
depuis `reouverture_event` (endpoint `GET /api/ticketcost/reouvertures`).
**Aucune dérivation inverse**, donc **aucune ambiguïté**. Le `total` calculé est
affiché à côté, à titre informatif (somme des lignes du lot).

---

## 3. Calcul centralisé (création) — fini la duplication

Avant, le calcul des 4 modes était dupliqué côté front
(`foTicketService.insertCoutReouverture`) **et** côté import, avec un bug connu
(**mode 3 == mode 4**). Désormais **un seul** point de vérité : le **backend**.

```
POST /api/ticketcost/reouverture
body : { id_ticket, pourcentage, mode }
```

Le backend (`TicketCostController.createReouverture`) :

1. lit les super costs **actuels** du ticket (`insertion ≤ now`) ;
2. calcule, **par élément**, la base selon le mode, puis
   `cout = base × pourcentage/100` (`computeReouvertureLines`) ;
3. insère le lot `cout_reouverture` (une ligne par élément, `insertion = now`) ;
4. enregistre le `reouverture_event` avec **la même `insertion`**.

Le front (`insertCoutReouverture`) n'est plus qu'un appel à cet endpoint — donc
le **Kanban** comme l'**import CSV** profitent du même calcul correct.

### Détail des 4 modes (base par élément)

| Mode | Base par élément                                             |
|------|--------------------------------------------------------------|
| 1 (dernier) | coût de l'élément dans le **lot le plus récent** (`MAX(insertion)`) |
| 2 (premier) | coût de l'élément dans le **lot le plus ancien** (`MIN(insertion)`) |
| 3 (moyenne) | `Σ(coûts de l'élément) / nb_clôtures`                 |
| 4 (somme)   | `Σ(coûts de l'élément)`                               |

`nb_clôtures` = nombre de lots super cost distincts (`insertion` distinctes).
Exemple `alea.txt` (super costs `100` puis `50`, un seul élément, `10 %`) :
mode 1 → `5` · mode 2 → `10` · mode 3 → `7,5` · mode 4 → `15`. ✔

---

## 4. Recalcul après modification d'un super cost — le point clé

> **« Modifier un super cost a un impact sur le calcul des réouvertures APRÈS ce
> super cost. »**

### Quelles réouvertures sont impactées ?

La base d'une réouverture = super costs d'`insertion ≤ insertion_réouverture`.
Modifier un super cost **S** (sans changer sa date) ne change que sa **valeur**.
Sont donc impactées **exactement** les réouvertures dont
`insertion > insertion(S)` : ce sont les seules dont la base peut contenir **S**.
Les réouvertures antérieures à **S** ne le « voient » pas → inchangées.

```
Timeline ticket  ─────────────────────────────────────────────▶ temps
   S1=100        R1(10%, mode1)     S2=50      R2(10%, mode4)
   t1            t2                 t3         t4

Éditer S1 (t1)  → recalcul de R1 (t2>t1) ET R2 (t4>t1)
Éditer S2 (t3)  → recalcul de R2 (t4>t3) seulement (R1 est avant t3)
```

### Comment ?

```
PUT /api/ticketcost/supercost/{lineId}
body : { montant }
```

`TicketCostController.updateSupercost` (transactionnel) :

1. retrouve le lot de **S** via une ligne (`lineId`) → `id_ticket`, `insertion` ;
2. **redistribue** le nouveau montant à parts égales sur les éléments du lot
   (même règle qu'à la création : `montant / nb_éléments`) ;
3. pour **chaque** `reouverture_event` du ticket avec `insertion > insertion(S)`,
   appelle `recomputeReouverture`.

`recomputeReouverture(event)` :

1. relit la base = super costs `insertion ≤ event.insertion` (avec leurs valeurs
   **à jour**) ;
2. **supprime** l'ancien lot `cout_reouverture` de cet événement
   (`id_ticket, event.insertion`) ;
3. **réinsère** le lot recalculé via `computeReouvertureLines`, en **conservant
   la même `insertion`** (donc le lien event ↔ lot reste valide).

Le pourcentage et le mode de l'événement **ne changent pas** : on rejoue
strictement le **même calcul** sur une **nouvelle base**. C'est ce qui garantit
la cohérence : `cout_reouverture` reste, à tout instant,
`base_courante × %_origine / 100`.

### Édition d'une réouverture elle-même

```
PUT /api/ticketcost/reouverture/{eventId}
body : { pourcentage, mode }
```

Met à jour l'événement puis appelle `recomputeReouverture` : ses lignes sont
recalculées à partir de **sa** base (super costs `≤ son insertion`). Sa position
sur la timeline ne bouge pas, donc **aucune** autre réouverture n'est touchée.

### Pourquoi « recalcul + réécriture » et pas « calcul à l'affichage » ?

La page existante « coûts par type d'élément » (`PageVaovao`) agrège les
`cout_reouverture` par `SUM` SQL. En **réécrivant** la valeur résolue à chaque
modification, **toutes** les agrégations existantes restent justes sans
modification. Un calcul purement « à la volée » aurait imposé de réécrire chaque
agrégation — plus risqué.

---

## 5. Fichiers

### Backend (`glpi_boot/`)
| Fichier | Rôle |
|---|---|
| `entity/ReouvertureEvent.java` | **Nouveau** — table `reouverture_event` (pct + mode). |
| `repository/ReouvertureEventRepository.java` | **Nouveau** — lookups par ticket / insertion. |
| `dto/ReouvertureListDTO.java`, `dto/SupercostListDTO.java` | **Nouveaux** — formes des listes. |
| `repository/TicketCostRepository.java` | + requêtes bornées par `insertion`, listes par lot. |
| `controllers/TicketCostController.java` | + endpoints liste / création / éditions + `computeReouvertureLines` + `recomputeReouverture`. |
| `script.sql` | + table `reouverture_event` (et purge à la réinitialisation). |

### Frontend (`glpi_react/src/`)
| Fichier | Rôle |
|---|---|
| `BO/ticket/NewPage.jsx` (+ `NewPage.css`) | La page : 2 tableaux + modales d'édition. |
| `BO/ticket/ticketService.js` | `getListReouverture/Supercost`, `editReouverture`, `editSupercost`. |
| `api/supercostApi.js` | `getReouvertureList`, `getSupercostList`, `createReouverture`, `updateReouverture`, `updateSupercost`. |
| `FO/ticket/foTicketService.js` | `insertCoutReouverture` → simple appel `POST /reouverture` (calcul centralisé). |

### Endpoints
| Méthode & URL | Rôle |
|---|---|
| `GET  /api/ticketcost/reouvertures` | Liste réouvertures `{ id, id_ticket, pourcentage, mode, total }`. |
| `GET  /api/ticketcost/supercosts` | Liste super costs `{ lineId, id_ticket, total }`. |
| `POST /api/ticketcost/reouverture` | Crée une réouverture (calcul + event). |
| `PUT  /api/ticketcost/reouverture/{eventId}` | Édite pct/mode + recalcule ses lignes. |
| `PUT  /api/ticketcost/supercost/{lineId}` | Édite le montant + recalcule les réouvertures postérieures. |

---

## 6. Recette / tests

Préparer un ticket avec **deux** clôtures (`S1 = 100`, `S2 = 50`) puis une
réouverture R après S2.

- [ ] **% d'origine** : la liste affiche `10` (pourcentage saisi), pas `15,00` ;
      le mode reste visible/éditable dans la modale.
- [ ] **Création (modes)** : R à `10 %` donne mode 1 → 5 · 2 → 10 · 3 → 7,5 · 4 → 15.
- [ ] **Édition réouverture** : passer R de `10 %` à `20 %` (mode 4) → `cout` = 30 ;
      les super costs et les autres réouvertures sont inchangés.
- [ ] **Édition super cost (impact aval)** : porter `S1` de `100` à `200`.
      R (mode 4, 10 %) passe de `15` → `(200+50) × 0,10 = 25`. Une réouverture
      **antérieure** à S1 (s'il y en a) reste inchangée.
- [ ] **Cohérence** : la page « coûts par type d'élément » reflète les nouveaux
      totaux (réécriture en base).
- [ ] **Réinitialisation** : `DELETE /api/ticketcost` vide aussi
      `reouverture_event` (pas d'événement orphelin).
- [ ] `sqlite3 glpi.db ".schema ticket_cost"` ne contient **toujours pas** `mode`.

---

## 7. Explication des fonctions créées (back & front)

Cette section décrit **chaque fonction** ajoutée ou modifiée, son rôle, ses
paramètres, ce qu'elle renvoie et sa logique.

### 7.1 Backend — Entité `ReouvertureEvent.java`

Simple objet JPA mappé sur la table `reouverture_event`. Champs :
`id`, `idTicket`, `insertion` (`LocalDateTime`), `pourcentage` (`Double`),
`mode` (`Integer`). Un constructeur pratique
`ReouvertureEvent(idTicket, insertion, pourcentage, mode)` + getters/setters.
Les `@JsonProperty("id_ticket")`, `@JsonProperty("pourcentage")`, etc. alignent
les clés JSON sur ce que lit/écrit le front.

### 7.2 Backend — `ReouvertureEventRepository.java`

Trois requêtes **dérivées** (Spring génère le SQL d'après le nom) :

| Méthode | SQL équivalent | Usage |
|---|---|---|
| `findByIdTicketOrderByInsertionAsc(idTicket)` | `… WHERE id_ticket=? ORDER BY insertion` | lister les réouvertures d'un ticket dans l'ordre. |
| `findByIdTicketAndInsertionGreaterThan(idTicket, insertion)` | `… WHERE id_ticket=? AND insertion>?` | retrouver les réouvertures **postérieures** à un super cost (celles à recalculer). |
| `findByIdTicketAndInsertion(idTicket, insertion)` | `… WHERE id_ticket=? AND insertion=?` | retrouver l'événement d'un lot précis. |

### 7.3 Backend — nouvelles requêtes de `TicketCostRepository.java`

```java
// Lignes d'un type, jusqu'à un horodatage inclus → BASE d'une réouverture
// (super costs qui existaient à l'instant de la réouverture).
List<TicketCost> findByIdTicketAndTypeCout_IdAndInsertionLessThanEqual(
        Long idTicket, Integer typeCoutId, LocalDateTime insertion);

// Toutes les lignes d'un LOT précis (même insertion) pour un type donné →
// sert à retrouver un lot super cost (édition) ou cout_reouverture (remplacement).
List<TicketCost> findByIdTicketAndTypeCout_IdAndInsertion(
        Long idTicket, Integer typeCoutId, LocalDateTime insertion);

// Liste des super costs : un lot par (ticket, insertion), avec un id de ligne
// représentatif (poignée d'édition) et le montant total du lot.
@Query("SELECT MIN(tc.id), tc.idTicket, SUM(tc.cout) FROM TicketCost tc " +
       "WHERE tc.typeCout.id = :type GROUP BY tc.idTicket, tc.insertion ORDER BY MIN(tc.id)")
List<Object[]> listBatchesByType(Integer typeCout);

// Total calculé d'une réouverture (somme des lignes du lot), pour l'affichage.
@Query("SELECT tc.idTicket, tc.insertion, SUM(tc.cout) FROM TicketCost tc " +
       "WHERE tc.typeCout.id = :type GROUP BY tc.idTicket, tc.insertion")
List<Object[]> sumReouvertureByBatch(Integer typeCout);
```

### 7.4 Backend — endpoints & logique de `TicketCostController.java`

**`listReouvertures()` — `GET /reouvertures`**
Construit la liste affichée par la page. (1) lit `sumReouvertureByBatch(2)` et
indexe les totaux par clé `idTicket@insertion` ; (2) parcourt tous les
`reouverture_event` et, pour chacun, retrouve son total ; renvoie des
`ReouvertureListDTO { id, id_ticket, pourcentage, mode, total }`. Le
**pourcentage vient de l'événement** (donnée d'origine), jamais d'un calcul
inverse.

**`listSupercosts()` — `GET /supercosts`**
Parcourt `listBatchesByType(1)` et renvoie un `SupercostListDTO { lineId,
id_ticket, total }` par lot. `lineId = MIN(id)` du lot sert de **poignée
d'édition** (pas besoin de transporter l'horodatage).

**`createReouverture(body)` — `POST /reouverture`** *(transactionnel)*
Crée une réouverture de bout en bout : (1) valide `id_ticket`, `pourcentage`,
`mode∈[1..4]` ; (2) lit les super costs actuels (`insertion ≤ now`) ;
(3) `computeReouvertureLines(...)` produit les lignes par élément ; (4) les
insère (`insertion = now`) ; (5) enregistre le `ReouvertureEvent` avec **la même
`insertion`**. Renvoie l'événement créé.

**`updateReouverture(eventId, body)` — `PUT /reouverture/{eventId}`** *(transactionnel)*
Édite une réouverture : applique le nouveau `pourcentage` et/ou `mode` à
l'événement, le sauve, puis appelle `recomputeReouverture(ev)`. Aucune autre
réouverture n'est touchée (l'`insertion` ne change pas).

**`updateSupercost(lineId, body)` — `PUT /supercost/{lineId}`** *(transactionnel)*
Cœur du « recalcul aval ». (1) charge la ligne `lineId` → `id_ticket` +
`insertion` du lot (et vérifie que c'est bien un super cost) ; (2) **redistribue**
le nouveau `montant` à parts égales sur les lignes du lot
(`montant / nb_éléments`) ; (3) pour chaque `ReouvertureEvent` du ticket avec
`insertion > insertion(S)`, appelle `recomputeReouverture`. Renvoie le lot mis à
jour.

**`recomputeReouverture(ev)` — privé**
Recalcule **une** réouverture sans changer ses entrées :
```java
private void recomputeReouverture(ReouvertureEvent ev) {
    // 1. Base = super costs <= insertion de la réouverture (valeurs À JOUR).
    List<TicketCost> supers = repository.findByIdTicketAndTypeCout_IdAndInsertionLessThanEqual(
            ev.getIdTicket(), TYPE_SUPER_COUT, ev.getInsertion());
    // 2. Supprime l'ancien lot cout_reouverture de cette réouverture.
    List<TicketCost> anciennes = repository.findByIdTicketAndTypeCout_IdAndInsertion(
            ev.getIdTicket(), TYPE_REOUVERTURE, ev.getInsertion());
    if (!anciennes.isEmpty()) repository.deleteAll(anciennes);
    // 3. Réinsère le lot recalculé, MÊME insertion (lien event ↔ lot préservé).
    List<TicketCost> nouvelles = computeReouvertureLines(
            supers, ev.getMode(), ev.getPourcentage(), ev.getIdTicket(), ev.getInsertion());
    if (!nouvelles.isEmpty()) repository.saveAll(nouvelles);
}
```

**`computeReouvertureLines(supers, mode, pourcentage, idTicket, insertion)` — privé**
Seul endroit qui implémente les 4 modes (création **et** recalcul). Logique :
- `f = pourcentage / 100` ;
- pour les modes 1 (dernier) / 2 (premier), détermine le lot cible
  `cible = MAX/MIN(insertion)` ;
- `nbCloture = nb d'insertions distinctes` (pour le mode 3) ;
- regroupe les super costs **par élément** (`elementKey = idItemType#idItem`) ;
- pour chaque élément, calcule la **base** selon le mode (dernier/premier =
  coût dans le lot cible ; moyenne = `Σ/nbCloture` ; somme = `Σ`), puis crée une
  ligne `cout_reouverture = base × f` (même `id_item`/`id_item_type`, même
  `insertion`). Renvoie la liste des lignes (vide si aucun super cost).

**Helpers privés**
`elementKey(tc)` → `idItemType + "#" + idItem` (clé de regroupement par élément) ;
`batchKey(idTicket, insertion)` → clé d'index des totaux ;
`nz(Double)` → 0 si null ;
`asLong/asInt/asDouble(Object)` → conversions sûres des valeurs du `body`
JSON (`asDouble` accepte aussi la virgule décimale).

**`deleteAll()` — `DELETE /api/ticketcost`** *(modifié)*
Vide `ticket_cost` **et** `reouverture_event` pour ne pas laisser d'événements
orphelins après une réinitialisation.

### 7.5 Backend — DTOs

`ReouvertureListDTO { id, id_ticket, pourcentage, mode, total }` et
`SupercostListDTO { lineId, id_ticket, total }` : simples objets de transport
(constructeur + getters, clés JSON via `@JsonProperty`) renvoyés par les
endpoints liste.

### 7.6 Frontend — `api/supercostApi.js`

| Fonction | Appel HTTP | Renvoie |
|---|---|---|
| `getReouvertureList()` | `GET /reouvertures` | `[{ id, id_ticket, pourcentage, mode, total }]` (tableau, `[]` si erreur). |
| `getSupercostList()` | `GET /supercosts` | `[{ lineId, id_ticket, total }]`. |
| `createReouverture({ id_ticket, pourcentage, mode })` | `POST /reouverture` | l'événement créé. |
| `updateReouverture(eventId, { pourcentage, mode })` | `PUT /reouverture/{eventId}` | l'événement mis à jour. |
| `updateSupercost(lineId, montant)` | `PUT /supercost/{lineId}` body `{ montant }` | le lot mis à jour. |

Les deux `get…` avalent l'erreur et renvoient `[]` (la page reste affichable) ;
les trois mutations laissent remonter l'erreur (gérée par l'appelant).

### 7.7 Frontend — `FO/ticket/foTicketService.js`

**`insertCoutReouverture(ticketId, pourcentage, mod)`** *(réécrite)*
N'embarque plus les 4 modes : délègue au backend.
```js
export async function insertCoutReouverture(ticketId, pourcentage, mod) {
  await createReouverture({
    id_ticket: ticketId,
    pourcentage: Number(pourcentage),
    mode: Number(mod),
  });
}
```
Comme le Kanban et l'import CSV passent tous deux par cette fonction, ils
bénéficient du **calcul unique et correct** (fin du bug « mode 3 == mode 4 »).

### 7.8 Frontend — `BO/ticket/ticketService.js`

| Fonction | Rôle |
|---|---|
| `getListReouverture()` | proxy vers `getReouvertureList()` (API). |
| `getListSupercost()` | proxy vers `getSupercostList()` (API). |
| `editReouverture(eventId, pourcentage, mode)` | convertit en nombres et appelle `updateReouverture`. |
| `editSupercost(lineId, montant)` | convertit en nombre et appelle `updateSupercost`. |

Couche fine qui isole la page des détails de l'API.

### 7.9 Frontend — `BO/ticket/NewPage.jsx`

| Fonction | Rôle |
|---|---|
| `formatCost(n)` | format monétaire FR (2 décimales). |
| `loadData()` | charge **en parallèle** réouvertures + super costs (`Promise.all`), gère `loading`/`error`. Appelée au montage et après chaque édition. |
| `openReouverture(row)` | pré-remplit le formulaire (`pourcentage`, `mode` de la ligne) et ouvre la modale en mode `"reouv"`. |
| `openSupercost(row)` | pré-remplit `montant` (= total du lot) et ouvre la modale en mode `"super"`. |
| `closeModal()` | ferme la modale (bloquée pendant un enregistrement). |
| `handleSave()` | selon le type, appelle `editReouverture` ou `editSupercost`, ferme la modale puis **`loadData()`** — indispensable car éditer un super cost recalcule les réouvertures, donc les **deux** tableaux changent. |

Le rendu : deux tableaux (réouvertures, super costs) + une modale d'édition
unique pilotée par l'état `editing`.
