# groundedIA — règles du projet

Ce fichier est lu au début de chaque session. Il fait foi : ne pas s'en écarter sans accord explicite de l'utilisateur.

## Le projet en une phrase

groundedIA est un socle Spring Boot qui branche un LLM sur un système d'information existant :
il croise des **documents** avec une **base de données métier**, **cite ses sources**, **refuse d'inventer**,
et **mesure sa propre fiabilité** automatiquement.

## À quoi il sert

Démonstrateur commercial. Il doit être **lisible, sobre et défendable devant un architecte d'entreprise**.
Chaque ligne doit pouvoir être expliquée en rendez-vous client. **Pas de sur-ingénierie** : pas d'abstraction
sans besoin immédiat, pas de dépendance qu'on ne sait pas justifier en une phrase.

## Contraintes techniques non négociables

- **Java 21, Spring Boot 3.x, Maven** (wrapper `mvnw` fourni, Maven 3.9).
- **PostgreSQL 16 + extension pgvector**, lancé via `docker-compose.yml`. Une seule base héberge les documents
  (chunks + embeddings) et les données métier.
- **Aucun framework IA** : pas de Spring AI, pas de LangChain4j, ni aucun équivalent. Les interfaces `LlmClient`
  et `EmbeddingClient` sont écrites dans `core` et implémentées avec `RestClient` de Spring.
  Raison : pouvoir expliquer chaque ligne en rendez-vous client, et basculer entre un modèle distant (API)
  et un modèle local (Ollama) par simple configuration.
- **Le LLM ne génère JAMAIS de SQL.** Toutes les requêtes sont écrites en Java, avec des paramètres liés
  (`JdbcClient` / `JdbcTemplate`). C'est un argument de sécurité vendu au client.
- **Machine cible : portable 16 Go de RAM, sans carte graphique.** Rester économe : modèles locaux légers,
  pas de traitement massif en mémoire, un seul conteneur Docker (PostgreSQL). Le seul service annexe admis
  est Ollama, installé nativement sur le poste pour le modèle local.

## Structure des modules

```
pom.xml                 parent Maven (hérite de spring-boot-starter-parent), agrège les modules
core/                   le socle réutilisable : clients LLM/embeddings, ingestion, recherche, ancrage, citations
examples/               les cas d'usage ; chaque exemple est une application Spring Boot autonome bâtie sur core
  hr-leave/             premier cas d'usage : questions sur les congés (documents RH + table salarie)
eval/                   le harnais d'évaluation : mesure automatique de la fiabilité des réponses
docker-compose.yml      PostgreSQL 16 + pgvector (image pgvector/pgvector), volume persistant, port 5432
```

- groupId `fr.groundedia` ; packages `fr.groundedia.core`, `fr.groundedia.examples.<cas>`, `fr.groundedia.eval`.
- `core` et `eval` sont des bibliothèques (jar) sans classe `main`. Seul `examples/hr-leave` est lançable :
  les autres modules ignorent `spring-boot:run` grâce à la propriété `spring-boot.run.skip` (true dans le
  parent, false dans le module applicatif). Ainsi `./mvnw spring-boot:run` à la racine lance l'application.
- L'application scanne tout `fr.groundedia` (`scanBasePackages`) pour détecter les composants de `core`.

## Base de données et migrations

- Le schéma est géré **uniquement par Flyway**. Jamais de `ddl-auto`, jamais de modification manuelle.
- Chaque module porte ses migrations dans ses propres ressources :
  - `core/src/main/resources/db/migration/core/` : extension `vector`, table `chunk`…
  - `examples/<cas>/src/main/resources/db/migration/<cas>/` : tables métier, jeux de test.
  L'application déclare ses emplacements dans `spring.flyway.locations` (avec `fail-on-missing-locations`).
