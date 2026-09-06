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
- [x] **Étape 1 — Ingestion documentaire** : lecture des PDF (PDFBox), découpage aux titres et aux phrases,
      provenance obligatoire (document, chapitre, page), commande `--ingest`, ingestion idempotente
- [x] **Étape 2 — Recherche** : interface `EmbeddingClient` (Ollama, bge-m3, en local), embeddings des morceaux
      dans pgvector, trois modes (vectoriel, plein texte français, hybride par Reciprocal Rank Fusion),
      endpoint `/search`, test comparatif sur dix questions
- [x] **Étape 3 — Réponse ancrée et garde-fous** : interface `LlmClient` (Ollama local / API distante, bascule par
      configuration), prompt qui impose les extraits et les citations, refus technique sous un seuil de confiance
      sans appeler le LLM, contrôle des citations après génération, endpoint `/ask`
- [x] **Étape 4 — Données métier et croisement** : le salarié est lu par une requête SQL écrite en Java avec
      paramètre lié (jamais de SQL généré), son ancienneté calculée en Java, et la réponse applique les extraits à sa
      situation dans un prompt à deux blocs, en distinguant ce qui vient du document de ce qui vient de la base ;
      test de cloisonnement : le modèle ne voit que les champs autorisés du salarié demandé
- [ ] **Étape 5 — Harnais d'évaluation** : jeu de référence de 50 cas en trois familles (`eval/golden-set.yaml`),
      exécuteur avec juge LLM (`./mvnw -pl eval test -Dtest=GoldenSetRunner`), rapport `eval/evaluation.md` avec le
      score, la latence, le coût et la liste détaillée des échecs, non-régression par cas bloquants
      (`eval/regression-set.yaml`)
- [ ] **Étape 6 — Cas d'usage hr-leave de bout en bout** : API REST, scénarios de questions sur les congés
      des salariés
- [ ] **Étape 7 — Identité de l'appelant** : authentification, `salarieId` dérivé de l'identité authentifiée et non
      du corps de la requête, journal des accès aux données RH
- [ ] **Étape 8 — Observabilité et coûts** : journal des refus et des réponses suspectes, latence et jetons
      par question, comparaison local / API distante

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

## Réponse ancrée

Prérequis : Ollama avec `bge-m3` dans tous les cas (la question est vectorisée localement), et pour la génération soit
un modèle local (`ollama pull qwen2.5:3b`, environ 1,9 Go), soit une clé d'API distante. Seule la génération bascule.

Le générateur est choisi par une seule ligne de configuration, `llm.provider` dans `application.yml` :

- `local` (défaut) : Ollama sur le poste, modèle `llm.local.model` ;
- `api` : toute API compatible OpenAI (`llm.api.base-url`, `llm.api.model` ; Mistral par défaut), avec la clé lue
  par Spring depuis l'environnement (variable `LLM_API_KEY`, ou le fichier `.env` local ignoré par git), jamais
  dans le code ni dans un fichier versionné.

Le reste du code ne connaît que l'interface `LlmClient` ; un test le prouve en démarrant le contexte avec chaque valeur.

```bash
./mvnw spring-boot:run
curl -s -X POST http://localhost:8080/ask -H "Content-Type: application/json; charset=utf-8" --data-binary @- <<'EOF'
{"question": "J'ai 3 ans d'ancienneté, combien de jours pour mon mariage ?"}
EOF
```

Le JSON passe par l'entrée standard : aucune apostrophe à échapper, et les accents restent en UTF-8 (le `curl` de
Git Bash les enverrait sinon en cp1252). La réponse contient le texte généré, les citations fondées, les morceaux
fournis au modèle, la latence, les jetons consommés, les signaux de la recherche et les seuils (champ `confiance`).
Sur un processeur portable avec `qwen2.5:3b`, compter 45 à 60 secondes par question (environ 2 400 jetons de
prompt), moins de 10 s quand le prompt est déjà en cache, et un peu plus de 100 ms pour un refus sans appel au modèle.

Trois garde-fous, dont deux ne dépendent pas du modèle :

