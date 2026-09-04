-- Étape 1 : ingestion documentaire.

-- Un document ingéré est enregistré avec l'empreinte SHA-256 de son fichier : relancer l'ingestion sur un fichier
-- inchangé ne fait rien ; un fichier modifié remplace l'ensemble de ses morceaux (suppression en cascade).
CREATE TABLE document (
    nom       TEXT        PRIMARY KEY,
    empreinte TEXT        NOT NULL,
    nb_pages  INTEGER     NOT NULL,
    ingere_le TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- La provenance d'un morceau (document, chapitre, page) est obligatoire : c'est elle qui permet de citer la source.
ALTER TABLE chunk
    ALTER COLUMN chapitre SET NOT NULL,
    ALTER COLUMN page     SET NOT NULL,
    ADD CONSTRAINT chunk_document_fk FOREIGN KEY (document) REFERENCES document (nom) ON DELETE CASCADE;

CREATE INDEX chunk_document_idx ON chunk (document);
