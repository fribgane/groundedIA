# groundedIA

Socle Spring Boot qui branche un LLM sur un système d'information existant. Il croise des **documents** avec
une **base de données métier**, **cite ses sources**, **refuse d'inventer**, et **mesure sa propre fiabilité**
automatiquement.

Sans framework IA : les clients LLM et embeddings sont écrits maison avec `RestClient`, interchangeables entre
une API distante et un modèle local (Ollama) par simple configuration. Le LLM ne génère jamais de SQL : toutes
les requêtes sont écrites en Java avec des paramètres liés.

## Feuille de route

- [x] **Étape 0 — Socle technique** : structure Maven multi-modules, PostgreSQL 16 + pgvector via Docker,
      migrations Flyway, application minimale avec `/actuator/health`
- [ ] **Étape 1 — Ingestion documentaire** : lecture des PDF (PDFBox), découpage aux titres et aux phrases,
      provenance obligatoire (document, chapitre, page), commande `--ingest`, ingestion idempotente
- [ ] **Étape 2 — Recherche** : interface `EmbeddingClient` (Ollama, bge-m3, en local), embeddings des morceaux
      dans pgvector, trois modes (vectoriel, plein texte français, hybride par Reciprocal Rank Fusion),
      endpoint `/search`, test comparatif sur dix questions
- [ ] **Étape 3 — Client LLM** : interface `LlmClient`, implémentations `RestClient` (API distante / Ollama),
      bascule par configuration
- [ ] **Étape 4 — Accès aux données métier** : requêtes SQL écrites en Java avec paramètres liés, résultats
      transmis au LLM ; jamais de SQL généré
- [ ] **Étape 5 — Réponse ancrée** : assemblage du contexte (extraits documentaires + données métier), réponse
      avec citation des sources
- [ ] **Étape 6 — Refus et garde-fous** : pas de source suffisante, pas de réponse ; contrôle que chaque
      affirmation cite une source réelle
- [ ] **Étape 7 — Cas d'usage hr-leave de bout en bout** : API REST, scénarios de questions sur les congés
      des salariés
- [ ] **Étape 8 — Harnais d'évaluation** : jeu de questions de référence, métriques de fiabilité (ancrage,
      citations, refus), rapport automatique

## Prérequis

- Java 21 (variable `JAVA_HOME` pointant sur le JDK 21)
- Docker Desktop avec Compose v2
- Maven est fourni par le wrapper (`mvnw`, Maven 3.9), rien à installer

## Démarrage rapide

```bash
docker compose up -d        # PostgreSQL 16 + pgvector, port 5432, volume persistant
./mvnw spring-boot:run      # Windows PowerShell : .\mvnw.cmd spring-boot:run
```

Si le port 5432 est déjà pris sur votre poste (PostgreSQL installé localement), copiez `.env.example` en `.env`
et mettez-y `POSTGRES_PORT=5433` : Compose et l'application lisent ce même fichier. Le port n'est publié que
sur `127.0.0.1`.

## Vérification

```bash
curl http://localhost:8080/actuator/health
# {"status":"UP"}   (le contrôle inclut la connexion à la base)

docker compose exec postgres psql -U groundedia -d groundedia -c 'SELECT * FROM salarie;'
# 3 lignes : Amina Diallo, Marc Petit, Julie Nguyen
```

## Ingestion des documents

Déposer les PDF (conventions collectives, extraits du Code du travail…) dans `documents/`, dossier ignoré par git, puis :

```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments=--ingest=documents/
```

