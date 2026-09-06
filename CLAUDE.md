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
  - `document` (nom, empreinte, nb_pages, ingere_le) : un PDF ingéré ; supprimer la ligne supprime ses morceaux.
  - `chunk` (id, document → document.nom, chapitre NOT NULL, page NOT NULL, texte, embedding vector(1024))
    + index GIN plein texte français sur `to_tsvector('french', texte)` ; les requêtes doivent utiliser
    exactement cette expression.
  - `salarie` (id, nom, date_embauche, type_contrat, statut, temps_travail, solde_conges, convention),
    avec 3 salariés de test insérés par migration.
- Identifiants et port : variables `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_HOST`,
  `POSTGRES_PORT`, avec des valeurs par défaut de développement (voir `.env.example`). Un seul fichier `.env`
  à la racine (ignoré par git) est lu à la fois par `docker-compose.yml` et par l'application
  (`spring.config.import`, optionnel) ; les variables d'environnement, si présentes, ont priorité.
  Le port PostgreSQL n'est publié que sur `127.0.0.1`.

## Ingestion des documents (étape 1)

- Package `fr.groundedia.core.ingestion` : `PdfTextExtractor` (PDFBox, texte page par page), `Chunker` (découpage),
  `IngestionService` (empreinte, une transaction par document), `IngestionCommand` (argument `--ingest=<dossier>`,
  l'application démarre alors sans serveur web et s'arrête à la fin).
- Règles de découpage, par ordre de priorité : couper aux titres (« Article 24 », « Art. L3141-3 », « L. 3141-1 »,
  « Chapitre Ier », lignes en majuscules) sans jamais mélanger deux titres dans un morceau ; ne jamais couper au
  milieu d'une phrase (unités = phrases, et alinéas d'énumération « 1° … ; 2° … ») ; remplir chaque morceau jusqu'à
  1200 caractères au maximum, jamais au-delà sauf phrase seule plus longue (soit 800 à 1200 dans les sections longues,
  un article court donne un morceau court) ; reprendre 100 caractères du morceau précédent, jamais au-delà d'un titre.
- Provenance obligatoire (colonnes `NOT NULL`) : `document` = nom du fichier, `chapitre` = dernier titre vu
  (sinon « Début du document »), `page` = page où commence le morceau. C'est ce qui permet de citer.
- Idempotence : table `document` avec une empreinte = SHA-256 du fichier + `Chunker.VERSION`. Inchangé = ignoré,
  modifié = remplacé (suppression en cascade des morceaux). **Toute modification des règles de découpage
  incrémente `Chunker.VERSION`**, ce qui force la ré-ingestion de tous les documents au prochain `--ingest`.
- Nettoyage des PDF réels, appris sur les documents de terrain : en-têtes et pieds de page (lignes d'extrémité de
  page répétées, numéro de page neutralisé), lignes de sommaire à points de suite, lignes de liens (« > … »,
  « service-public.fr ») et leur intitulé, métadonnées Légifrance (« En vigueur étendu », « Modifié par … »)
  jamais prises pour un intitulé d'article. Trois styles de titres d'articles reconnus : « Article 24 »,
  « Art. L3141-3 », « L. 3141-1 » seul en tête de ligne (codes compilés).
- Les PDF sources sont dans `documents/`, ignoré par git (textes publics volumineux). Corpus de démonstration :
  convention collective Syntec (avenant n° 46 du 16 juillet 2021, texte consolidé, 60 pages) et Code du travail,
  Titre IV « Congés payés et autres congés » des parties législative (40 pages) et réglementaire (24 pages),
  extraits du PDF quotidien de codes.droit.org (compilation des données ouvertes Légifrance/DILA).
- Tests unitaires du découpage dans `core` (`./mvnw test`), sans base de données ; le test d'extraction construit
  ses PDF avec PDFBox.

## Recherche (étape 2)

- Package `fr.groundedia.core.embedding` : `EmbeddingClient` (interface maison), `OllamaEmbeddingClient`
  (`RestClient`, `POST /api/embed`, modèle `ollama.embedding-model`, base `ollama.base-url`), `EmbeddingCommand`
  (argument `--embed` : vecteurs des morceaux sans embedding, lots de 16, barre de progression dans les journaux,
  reprise possible). Le délai HTTP est réglé par `spring.http.client.read-timeout`.
- Modèle : `bge-m3` (1024 dimensions = colonne `chunk.embedding`, multilingue). Changer de modèle implique de
  changer la dimension de la colonne par migration et de recalculer tous les vecteurs.
