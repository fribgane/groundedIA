-- Socle documentaire de groundedIA.
-- Convention : la version d'une migration est son horodatage de création (AAAAMMJJHHMM).
-- L'ordre chronologique est ainsi global à tous les modules (core et cas d'usage).

-- pgvector : type « vector » et opérateurs de similarité.
CREATE EXTENSION IF NOT EXISTS vector;

-- Un chunk est un extrait de document, localisé (document, chapitre, page) pour pouvoir être cité.
-- L'embedding fait 1024 dimensions (taille des modèles d'embeddings visés, ex. bge-m3 ou mxbai-embed-large).
CREATE TABLE chunk (
    id        BIGINT  GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    document  TEXT    NOT NULL,
    chapitre  TEXT,
    page      INTEGER,
    texte     TEXT    NOT NULL,
    embedding vector(1024)
);

-- Recherche plein texte en français. Les requêtes doivent utiliser exactement la même expression
-- (to_tsvector('french', texte)) pour que l'index soit utilisé.
CREATE INDEX chunk_texte_fts_idx ON chunk USING GIN (to_tsvector('french', texte));
