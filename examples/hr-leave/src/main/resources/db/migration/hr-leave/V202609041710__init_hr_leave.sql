-- Données métier du cas d'usage hr-leave.
-- Convention : la version d'une migration est son horodatage de création (AAAAMMJJHHMM).

CREATE TABLE salarie (
    id            BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nom           TEXT          NOT NULL,
    date_embauche DATE          NOT NULL,
    type_contrat  TEXT          NOT NULL CHECK (type_contrat IN ('CDI', 'CDD')),
    statut        TEXT          NOT NULL CHECK (statut IN ('CADRE', 'NON_CADRE')),
    -- Temps de travail en pourcentage d'un temps plein : 100 = temps plein, 80 = temps partiel à 80 %.
    temps_travail NUMERIC(5, 2) NOT NULL DEFAULT 100 CHECK (temps_travail > 0 AND temps_travail <= 100),
    -- Solde de congés en jours (les demi-journées sont possibles).
    solde_conges  NUMERIC(5, 2) NOT NULL DEFAULT 0,
    -- Convention collective applicable.
    convention    TEXT
);