Sous Windows PowerShell, l'argument doit être entre guillemets (PowerShell le coupe sinon au premier point) :

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--ingest=documents/"
```

Chaque PDF est lu page par page (Apache PDFBox) puis découpé : on coupe aux titres (« Article 24 », « Art. L3141-3 »,
« L. 3141-1 », « Chapitre Ier », lignes en majuscules) sans jamais mélanger deux titres dans un morceau, jamais au milieu
d'une phrase (les unités sont les phrases et les alinéas d'énumération « 1° … ; 2° … »), en remplissant chaque morceau
jusqu'à 1200 caractères au maximum, soit 800 à 1200 caractères dans les sections longues (un article court forme un
morceau court), avec un chevauchement de 100 caractères entre morceaux consécutifs d'un même titre. En-têtes et pieds de
page, numéros de page, lignes de sommaire et lignes de liens sont ignorés. Chaque morceau conserve le document, le titre
dont il provient et sa page : c'est ce qui permet de citer la source. L'ingestion est idempotente : un PDF inchangé (même
empreinte SHA-256, même version des règles de découpage) est ignoré, un PDF modifié remplace ses morceaux, un PDF
illisible est signalé sans bloquer les autres.

Corpus de démonstration (textes publics) : la convention collective Syntec consolidée par l'avenant n° 46 du
16 juillet 2021, et le Titre IV « Congés payés et autres congés » du Code du travail (parties législative et
réglementaire), extrait du PDF quotidien de [codes.droit.org](https://codes.droit.org/), compilation des données
ouvertes Légifrance. Tout autre PDF texte déposé dans `documents/` est ingéré de la même façon.

```bash
docker compose exec postgres psql -U groundedia -d groundedia -c "SELECT document, count(*) AS morceaux, min(page), max(page) FROM chunk GROUP BY document;"
```

## Recherche

Prérequis : Ollama en local (`ollama serve`) avec le modèle d'embedding `bge-m3` (`ollama pull bge-m3`, environ
1,2 Go). Les embeddings sont calculés sur le poste, gratuitement : les documents ne quittent pas la machine.

```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments=--embed    # vecteurs des morceaux qui n'en ont pas, par lots, avec progression
./mvnw spring-boot:run                                        # puis, dans un autre terminal :
curl "http://localhost:8080/search?q=combien+de+jours+pour+me+marier&mode=hybrid"
```

Sous PowerShell : `.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--embed"` (guillemets obligatoires).
Le calcul prend environ 4 minutes pour 500 morceaux sur un processeur portable ; la commande reprend là où elle
s'est arrêtée. Pour changer de modèle d'embedding, remettre les vecteurs à zéro
(`UPDATE chunk SET embedding = NULL;`) puis relancer `--embed` ; un modèle d'une autre dimension que 1024
(par exemple `nomic-embed-text`, 768) impose aussi une migration de la colonne `embedding`.

Trois modes, choisis par `search.mode` dans `application.yml` ou par le paramètre `mode=` de l'endpoint :

- `vector` : similarité cosinus (pgvector) entre le vecteur de la question et celui de chaque morceau ;
- `fulltext` : recherche lexicale PostgreSQL en français. Question ordinaire : mots lemmatisés, mots vides
  ignorés, filtre sur l'index GIN, classement `ts_rank_cd`. Question qui cite un article (« L3141-3 »,
  « article 5.7 ») : seuls les morceaux qui portent ce titre ou le citent sont retenus, par expression régulière
  tolérante à la typographie (le lexiseur français sépare « L. 3141-3 » en « 3141 » et « -3 »), le titre d'abord ;
- `hybrid` : les 20 meilleurs vectoriels et les 5 meilleurs plein texte, fusionnés par Reciprocal Rank Fusion
  (k = 60). Les profondeurs diffèrent parce que la liste vectorielle reste pertinente loin dans le classement
  alors qu'une liste plein texte en « ou » n'est fiable qu'en tête ; elles ont été calibrées sur les dix questions
  du test comparatif.

L'endpoint renvoie les 5 meilleurs morceaux avec leur score (similarité, rang plein texte ou score RRF selon le
mode), leur document, leur article et leur page. Le test comparatif exécute dix questions dans les trois modes,
affiche les résultats côte à côte (rang du bon morceau, top 3 de chaque mode) et vérifie que l'hybride trouve le
bon morceau dans le top 3 plus souvent que chaque mode seul :

```bash
./mvnw test -pl examples/hr-leave -am -Dcomparatif=true
```

## Structure

```
core/                   le socle réutilisable (bibliothèque)
examples/hr-leave/      premier cas d'usage : questions sur les congés (application Spring Boot)
eval/                   le harnais d'évaluation
docker-compose.yml      PostgreSQL 16 + pgvector
CLAUDE.md               règles et conventions du projet (contraintes, structure, commandes)
```

Le schéma de la base est géré par Flyway : les migrations du socle sont dans `core`, celles du cas d'usage dans
`examples/hr-leave`. La version d'une migration est son horodatage de création (`V202609041700__init_core.sql`),
ce qui donne un ordre chronologique global à tous les modules.