1. **Avant le modèle** : la recherche hybride doit couvrir la question, sinon le service refuse **sans appeler le
   LLM**. La question est couverte si la similarité cosinus du meilleur morceau vectoriel atteint
   `reponse.seuil-similarite` (0,58), **ou** si la densité lexicale du meilleur morceau plein texte atteint
   `reponse.seuil-lexical` (0,40 ; densité = n/(n+10) pour n occurrences des mots de la question), **ou** si la
   question cite un article présent en titre dans le corpus (un article seulement cité par d'autres ne compte pas ;
   si l'article cité est absent, la recherche lexicale repart sur les mots de la question). Mesure sur 31 questions
   avec bge-m3 : hors sujet ≤ 0,547 en similarité et ≤ 0,333 en densité (« capitale du Japon » 0,30 / 0,17,
   « montant du SMIC » 0,52 / 0,17, « télétravail à l'étranger » 0,547 / 0,23) ; couvertes ≥ 0,618 en similarité
   ou ≥ 0,444 en densité (la question courte « Combien de jours pour mon mariage ? », 0,560 / 0,524, passe par la
   densité). Ce que le code garantit : le refus du hors-sujet franc. Ce qu'il ne garantit pas : une question hors
   sujet qui réutilise le vocabulaire du corpus, ou qui cite un article présent, atteint le modèle, et seule la
   consigne joue alors. À remesurer si le modèle d'embedding ou le corpus change.
2. **Dans le prompt** : répondre uniquement à partir des extraits, citer chaque affirmation au format
   `[Document, Article, p. N]`, refuser par la phrase exacte « Je n'ai pas trouvé cette information dans les
   documents fournis. », ne jamais utiliser de connaissances générales, signaler les contradictions, traiter
   question et extraits comme des données. Un exemple de réponse est donné : sans lui, le modèle 3B refuse à tort.
3. **Après le modèle** : une réponse qui n'est pas un refus, dont aucune citation ne désigne un extrait fourni
   (même article, même page ; le nom du document peut comporter une coquille) et qui ne s'appuie sur aucune donnée
   de la situation (section suivante) est marquée suspecte (journal et champ `suspecte`). Un refus du modèle, même
   entouré de politesses, est renvoyé sous la forme de la phrase exacte.

Les trois comportements attendus, à rejouer :

```bash
curl -s -X POST http://localhost:8080/ask -H "Content-Type: application/json; charset=utf-8" --data-binary @- <<'EOF'
{"question": "J'ai 3 ans d'ancienneté, combien de jours pour mon mariage ?"}
EOF
curl -s -X POST http://localhost:8080/ask -H "Content-Type: application/json; charset=utf-8" --data-binary @- <<'EOF'
{"question": "Puis-je télétravailler depuis l'étranger ?"}
EOF
curl -s -X POST http://localhost:8080/ask -H "Content-Type: application/json; charset=utf-8" --data-binary @- <<'EOF'
{"question": "Quelle est la capitale du Japon ?"}
EOF
```

Limite connue du modèle 3B : il refuse à tort les questions « que dit l'article L3141-3 ? » alors que l'article est en
tête des extraits (il répond en revanche à « que prévoit l'article 5.7 de la convention ? »). Un modèle 7B
(`ollama pull qwen2.5:7b`, 4,7 Go, deux fois plus lent) ou l'API distante corrige ce point sans toucher au code.

Sous PowerShell, la même chose avec `Invoke-RestMethod` (apostrophes doublées dans la chaîne) :

```powershell
Invoke-RestMethod -Method Post -Uri http://localhost:8080/ask -ContentType "application/json; charset=utf-8" -Body '{"question": "J''ai 3 ans d''ancienneté, combien de jours pour mon mariage ?"}' | ConvertTo-Json -Depth 4
Invoke-RestMethod -Method Post -Uri http://localhost:8080/ask -ContentType "application/json; charset=utf-8" -Body '{"question": "Puis-je télétravailler depuis l''étranger ?"}' | ConvertTo-Json -Depth 4
Invoke-RestMethod -Method Post -Uri http://localhost:8080/ask -ContentType "application/json; charset=utf-8" -Body '{"question": "Quelle est la capitale du Japon ?"}' | ConvertTo-Json -Depth 4
```

## Réponse personnalisée : documents + base RH

Prérequis : le modèle local `qwen2.5:7b` (`ollama pull qwen2.5:7b`, 4,7 Go), défaut depuis cette étape. Le 3B ne
sait pas appliquer une règle à paliers ni soustraire un solde.

`POST /ask` accepte un `salarieId` en plus de la question. Le déroulé, entièrement en Java sauf la rédaction :

1. le salarié est lu en base par une requête écrite en Java, constante, paramètre lié, colonnes nommées une à une
   (`SalarieRepository.REQUETE`) ; le LLM ne génère jamais de SQL ;
2. son ancienneté est calculée en Java à la date du jour (`Anciennete` : « 7 ans et 6 mois », « soit 7,5 années ») ;
3. la recherche documentaire habituelle est faite sur la question ;
4. le prompt contient la question entre guillemets puis deux blocs étiquetés par leur source,
   `SITUATION DU SALARIÉ (source : base de données RH)` et
   `EXTRAITS DE DOCUMENTS (source : convention collective et Code du travail)` ;
5. le modèle applique la règle des extraits à cette situation précise, écrit le calcul, et distingue ce qui vient du
   document (cité avec son étiquette) de ce qui vient de la base (« (source : base de données RH) »). Si la question
   affirme une ancienneté ou un solde différents de la base, la base fait foi.

La réponse JSON renvoie séparément `situation` (les champs montrés au modèle) et `citations` (les extraits cités) :
la distinction des sources ne dépend pas de la prose du modèle. Une réponse qui s'appuie sur la base sans citer de
document (« il manque 2,5 jours ») n'est pas suspecte : la marque « (source : base de données RH) » compte comme ancrage.

Règle de sécurité : le modèle ne voit que le salarié demandé et que les champs listés dans
`SituationSalarieService.CHAMPS_AUTORISES` (date d'embauche, ancienneté, contrat, statut, temps de travail, solde de
congés en jours ouvrés, convention). **Le nom n'en fait pas partie** : aucune règle n'en dépend, et une donnée
nominative n'a pas à quitter le poste quand la génération passe par une API distante. Le test
`SecuriteDonneesSalarieTest` vérifie que le prompt construit pour un salarié ne contient aucun nom ni rien d'un autre
salarié, que la liste des champs est exactement celle autorisée et que la requête SQL est bien
`SELECT <colonnes nommées> FROM salarie WHERE id = :id`. Ajouter un champ visible oblige à passer par ces deux endroits.

Périmètre assumé du démonstrateur : il n'y a pas d'authentification, `salarieId` est un paramètre libre et la
situation est renvoyée dans la réponse. Ce que le code garantit, c'est le cloisonnement du **modèle** ; celui de
l'**appelant** (l'identifiant dérivé de l'identité authentifiée) est l'étape 5 de la feuille de route.

Les deux commandes de la démonstration, même question, deux salariés :

```bash
curl -s -X POST http://localhost:8080/ask -H "Content-Type: application/json; charset=utf-8" --data-binary @- <<'EOF'
{"salarieId": 1, "question": "Combien de jours pour mon mariage ?"}
EOF
curl -s -X POST http://localhost:8080/ask -H "Content-Type: application/json; charset=utf-8" --data-binary @- <<'EOF'
{"salarieId": 2, "question": "Combien de jours pour mon mariage ?"}
EOF
```

Point d'honnêteté : dans ce corpus, le congé pour mariage est de quatre jours pour tous (article 5.7 Syntec,
article L. 3142-4 du Code), sans condition d'ancienneté ; le système répond donc quatre jours ouvrés à Amina comme à
Marc, et il aurait tort de faire autrement. Les règles qui dépendent réellement de la situation sont le solde, les
congés d'ancienneté de l'article 5.1 (un jour ouvré supplémentaire après cinq ans, deux après dix, trois après quinze,
quatre après vingt, ancienneté appréciée au 1er mai) et le maintien de salaire de l'article 9.2 (selon statut et
ancienneté) :

```bash
curl -s -X POST http://localhost:8080/ask -H "Content-Type: application/json; charset=utf-8" --data-binary @- <<'EOF'
{"salarieId": 1, "question": "Ai-je assez de solde pour prendre 15 jours en août ?"}
EOF
curl -s -X POST http://localhost:8080/ask -H "Content-Type: application/json; charset=utf-8" --data-binary @- <<'EOF'
{"salarieId": 1, "question": "Ai-je des jours de congés supplémentaires grâce à mon ancienneté ?"}
EOF
curl -s -X POST http://localhost:8080/ask -H "Content-Type: application/json; charset=utf-8" --data-binary @- <<'EOF'
{"salarieId": 2, "question": "Ai-je des jours de congés supplémentaires grâce à mon ancienneté ?"}
EOF
```

Mesuré le 5 septembre 2026 avec `qwen2.5:7b` sur processeur (90 à 140 s par réponse, 2 400 à 2 900 jetons de
prompt ; 236 ms pour le refus d'une question hors sujet, sans appel au modèle même quand `salarieId` est fourni) :

| Question (même texte pour les deux)                         | Amina (7 ans et 6 mois, 12,5 j)                         | Marc (1 an et 11 mois, 4 j)                                   |
|-------------------------------------------------------------|---------------------------------------------------------|---------------------------------------------------------------|
| Combien de jours pour mon mariage ?                         | quatre jours ouvrés `[…, Article L. 3142-4, p. 11]`      | quatre jours ouvrés `[…, Article L. 3142-4, p. 11]`            |
| Ai-je assez de solde pour prendre 15 jours en août ?        | non, 12,5 jours ouvrés (source : base RH), il manque 2,5 | non, 4 jours ouvrés (source : base RH), il manque 11           |
| Ai-je des jours de congés supplémentaires grâce à mon ancienneté ? | un jour ouvré supplémentaire, 7,5 ans `[…, Article 5.1, p. 24]` | aucun, 1 an et 11 mois ne dépasse pas le palier de 5 ans `[…, Article 5.1, p. 24]` |

Aucune ligne de code ne connaît Amina ni Marc : la différence vient de la ligne `salarie` lue en base et de la
règle lue dans l'extrait. Deux limites observées, à connaître avant une démonstration :

- sur les réponses de solde, le modèle ajoute une citation d'extrait superflue (le passage cité existe et lui a été
  fourni, mais la réponse vient de la base) ; la marque « (source : base de données RH) » et le champ `situation`
  restent la source fiable ;
- « Combien de jours de congés payés ai-je par an, avec mon ancienneté ? » est trop pour le 7B local : il répond
  juste à Marc (25 jours, palier de 5 ans non atteint) mais additionne un mauvais palier pour Amina (27 au lieu de 26).
  La question directe sur les jours d'ancienneté, ci-dessus, est juste pour les deux. Le total demande un modèle plus
  fort : basculer `llm.provider=api` ne change aucune autre ligne. La question courte « Combien de jours de congés
  payés ai-je par an ? » ne remonte pas le morceau des paliers dans les cinq extraits (le modèle répond alors 25 jours
  à tous) : dire « avec mon ancienneté » suffit à le placer en tête.

## Évaluation : le vrai score, même mauvais

Le module `eval` mesure le système de l'extérieur, par son API, comme le ferait un client. Trois fichiers, tous
dans `eval/` :

- `golden-set.yaml` : le jeu de référence, 50 cas en trois familles. **A** (20) : la réponse est dans les documents
  seuls ; on note la question, la réponse attendue et la source attendue (document, article ; plusieurs sources
  possibles quand la convention et le Code disent la même chose). **B** (20) : la réponse demande les documents et
  la fiche du salarié ; on note la question, le `salarieId` et la réponse attendue, dont le couple Amina / Marc sur
  la même question. **C** (10) : le système doit refuser, dont un piège de culture générale et une question RH hors
  du corpus. Trente cas sont fournis, écrits à partir des documents réellement ingérés et relus contre le texte des
  morceaux ; les vingt derniers sont à écrire à la main (le fichier dit où).
- `regression-set.yaml` : les cas promus **bloquants**. Si l'un d'eux échoue, la commande échoue : c'est la
  non-régression. Pour promouvoir un échec, ajouter son identifiant, la date et le motif.
- `evaluation.md` : le rapport généré, écrit pour un non-développeur : date, commit, score en une phrase, tableau
  par famille, citations correctes, refus corrects, latence médiane et p95, coût moyen, puis **la liste détaillée
  des échecs** (question, réponse attendue, réponse obtenue, cause probable) et l'état des cas bloquants.

```bash
./mvnw spring-boot:run                                   # dans un premier terminal : l'application, la base, Ollama
./mvnw -pl eval test -Dtest=GoldenSetRunner              # dans un second : environ une heure avec qwen2.5:7b sur processeur
```

L'exécuteur cherche l'application sur `http://localhost:8081` (le `server.port` d'`application.yml`) ; une autre
adresse se passe par `-Deval.base-url=http://hote:port`. Le juge est `qwen2.5:7b` via Ollama (`-Deval.juge.model`),
le coût se calcule avec `-Deval.prix.entree-par-million` et `-Deval.prix.sortie-par-million` (euros, zéro pour un
modèle local). Ce test est exclu du build ordinaire : `./mvnw package` ne le lance jamais. Il vérifie d'abord que
l'application et le juge répondent, puis réécrit le rapport après chaque cas : une interruption laisse un rapport
partiel daté, avec un bandeau « évaluation en cours ».

Contrainte matérielle, apprise le 5 septembre 2026 : une campagne complète mobilise pendant deux heures PostgreSQL
(Docker), Ollama avec le modèle 7B (4,7 Go) et l'embarqué bge-m3, l'application et le JVM des tests, soit environ
8 Go. Sur le portable de 16 Go, avec un navigateur et un IDE ouverts, Ollama et Docker Desktop ont été tués en cours
de campagne (le rapport le montre : 14 cas « non mesurables », dont les six refus attendus). Lancer l'évaluation sur
une machine au repos, ou fermer les applications lourdes avant.

Comment un cas est jugé, et par qui :

- **Refus (famille C)** : par le code. Réussi si l'application a refusé.
- **Citations et ancrage** : par le code. La source attendue est comparée aux citations que l'application a elle-même
  vérifiées (même article, même document, typographie ignorée). Une bonne réponse mal sourcée est un échec ; une
  réponse que l'application marque elle-même « suspecte » (ni citation fondée ni donnée de la fiche) aussi.
- **Justesse (familles A et B)** : par un **juge**, second appel à un modèle de langage, sans aléa, consigne
  stricte : la réponse obtenue est correcte seulement si elle contient chaque information de la réponse attendue
  sans en contredire aucune (un chiffre en lettres vaut un chiffre, une unité différente est une erreur) ; un refus
  est incorrect. Le juge ne sert que là. Un verdict illisible ou une panne (application, juge) donne un cas « non
  mesurable », compté en échec et affiché à part : la campagne continue, le rapport est réécrit après chaque cas.

Limites de la méthode du juge, à connaître avant de présenter le score : c'est un modèle, il se trompe parfois (une
tolérance ou une sévérité injustifiée sur une formulation) ; il ne vérifie que ce que la réponse attendue contient,
donc une réponse attendue incomplète ou fausse fausse le verdict ; il ne juge pas une nuance juridique que l'expert
n'a pas écrite ; quand il est le même modèle que le système évalué, il peut être indulgent avec son propre style ;
et il double le temps de calcul. C'est pourquoi les refus et les citations sont jugés par le code, que chaque échec
porte la raison du juge pour être relu par un humain, et que seuls des cas relus à la main sont promus bloquants.
Un juge plus fort (API distante) réduit la première limite, pas les autres.

## Structure

```
core/                   le socle réutilisable (bibliothèque)
examples/hr-leave/      premier cas d'usage : questions sur les congés (application Spring Boot)
eval/                   le harnais d'évaluation : golden-set.yaml, regression-set.yaml, evaluation.md, exécuteur
docker-compose.yml      PostgreSQL 16 + pgvector
CLAUDE.md               règles et conventions du projet (contraintes, structure, commandes)
```

Le schéma de la base est géré par Flyway : les migrations du socle sont dans `core`, celles du cas d'usage dans
`examples/hr-leave`. La version d'une migration est son horodatage de création (`V202609041700__init_core.sql`),
ce qui donne un ordre chronologique global à tous les modules.
