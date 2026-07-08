-- sqlite3 glpi.db

CREATE TABLE statut (
    id INTEGER PRIMARY KEY,
    nom_francais TEXT NOT NULL,
    nom_malgache TEXT,
    couleur_fond TEXT NOT NULL DEFAULT '#FFFFFF'
);

INSERT INTO statut (id, nom_francais, couleur_fond) VALUES
(1, 'Nouveau', '#2cf0c5ff'),
(3, 'En cours', '#eeb02bff'),
(6, 'Clos', '#1de448ff');

CREATE TABLE type_cout (
    id INTEGER PRIMARY KEY,
    libelle TEXT -- super_cout, cout_reouverture, cout_glpi
);

INSERT INTO type_cout (id, libelle) VALUES
(1, 'super_cout'),
(2, 'cout_reouverture'),
(3, 'cout_glpi');

CREATE TABLE ticket_cost (
    id INTEGER PRIMARY KEY,
    id_ticket INTEGER NOT NULL,
    type_cout INTEGER NOT NULL, -- cle etrangere vers type_cout(id)
    cout NUMERIC NOT NULL,
    id_item INTEGER,
    id_item_type TEXT, -- libelle du type d'element GLPI ("Computer", "Monitor"...)
    -- si plusieurs lignes partagent la meme insertion pour un ticket,
    -- alors ce sont toutes les dernieres lignes (pas de LIMIT)
    insertion TIMESTAMP
);

-- Metadonnees d'une reouverture (un lot cout_reouverture).
-- ticket_cost ne stocke que la VALEUR resolue (cout = base * pourcentage/100).
-- Pour afficher le pourcentage D'ORIGINE et recalculer une reouverture quand un
-- super cost anterieur change, on persiste ici les ENTREES du calcul
-- (pourcentage + mode). Le lien avec les lignes cout_reouverture se fait par
-- (id_ticket, insertion) : toutes les lignes d'un lot partagent cette insertion.
-- NB : la regle "pas de colonne mode dans ticket_cost" reste respectee — le mode
-- vit ici, dans une table dediee, pas dans ticket_cost.
CREATE TABLE reouverture_event (
    id INTEGER PRIMARY KEY,
    id_ticket INTEGER NOT NULL,
    insertion TIMESTAMP NOT NULL, -- = insertion du lot cout_reouverture
    pourcentage NUMERIC NOT NULL, -- pourcentage saisi (donnee d'origine)
    mode INTEGER NOT NULL          -- 1=dernier, 2=premier, 3=moyenne, 4=somme
);

-- Correspondance ref_ticket (CSV du 1er import) -> id GLPI reellement attribue.
-- Ecrite au 1er import, relue par l'import des mouvements (cancel/open/close)
-- qui ne connait que la reference, pas l'id GLPI.
CREATE TABLE ticket_ref (
    id INTEGER PRIMARY KEY,
    ref TEXT NOT NULL,            -- reference telle qu'ecrite dans le CSV
    id_ticket INTEGER NOT NULL    -- id GLPI du ticket cree pour cette reference
);

-- Reinitialisation des donnees de ticket_cost :
-- vide la table et remet le compteur d'id a zero (prochain id = 1).
DELETE FROM ticket_cost;
DELETE FROM ticket_ref;
DELETE FROM reouverture_event;
-- DELETE FROM sqlite_sequence WHERE name = 'ticket_cost';