- Package `fr.groundedia.core.recherche` : `Mode` (vector | fulltext | hybrid, configuré par `search.mode`),
  `RechercheRepository` (deux requêtes SQL à paramètres liés), `ReferencesArticles` (une référence citée dans la
  question est reconnue quelle que soit sa typographie et sa casse : « L3141-3 », « L. 3141-3 », « l3141-3 »),
  `Rrf` (fusion, k = 60, égalités départagées par l'identifiant), `RechercheService`, `RechercheController`
  (`GET /search?q=&mode=`, `search.limit` = 5 résultats ; 400 pour une question vide ou un mode inconnu, 503 si
  Ollama est arrêté, le modèle absent ou les embeddings non calculés).
- Lexical (`fulltext`) : sans référence, les lexèmes de `plainto_tsquery('french', …)` sont cherchés en « ou » (pas
  en « et ») pour garder du rappel, classés par `ts_rank_cd` normalisé dans [0, 1[ (option 32). Avec une référence,
  seuls les morceaux qui la portent en titre (+2) ou la citent (+1) sont retenus : les paliers sont stricts.
- Hybride : `search.candidats-vectoriels` = 20 et `search.candidats-plein-texte` = 5. Profondeurs différentes à
  dessein (la liste plein texte en « ou » n'est fiable qu'en tête), calibrées sur les dix questions du test
  comparatif ; ne pas les retoucher sans refaire la mesure et le dire.
- Après un `--ingest` qui a réinséré des morceaux, relancer `--embed` (l'application le rappelle au démarrage).
  Changement de modèle : `UPDATE chunk SET embedding = NULL;` puis `--embed` ; autre dimension que 1024 =
  migration de la colonne.
- Test comparatif `RechercheComparativeTest` (examples/hr-leave) : dix questions, cinq en langage naturel, cinq
  avec référence exacte ; activé par `-Dcomparatif=true` car il exige la base, les embeddings et Ollama.

## Réponse ancrée (étape 3)

- Package `fr.groundedia.core.llm` : `LlmClient` (interface maison : `generer(consigne, message)` → texte + jetons),
  `OllamaLlmClient` (`POST /api/chat`, température 0, graine fixe, `num_ctx` 8192, actif si `llm.provider=local`,
  valeur par défaut), `ApiLlmClient` (`POST {llm.api.base-url}/chat/completions`, compatible OpenAI, actif si
  `llm.provider=api`, clé lue par Spring depuis l'environnement : variable `LLM_API_KEY` ou `.env` local non
  versionné, démarrage refusé sans clé). **Rien d'autre ne change entre les deux** : `LlmClientSelectionTest` le
  prouve, ne pas introduire de code spécifique à un fournisseur ailleurs. Ollama reste nécessaire dans les deux cas
  pour les embeddings (bge-m3) : seule la génération bascule.
- Package `fr.groundedia.core.reponse` : `ReponseService` (recherche hybride de `reponse.morceaux` = 5 morceaux,
  prompt, appel, contrôle) et `ReponseController` (`POST /ask {"question"}`).
- Garde-fous, dans l'ordre : (1) question non couverte → refus **sans appel au LLM** (garde fermée par défaut,
  décision dans `ReponseService.confiance()`) ; couverte si similarité cosinus du 1er vectoriel ≥
  `reponse.seuil-similarite` (0,58), **ou** densité lexicale du 1er plein texte (n/(n+10), dans [0, 1[) ≥
  `reponse.seuil-lexical` (0,40), **ou** un morceau porte en titre l'article que la question cite (un article
  seulement cité par d'autres morceaux ne compte pas ; article cité absent du corpus → la recherche lexicale repart
  sur les mots de la question). Les signaux viennent de `RechercheService.hybride()` (`RechercheDetaillee`), la
  décision reste dans `reponse` ; (2) la consigne système (`ReponseService.CONSIGNE_SYSTEME`), avec un exemple de
  réponse : sans exemple, le modèle 3B refuse à tort ; (3) réponse non-refus dont aucune citation ne désigne un
  extrait fourni (même article, même page, nom du document ignoré) et sans marque « (source : base de données RH) »
  quand une situation est fournie → `suspecte` (journal + champ), seules les citations fondées sont renvoyées.
  Phrase de refus : constante `ReponseService.REFUS`, renvoyée telle quelle dès que le modèle refuse (détection
  tolérante à l'apostrophe, la casse et le point final).
- Les seuils 0,58 / 0,40 sont calibrés sur 31 questions mesurées avec des chaînes UTF-8 correctes (hors sujet
  ≤ 0,547 et ≤ 0,333 ; couvertes ≥ 0,618 ou ≥ 0,444 ; « Combien de jours pour mon mariage ? » = 0,560 / 0,524 passe
  par la densité) : toute modification du modèle d'embedding ou du corpus impose de remesurer (voir README).
  Attention : le `curl` de Git Bash (7.87, sans Unicode) envoie les accents en cp1252 et fausse toute mesure ;
  passer le JSON par l'entrée standard, ou utiliser `Invoke-RestMethod` / node.
- Les journaux ne contiennent pas le texte des questions (données RH) au niveau INFO/WARN, seulement au niveau DEBUG.
- Modèle local par défaut `qwen2.5:7b` depuis l'étape 4 (4,7 Go, tenable sur 16 Go sans GPU ; 100 à 130 s par
  question sur processeur, ≈ 2 400 à 2 800 jetons de prompt). `qwen2.5:3b` (1,9 Go, 45 à 60 s) reste utilisable
  pour les questions sans salarié mais se trompe dans tout calcul (paliers, soustraction). Moins de 10 s si le
  prompt est en cache, ≈ 110 ms pour un refus sans appel au modèle. Réponses reproductibles à température 0 avec
  graine fixe (mode local). Limite connue : le 3B refuse à tort « que dit l'article L3141-3 ? » même avec l'extrait
  en tête et une consigne dédiée (testé) ; ne pas retoucher la consigne pour ce cas.
- Les exemples de la consigne sont **fictifs** (`accord.pdf`, article 12, déménagement ; solde 8 / 10 jours) : s'ils
  fuient dans une réponse, la citation ne désigne aucun extrait fourni et la réponse est marquée suspecte. Un exemple
  réel (5.7, p. 26) rendait cette fuite invisible. Le 3B avait besoin d'un exemple réel pour ne pas refuser ; le 7B non.

## Réponse personnalisée (étape 4)

- Le socle ne connaît pas la table `salarie` : `Situation` (liste de champs libellés) et l'interface
  `SituationProvider` sont dans `fr.groundedia.core.reponse` ; `ReponseController` résout `salarieId` par le
  `SituationProvider` disponible (400 si absent ou inconnu) et `ReponseService.repondre(question, situation)`
  construit le prompt : la question entre « », puis le bloc `SITUATION DU SALARIÉ (source : base de données RH)`
  (constantes `TITRE_SITUATION` / `SOURCE_SITUATION`, une ligne « - libellé : valeur » par champ), puis
  `EXTRAITS DE DOCUMENTS (source : <reponse.source-documents>)`. Question et valeurs sont aplaties sur une ligne :
  aucune donnée ne peut imiter un bloc. La réponse renvoie `situation` et `citations` séparément.
- Le cas d'usage porte les données métier dans `fr.groundedia.examples.hrleave.salarie` : `Salarie`,
  `SalarieRepository` (requête constante `REQUETE` = colonnes nommées `COLONNES` + `WHERE id = :id`), `Anciennete`
  (années et mois, en Java, à la date de l'horloge injectée, aussi en années décimales « soit 7,5 années » : le 7B
  compare mieux 7,5 à cinq et dix que « 7 ans et 6 mois »), `SituationSalarieService` (les champs autorisés, dans
  `CHAMPS_AUTORISES` : date d'embauche, ancienneté, contrat, statut, temps de travail, solde, convention ; statut
  traduit dans le vocabulaire Syntec « ingénieurs et cadres » / « ETAM » seulement si la convention est Syntec ;
  convention absente = « non renseignée » ; solde en « jours ouvrés », l'unité de `salarie.solde_conges`).
- **Règle de sécurité** : le modèle ne voit que le salarié demandé et que les champs autorisés. **Le nom n'en fait
  pas partie** : aucune règle n'en dépend et une donnée nominative ne doit pas partir vers une API distante. Toute
  colonne ajoutée passe par `COLONNES` et `CHAMPS_AUTORISES`, et `SecuriteDonneesSalarieTest` le vérifie (prompt
  sans aucun nom ni trace d'un autre salarié, liste de champs exacte, `REQUETE` exacte sans `*`).
- Périmètre assumé : pas d'authentification, `salarieId` est un paramètre libre et la situation est renvoyée dans
  la réponse (même en cas de refus). Le cloisonnement garanti est celui du modèle, pas celui de l'appelant ; en
  production l'identifiant vient de l'identité authentifiée (étape 5 de la feuille de route).
- La consigne (règle 6) demande d'écrire le calcul, de ne retenir que le palier d'ancienneté le plus élevé atteint,
  de donner l'écart pour un solde, de répondre aux questions de solde et d'ancienneté depuis la situation, et fait
  prévaloir la situation sur ce que la question affirme. Ce qui vient de la base est marqué
  « (source : base de données RH) » ; cette marque compte comme ancrage pour le contrôle `suspecte`.
- Le corpus ne fait pas dépendre le congé de mariage de l'ancienneté (4 jours pour tous) : ne pas essayer de faire
  dire « 5 jours » au système. Les règles à ancienneté réelles sont l'article 5.1 (congés d'ancienneté) et 9.2
  (maintien de salaire) de la convention Syntec. Le morceau des paliers du 5.1 n'est remonté que si la question
  évoque l'ancienneté : la question courte « Combien de jours de congés payés ai-je par an ? » ne le place pas dans
  les cinq extraits et le modèle répond alors 25 jours à tous.
- Mesuré le 2026-09-05 avec qwen2.5:7b via `POST /ask` : mariage 4 j pour les deux ✓ ; solde « il manque 2,5 » /
  « il manque 11 » ✓ (avec une citation d'extrait superflue) ; « Ai-je des jours de congés supplémentaires grâce à
  mon ancienneté ? » → Amina 1 jour (palier 5 ans), Marc aucun ✓ ; « Combien de jours de congés payés ai-je par an,
  avec mon ancienneté ? » → Marc 25 ✓ mais Amina 27 ✗ (mauvais palier). Questions de démonstration : les trois
  premières ; le total annuel demande l'API. Ne pas retoucher la consigne sans rejouer ces huit questions.

## Évaluation (étape 5)

- Module `eval`, package `fr.groundedia.eval`, **autonome** : aucune dépendance sur `core` ni `hr-leave`, il évalue
  l'application par HTTP (`GET /actuator/health`, `POST /ask`) comme un client ; c'est ce qui permet
  `./mvnw -pl eval test -Dtest=GoldenSetRunner` sans `-am` ni `install`. Ne pas y introduire de dépendance interne.
- Fichiers à la racine du module (Maven exécute les tests depuis `eval/`) : `golden-set.yaml` (listes `famille_a`,
  `famille_b`, `famille_c` ; champs `id`, `question`, `reponse_attendue`, `source_attendue` objet ou liste
  d'alternatives `{document, article}`, `salarieId` en B, `commentaire`), `regression-set.yaml` (`bloquants` :
  `id`, `promu_le`, `motif`), `evaluation.md` (généré, versionné : c'est le livrable). Champ inconnu = erreur de lecture.
- Classes : `JeuDeCas` / `Regression` (lecture YAML, validation), `AskClient` (RestClient, lecture 15 min,
  `verifierDisponible()` distingue injoignable et non saine), `Evaluateur` (décision par famille + cause probable
  déduite des signaux de l'API : garde / modèle via `confiance.couverte`, passage attendu fourni ou non, citation ;
  une panne de l'application ou du juge sur un cas donne un résultat `nonMesurable`, la campagne continue), `Juge` +
  `OllamaJuge` (`/api/chat`, température 0, graine 42, `verifierDisponible()` via `/api/tags`, consigne stricte
  binaire « CORRECT : … » / « INCORRECT : … » qui tranche chiffres en lettres, décimales, unités et valeurs multiples,
  parsée par `Verdict`, féminin toléré), `Sources` (article + document, sans casse ni espaces, point gardé dans un
  numéro : « 1.2 » ≠ « 12 », « 5.7 » ≠ « 5.75 », « L. 3141-5 » ≠ « L. 3141-5-1 »), `Metriques` (latences et jetons sur
  les seules questions ayant appelé le modèle, refus de la garde comptés à part, p95 au rang le plus proche, coût =
  jetons × prix configurés, `nonMesurables`), `Rapport` (markdown pour non-développeur, bandeau « en cours », note du
  cas dans chaque échec, définitions de garde / similarité / densité / suspecte), `Configuration` (propriétés
  système `-Deval.*` seulement : `base-url` défaut `http://localhost:8081`, `juge.base-url`, `juge.model` défaut
  `qwen2.5:7b`, `prix.entree-par-million`, `prix.sortie-par-million` (virgule acceptée), chemins des trois fichiers),
  `Git` (commit court, drapeau modifications locales).
- Réussite : C = `refus` vrai (code) ; A/B = pas de refus, verdict CORRECT du juge, réponse non `suspecte`, et si des
  sources sont attendues au moins une citation renvoyée en désigne une (une bonne réponse mal sourcée ou non ancrée
  est un échec, la règle est écrite dans le rapport). Les refus, l'ancrage et les citations ne passent jamais par le
  juge. Verdict illisible ou erreur technique = échec `nonMesurable`, compté à part dans le rapport. Latence =
  `latenceMs` de l'application.
- `GoldenSetRunner` (test JUnit) est exclu par surefire dans `eval/pom.xml` (`<excludes>`) : le build ordinaire ne le
  lance pas, `-Dtest=GoldenSetRunner` l'emporte sur l'exclusion. Il vérifie application et juge avant de commencer,
  refuse un jeu vide, réécrit le rapport après chaque cas et échoue **après** l'avoir écrit si un cas bloquant a
  échoué. Durée : ≈ 2 à 3 min par cas A/B avec qwen2.5:7b sur processeur (réponse + juge), ≈ 1 h pour 30 cas.
- `GoldenSetFichierTest` (build ordinaire) relit le vrai `golden-set.yaml` : répartition ≤ 20/20/10, cas
  obligatoires présents (Japon, télétravail, couple 1/2), chaque bloquant existe. YAML : toute valeur contenant « : »
  doit être entre guillemets.
- Les cas B dépendent de la date (ancienneté) : les réponses attendues sont calculées au 2026-09-05 et restent
  vraies tant qu'aucun salarié de test ne franchit un palier (prochain : Julie 10 ans en 2031, Amina 10 ans le
  2029-03-01). Marc (embauché le 2024-09-15) passe 2 ans le 2026-09-15 : aucune règle du corpus ne change à 2 ans.
- Ne jamais ajuster les réponses attendues pour faire monter le score : un cas douteux se corrige après relecture
  du morceau (`SELECT texte FROM chunk WHERE id = …`), et la correction se note dans `commentaire`. Une réponse
  attendue se limite au fait vérifiable (chiffre, conclusion) : le raisonnement va dans `commentaire`, sinon le juge
  exige qu'il soit récité (constaté le 2026-09-05 sur B01).
- Campagnes du 2026-09-05 (qwen2.5:7b, juge qwen2.5:7b, 30 cas) : premier passage interrompu à 18 cas (8/18 ; A 3/12,
  B 5/6), second passage complet mais Ollama puis Docker tués par manque de mémoire à partir du 14e cas (8/30, 14 non
  mesurables ; A 4/12 mesurés en entier, B 4/4 mesurés, C non mesuré). Échecs réels et reproductibles en A : A01
  (répond « 6 jours de recherche d'emploi »), A03 (refus, passage fourni), A04/A10 (recherche ne remonte pas 5.2 /
  L. 3141-7), A05 (lit mal les seuils du 5.1), A11 (Syntec 7 j au lieu du Code 14 j), A08/A09 (jugés inexacts).
  Une campagne complète demande ≈ 8 Go libres pendant 2 h (≈ 4 min par cas A/B) : fermer navigateur et IDE, ou
  machine dédiée. Résultats reproductibles d'un passage à l'autre (température 0, graine fixe).

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
./mvnw spring-boot:run -Dspring-boot.run.arguments=--ingest=documents/   # ingère les PDF de documents/
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--ingest=documents/"   # même chose sous PowerShell (guillemets obligatoires)
docker compose exec postgres psql -U groundedia -d groundedia -c 'SELECT document, count(*) FROM chunk GROUP BY document;'
docker compose exec postgres psql -U groundedia -d groundedia -c 'DELETE FROM document;'   # repartir de zéro côté documents (cascade sur chunk)
./mvnw spring-boot:run -Dspring-boot.run.arguments=--embed     # embeddings des morceaux (Ollama + bge-m3 requis)
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--embed"   # même chose sous PowerShell (guillemets obligatoires)
curl "http://localhost:8080/search?q=que+dit+l+article+L3141-3&mode=fulltext"   # recherche (mode : vector | fulltext | hybrid)
./mvnw test -pl examples/hr-leave -am -Dcomparatif=true      # test comparatif des trois modes (base + Ollama requis ; -am construit core)
curl -s -X POST http://localhost:8080/ask -H "Content-Type: application/json; charset=utf-8" -d '{"question": "Quelle est la capitale du Japon ?"}'   # réponse ancrée (refus attendu)
./mvnw -pl eval test -Dtest=GoldenSetRunner       # évaluation complète (application démarrée requise) → eval/evaluation.md ; ≈ 1 h
./mvnw -pl eval test -Dtest=GoldenSetRunner -Deval.base-url=http://localhost:8080   # si l'application écoute ailleurs que sur server.port
./mvnw -q package                                 # build complet de tous les modules (tests compris)
./mvnw -q test -pl core                           # tests unitaires du socle
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