- **Version d'une migration = horodatage de création**, format `V<AAAAMMJJHHMM>__description.sql`
  (ex. `V202609041700__init_core.sql`). L'ordre chronologique est donc global à tous les modules : une nouvelle
  migration, quel que soit son module, est toujours postérieure à celles déjà appliquées (Flyway reste en
  `out-of-order=false`). Ne jamais modifier une migration déjà appliquée : en écrire une nouvelle.
- Tables actuelles :
  - `chunk` (id, document, chapitre, page, texte, embedding vector(1024)) + index GIN plein texte français
    sur `to_tsvector('french', texte)` ; les requêtes doivent utiliser exactement cette expression.
  - `salarie` (id, nom, date_embauche, type_contrat, statut, temps_travail, solde_conges, convention),
    avec 3 salariés de test insérés par migration.
- Identifiants et port : variables `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_HOST`,
  `POSTGRES_PORT`, avec des valeurs par défaut de développement (voir `.env.example`). Un seul fichier `.env`
  à la racine (ignoré par git) est lu à la fois par `docker-compose.yml` et par l'application
  (`spring.config.import`, optionnel) ; les variables d'environnement, si présentes, ont priorité.
  Le port PostgreSQL n'est publié que sur `127.0.0.1`.

## Conventions de code

- Langue : documentation, commentaires, messages et vocabulaire métier en **français** (tables, colonnes,
  objets métier : `salarie`, `solde_conges`…). Les identifiants techniques restent en anglais idiomatique
  Java (`LlmClient`, `ChunkRepository`).
- Configuration par `application.yml` et variables d'environnement ; aucun secret dans le dépôt.
- Code plat et explicite plutôt que design générique : une classe fait une chose, pas de couche « au cas où ».
- Toute dépendance ajoutée est justifiée par un commentaire d'une ligne dans le `pom.xml`.
- Pas de tests exigeant la base sans conteneur disponible ; on décide de l'outillage de test à la première
  étape qui en a besoin.
- `mvnw` doit rester en mode `100755` dans git (`git ls-files -s mvnw`) : Windows ne conserve pas le bit
  exécutable, il faut l'indexer avec `git add --chmod=+x mvnw`.

## Commandes utiles

```bash
docker compose up -d                              # PostgreSQL + pgvector (port 5432, ou POSTGRES_PORT)
./mvnw spring-boot:run                            # lance examples/hr-leave (PowerShell : .\mvnw.cmd ...)
curl http://localhost:8080/actuator/health        # {"status":"UP"} : l'application et sa connexion à la base
docker compose exec postgres psql -U groundedia -d groundedia -c 'SELECT * FROM salarie;'
./mvnw -q package                                 # build complet de tous les modules
./mvnw clean                                      # obligatoire après suppression ou renommage d'une migration :
                                                  # Maven ne purge pas les copies obsolètes dans target/classes
docker compose down -v                            # repartir d'une base vide (supprime le volume)
```

Si le port 5432 est déjà pris sur le poste (PostgreSQL installé localement), copier `.env.example` en `.env`
et y mettre `POSTGRES_PORT=5433` : Compose et l'application lisent ce même fichier.

## Feuille de route

Les 9 étapes (0 à 8), avec cases à cocher, sont dans `README.md`. Règle : **on ne travaille que sur l'étape
demandée**, on ne prépare pas les suivantes. Quand l'utilisateur valide une étape, cocher sa case dans le README
et mettre à jour ce fichier si une convention a changé.

## Règles pour les sessions Claude

1. Relire ce fichier avant toute modification de structure.
2. Respecter les contraintes ci-dessus sans exception ; si une demande les contredit, le signaler avant d'agir.
3. Rester dans le périmètre de l'étape demandée : pas de « bonus », pas d'anticipation.
4. Chaque ajout doit être explicable à un architecte en une ou deux phrases ; sinon, ne pas l'ajouter.
5. Préférer la solution la plus simple qui marche ; pas de dépendance, de couche ou d'abstraction spéculative.
6. Terminer chaque étape par les commandes exactes permettant de la vérifier.
