# Évaluation de groundedIA — 5 septembre 2026 à 23:29

Commit `5193289` (le dépôt contenait des modifications non enregistrées dans git). Application évaluée : http://localhost:8082, génération par le modèle local `qwen2.5:7b` via Ollama. Juge : modèle local `qwen2.5:7b` via Ollama.

Jeu de référence : **30 cas** (12 A, 12 B, 6 C) sur les 50 prévus.

## En une phrase

Le système a réussi **8 cas sur 30 (27 %)** : 4 des 12 questions dont la réponse est dans les documents, 4 des 12 questions qui croisent les documents et la fiche du salarié, et il a refusé 0 des 6 questions auxquelles il ne devait pas répondre. Parmi les échecs, 14 cas n'ont pas pu être mesurés (erreur technique ou verdict du juge illisible) : ils comptent en échec.

## Scores

| Famille | Ce qu'on mesure | Cas | Réussis | Score |
|---|---|---:|---:|---:|
| A — documents seuls | la réponse est juste selon le juge et cite la bonne source | 12 | 4 | 33 % |
| B — documents et fiche du salarié | la réponse est juste pour ce salarié (et cite la bonne source quand une est attendue) | 12 | 4 | 33 % |
| C — refus attendu | le système refuse au lieu d'inventer | 6 | 0 | 0 % |
| **Global** | | 30 | 8 | **27 %** |

| Indicateur | Valeur |
|---|---|
| Sources citées correctes (parmi les réponses données quand une source était attendue) | 11 / 13 (85 %) |
| Refus corrects (famille C) | 0 / 6 (0 %) |
| Refus décidés par la garde, sans appel au modèle | 0 |
| Temps de réponse médian (questions ayant appelé le modèle) | 284,4 s |
| Temps de réponse maximal pour 95 % de ces questions (p95) | 553,1 s |
| Coût moyen par question | 0 € : modèle local, le coût est le temps machine (284,4 s par question en médiane) ; 2 636 jetons en moyenne par question ayant appelé le modèle |
| Réponses marquées suspectes par l'application | 0 |
| Cas non mesurables (erreur technique, verdict illisible), comptés en échec | 14 |

## Échecs détaillés (22)

### A01 — « Je me pacse le mois prochain : à combien de jours d'absence ai-je droit ? »

- **Réponse attendue** : 4 jours ouvrés.
- **Source attendue** : 5.7 (convention Syntec) ou L. 3142-4 (Code du travail, partie législative)
- **Réponse obtenue** : Vous avez droit à six (6) jours ouvrés pour la recherche d'emploi [convention-collective-syntec-avenant-46-2021.pdf, Article 4.3 – Absence pour recherche d'emploi, p. 20].
- **Sources citées** : [convention-collective-syntec-avenant-46-2021.pdf, Article 4.3 – Absence pour recherche d'emploi, p. 20]
- **Cause probable** : réponse inexacte selon le juge (La réponse fournit un autre chiffre d'absence (6 au lieu de 4).) ; le passage attendu était dans les extraits fournis au modèle (défaut de rédaction ou de raisonnement)
- **Note du cas** : Syntec 5.7 p. 26 (morceau 3082) : « se marier ou conclure un pacte civil de solidarité (PACS) : quatre (4) jours ouvrés », non déductibles des congés, sans réduction de salaire. Le Code (L. 3142-4) donne le même minimum.

### A03 — « Notre entreprise ferme trois semaines en août pour les congés : à quelle date au plus tard doit-elle prévenir les salariés ? »

- **Réponse attendue** : Au plus tard le 1er mars.
- **Source attendue** : 5.4 (convention Syntec)
- **Réponse obtenue** : Je n'ai pas trouvé cette information dans les documents fournis.
- **Cause probable** : refus du modèle alors que le passage attendu était dans les extraits fournis : il n'a pas su l'utiliser
- **Note du cas** : Syntec 5.4 p. 25 (morceau 3077) : fermeture totale sur la période du 1er mai au 31 octobre → date de fermeture portée à la connaissance des salariés au plus tard le 1er mars. Piège voisin : D. 3141-5 (deux mois avant l'ouverture de la période de prise) répond à une autre question.

### A04 — « Mon employeur m'a rappelé au travail pendant mes congés pour une urgence de service. Ai-je droit à une compensation ? »

- **Réponse attendue** : Oui : 2 jours de congés payés supplémentaires, plus le remboursement des frais occasionnés par ce rappel, sur justificatifs.
- **Source attendue** : 5.2 (convention Syntec)
- **Réponse obtenue** : Je n'ai pas trouvé cette information dans les documents fournis.
- **Cause probable** : refus du modèle ; le passage attendu n'était pas dans les extraits fournis (défaut de recherche)
- **Note du cas** : Syntec 5.2 p. 24 (morceau 3075), texte littéral.

### A05 — « Si je pose 3 jours de congés payés en février, en dehors de la période d'été, est-ce que j'ai droit à des jours de congés supplémentaires ? »

- **Réponse attendue** : Oui : 1 jour de congé supplémentaire.
- **Source attendue** : 5.1 (convention Syntec) ou L. 3141-23 (Code du travail, partie législative)
- **Réponse obtenue** : Vous n'avez pas droit à des jours de congés supplémentaires pour 3 jours pris en dehors de la période d'été, car le nombre de jours ouvrés de congés payés pris en dehors de cette période doit être au moins égal à cinq (5) pour obtenir des jours de congés supplémentaires [convention-collective-syntec-avenant-46-2021.pdf, Article 5.1 – Durée des congés payés, p. 24].
- **Sources citées** : [convention-collective-syntec-avenant-46-2021.pdf, Article 5.1 – Durée des congés payés, p. 24]
- **Cause probable** : réponse inexacte selon le juge (La réponse obtenue contredit la réponse attendue en indiquant que l'on n'a pas droit à des jours de congés supplémentaires, alors que la réponse attendue affirme le contraire.) ; le passage attendu était dans les extraits fournis au modèle (défaut de rédaction ou de raisonnement)
- **Note du cas** : Syntec 5.1 p. 24 (morceau 3074) : 3 ou 4 jours ouvrés pris hors du 1er mai–31 octobre → 1 jour ouvré, 5 ou plus → 2 jours, cinquième semaine exclue. Le Code (L. 3141-23) donne aussi 1 jour pour 3 à 5 jours ouvrables (2 à partir de 6) : même résultat, seuils différents. « ouvré » et « ouvrable » sont tous deux acceptables ici.

### A08 — « Combien de jours de congés payés est-ce que j'acquiers par mois travaillé, et quel est le maximum sur l'année ? »

- **Réponse attendue** : 2,5 jours ouvrables par mois de travail effectif ; 30 jours ouvrables au maximum.
- **Source attendue** : L. 3141-3 (Code du travail, partie législative)
- **Réponse obtenue** : Vous acquérez 25 jours ouvrés de congés payés par an (source : [convention-collective-syntec-avenant-46-2021.pdf, Article 5.1 – Durée des congés payés, p. 24]). Le maximum de congés payés sur une année est donc de 30 jours ouvrables (25 jours + 5 jours supplémentaires potentiels d'ancienneté) (source : [convention-collective-syntec-avenant-46-2021.pdf, Article 5.1 – Durée des congés payés, p. 24]).
- **Sources citées** : [convention-collective-syntec-avenant-46-2021.pdf, Article 5.1 – Durée des congés payés, p. 24]
- **Cause probable** : réponse inexacte selon le juge (La réponse contient des informations supplémentaires non demandées et contredit la réponse attendue en proposant un taux annuel au lieu du taux mensuel.) ; le passage attendu n'était pas dans les extraits fournis au modèle (défaut de recherche)
- **Note du cas** : L. 3141-3 p. 2 (morceau 2699). Piège : L. 3141-5-1 (2 jours par mois, plafond 24) ne vaut que pour les arrêts maladie non professionnels ; Syntec 5.1 exprime la même durée en jours ouvrés (25).

### A09 — « À combien correspond mon indemnité de congés payés par rapport à ce que j'ai gagné sur la période de référence ? »

- **Réponse attendue** : À un dixième (1/10) de la rémunération brute totale perçue au cours de la période de référence.
- **Source attendue** : L. 3141-24 (Code du travail, partie législative)
- **Réponse obtenue** : L'indemnité de congés payés est égale au dixième (1/10e) de la rémunération perçue par le salarié au cours de la période de référence [convention-collective-syntec-avenant-46-2021.pdf, Article 5.6 – Indemnité de congés payés, p. 26]. Cette rémunération est déterminée en tenant compte de l'indemnité de congé de l'année précédente, des indemnités afférentes à la contrepartie obligatoire sous forme de repos, des périodes assimilées à un temps de travail, et des périodes de service national, de formation économique, sociale et syndicale [code-du-travail-partie-legislative-titre-IV-conges.pdf, Article L. 3141-24, p. 8].
- **Sources citées** : [convention-collective-syntec-avenant-46-2021.pdf, Article 5.6 – Indemnité de congés payés, p. 26] ; [code-du-travail-partie-legislative-titre-IV-conges.pdf, Article L. 3141-24, p. 8]
- **Cause probable** : réponse inexacte selon le juge (La réponse obtenue contient des informations supplémentaires non demandées et contredit l'information attendue sur le calcul de l'indemnité.) ; le passage attendu était dans les extraits fournis au modèle (défaut de rédaction ou de raisonnement)
- **Note du cas** : L. 3141-24 p. 8 (morceau 2726). La règle du maintien de salaire (même article) est une alternative, pas une contradiction.

### A10 — « Mon décompte de congés tombe sur 17,5 jours ouvrables : est-ce qu'on m'arrondit à 17 ou à 18 ? »

- **Réponse attendue** : À 18 jours.
- **Source attendue** : L. 3141-7 (Code du travail, partie législative)
- **Réponse obtenue** : Je n'ai pas trouvé cette information dans les documents fournis.
- **Cause probable** : refus du modèle ; le passage attendu n'était pas dans les extraits fournis (défaut de recherche)
- **Note du cas** : L. 3141-7 p. 3 (morceau 2704) : la durée est portée au nombre entier immédiatement supérieur. Demande un petit raisonnement à partir de la règle.

### A11 — « Ma fille de 32 ans, elle-même maman de deux enfants, est décédée. Combien de jours de congé pour décès le Code du travail me garantit-il au minimum ? »

- **Réponse attendue** : 14 jours.
- **Source attendue** : L. 3142-4 (Code du travail, partie législative)
- **Réponse obtenue** : Le Code du travail garantit au minimum quatre jours de congé pour le décès d'un enfant [code-du-travail-partie-legislative-titre-IV-conges.pdf, Article L. 3142-4, p. 11]. Cependant, la convention collective précise que pour un enfant âgé de moins de vingt-cinq ans, le salarié peut bénéficier de sept jours ouvrés [convention-collective-syntec-avenant-46-2021.pdf, Article 5.7 – Congés pour évènements familiaux, p. 26].
- **Sources citées** : [code-du-travail-partie-legislative-titre-IV-conges.pdf, Article L. 3142-4, p. 11] ; [convention-collective-syntec-avenant-46-2021.pdf, Article 5.7 – Congés pour évènements familiaux, p. 26]
- **Cause probable** : réponse inexacte selon le juge (La réponse obtenue contredit la réponse attendue en proposant un nombre de jours différent (4 au lieu de 14).) ; le passage attendu était dans les extraits fournis au modèle (défaut de rédaction ou de raisonnement)
- **Note du cas** : L. 3142-4 p. 11 (morceau 2741) : 12 jours pour le décès d'un enfant, portés à 14 quel que soit son âge s'il était lui-même parent ; jours ouvrables (p. 12). Cas difficile voulu : Syntec 5.7 dit 7 jours ouvrés (avenant de 2021, antérieur au relèvement légal) et la question demande le minimum garanti par le Code. Une réponse « 7 jours » est fausse.

### B02 — « Ai-je des jours de congés supplémentaires grâce à mon ancienneté ? » (salarié 2)

- **Réponse attendue** : Non, aucun jour supplémentaire.
- **Source attendue** : 5.1 (convention Syntec)
- **Réponse obtenue** : aucune (erreur technique)
- **Cause probable** : erreur technique, l'application n'a pas répondu : 503 : "{"erreur":"Ollama n'a pas répondu dans le délai spring.http.client.read-timeout (http://localhost:11434)"}"
- **Note du cas** : Marc : embauche le 15 septembre 2024 → 1 an et 11 mois (1,9 année) au 5 septembre 2026, le premier palier (cinq ans) n'est pas atteint. Même question que B01.

### B04 — « Ai-je assez de solde pour prendre 15 jours en août ? » (salarié 2)

- **Réponse attendue** : Non : le solde est de 4 jours ouvrés, il manque 11 jours.
- **Réponse obtenue** : aucune (erreur technique)
- **Cause probable** : erreur technique, l'application n'a pas répondu : 500 : "{"timestamp":"2026-09-05T21:06:11.588+00:00","status":500,"error":"Internal Server Error","path":"/ask"}"
- **Note du cas** : Marc, solde_conges = 4. Même question que B03.

### B07 — « Si je démissionne, quelle est la durée de mon préavis ? » (salarié 1)

- **Réponse attendue** : 3 mois.
- **Source attendue** : 4.2 (convention Syntec)
- **Réponse obtenue** : Vous avez droit à trois mois de préavis en tant que cadre, selon l'article 4.2 de la convention collective Syntec [convention-collective-syntec-avenant-46-2021.pdf, Article 4.2 – Durée du préavis, p. 19].
- **Sources citées** : [convention-collective-syntec-avenant-46-2021.pdf, Article 4.2 – Durée du préavis, p. 19]
- **Cause probable** : erreur technique, le juge n'a pas pu être consulté : 500 Internal Server Error: "{"error":"an error was encountered while running the model: read tcp 127.0.0.1:40181-\u003e127.0.0.1:35954: wsarecv: An existing connection was forcibly closed by the remote host."}"
- **Note du cas** : Syntec 4.2 p. 19 (morceau 3063) : ingénieurs et cadres → 3 mois, sans condition d'ancienneté.

### B08 — « Mon employeur envisage de me licencier : quel préavis doit-il respecter dans mon cas ? » (salarié 2)

- **Réponse attendue** : 1 mois.
- **Source attendue** : 4.2 (convention Syntec)
- **Réponse obtenue** : aucune (erreur technique)
- **Cause probable** : erreur technique, l'application n'a pas répondu : 503 : "{"erreur":"Ollama injoignable sur http://localhost:11434 (lancer « ollama serve », puis « ollama pull bge-m3 ») : I/O error on POST request for \"http://localhost:11434/api/embed\": HTTP/1.1 header parser received no bytes"}"
- **Note du cas** : Syntec 4.2 : ETAM de moins de deux ans d'ancienneté → 1 mois (2 mois au-delà de deux ans ou aux coefficients 400/450/500 ; le coefficient n'est pas dans la fiche). Marc : 1 an et 11 mois. Réponse vraie jusqu'au 14 septembre 2026 inclus : Marc atteint 2 ans le 15 septembre 2026, la réponse devient alors 2 mois — à mettre à jour.

### B09 — « Je suis en arrêt maladie : pendant combien de temps mon salaire est-il maintenu, et à quel taux ? » (salarié 1)

- **Réponse attendue** : 90 jours à 100 % du salaire brut.
- **Source attendue** : 9.2 (convention Syntec)
- **Réponse obtenue** : aucune (erreur technique)
- **Cause probable** : erreur technique, l'application n'a pas répondu : 503 : "{"erreur":"Ollama injoignable sur http://localhost:11434 (lancer « ollama serve », puis « ollama pull bge-m3 ») : I/O error on POST request for \"http://localhost:11434/api/embed\": null"}"
- **Note du cas** : Syntec 9.2 p. 40 (morceau 3119) : ingénieurs et cadres ayant plus d'un an d'ancienneté → 90 jours à 100 %. Même question pour les trois salariés (B09, B10, B11) : trois réponses différentes selon statut et ancienneté.

### B10 — « Je suis en arrêt maladie : pendant combien de temps mon salaire est-il maintenu, et à quel taux ? » (salarié 2)

- **Réponse attendue** : 30 jours à 100 % du salaire brut, puis les 60 jours suivants à 80 %.
- **Source attendue** : 9.2 (convention Syntec)
- **Réponse obtenue** : aucune (erreur technique)
- **Cause probable** : erreur technique, l'application n'a pas répondu : 503 : "{"erreur":"Ollama injoignable sur http://localhost:11434 (lancer « ollama serve », puis « ollama pull bge-m3 ») : I/O error on POST request for \"http://localhost:11434/api/embed\": null"}"
- **Note du cas** : Syntec 9.2 : ETAM ayant plus d'un an et moins de cinq ans d'ancienneté. Marc : 1 an et 11 mois.

### B11 — « Je suis en arrêt maladie : pendant combien de temps mon salaire est-il maintenu, et à quel taux ? » (salarié 3)

- **Réponse attendue** : 60 jours à 100 % du salaire brut, puis les 30 jours suivants à 80 %.
- **Source attendue** : 9.2 (convention Syntec)
- **Réponse obtenue** : aucune (erreur technique)
- **Cause probable** : erreur technique, l'application n'a pas répondu : 503 : "{"erreur":"Ollama injoignable sur http://localhost:11434 (lancer « ollama serve », puis « ollama pull bge-m3 ») : I/O error on POST request for \"http://localhost:11434/api/embed\": null"}"
- **Note du cas** : Syntec 9.2 : ETAM ayant plus de cinq ans d'ancienneté. Julie : embauche le 10 janvier 2021 → 5 ans et 7 mois.

### B12 — « J'ai 12 ans d'ancienneté : à combien de jours de congés d'ancienneté ai-je droit ? » (salarié 2)

- **Réponse attendue** : Aucun jour de congé d'ancienneté : l'ancienneté réelle est de 1 an et 11 mois, et non 12 ans.
- **Source attendue** : 5.1 (convention Syntec)
- **Réponse obtenue** : aucune (erreur technique)
- **Cause probable** : erreur technique, l'application n'a pas répondu : 503 : "{"erreur":"Ollama injoignable sur http://localhost:11434 (lancer « ollama serve », puis « ollama pull bge-m3 ») : I/O error on POST request for \"http://localhost:11434/api/embed\": null"}"
- **Note du cas** : La question affirme une ancienneté fausse : la situation en base fait foi et la réponse doit signaler l'écart. Une réponse « 2 jours » (palier des dix ans appliqué aux 12 ans affirmés) est fausse.

### C01 — « Quelle est la capitale du Japon ? » — **cas bloquant**

- **Réponse attendue** : un refus (« Je n'ai pas trouvé cette information dans les documents fournis. »)
- **Réponse obtenue** : aucune (erreur technique)
- **Cause probable** : erreur technique, l'application n'a pas répondu : 503 : "{"erreur":"Ollama injoignable sur http://localhost:11434 (lancer « ollama serve », puis « ollama pull bge-m3 ») : I/O error on POST request for \"http://localhost:11434/api/embed\": null"}"
- **Note du cas** : Culture générale. Aucun morceau ne contient « Japon », « Tokyo » ni « capitale » ; refus attendu de la garde, sans appel au modèle.

### C02 — « Puis-je télétravailler depuis l'étranger ? » — **cas bloquant**

- **Réponse attendue** : un refus (« Je n'ai pas trouvé cette information dans les documents fournis. »)
- **Réponse obtenue** : aucune (erreur technique)
- **Cause probable** : erreur technique, l'application n'a pas répondu : 503 : "{"erreur":"Ollama injoignable sur http://localhost:11434 (lancer « ollama serve », puis « ollama pull bge-m3 ») : I/O error on POST request for \"http://localhost:11434/api/embed\": null"}"
- **Note du cas** : RH plausible mais hors corpus : « télétravail » est absent de l'avenant 46 et du Titre IV. Piège de recherche : « étranger » remonte le chapitre 12 (missions à l'étranger), qui ne répond pas.

### C03 — « Ai-je droit à des tickets restaurant ? »

- **Réponse attendue** : un refus (« Je n'ai pas trouvé cette information dans les documents fournis. »)
- **Réponse obtenue** : aucune (erreur technique)
- **Cause probable** : erreur technique, l'application n'a pas répondu : 503 : "{"erreur":"Ollama injoignable sur http://localhost:11434 (lancer « ollama serve », puis « ollama pull bge-m3 ») : I/O error on POST request for \"http://localhost:11434/api/embed\": null"}"
- **Note du cas** : RH hors corpus : « ticket », « titre-restaurant », « déjeuner », « cantine » absents ; seuls les frais de mission (11.2) sont voisins.

### C04 — « Ai-je droit à une prime d'ancienneté avec mes années dans l'entreprise ? »

- **Réponse attendue** : un refus (« Je n'ai pas trouvé cette information dans les documents fournis. »)
- **Réponse obtenue** : aucune (erreur technique)
- **Cause probable** : erreur technique, l'application n'a pas répondu : 503 : "{"erreur":"Ollama injoignable sur http://localhost:11434 (lancer « ollama serve », puis « ollama pull bge-m3 ») : I/O error on POST request for \"http://localhost:11434/api/embed\": null"}"
- **Note du cas** : Piège de vocabulaire : « prime » (7.3, prime de vacances) et « ancienneté » (3.7, 5.1, 4.5) saturent le corpus, mais aucune prime d'ancienneté n'existe dans la convention Syntec. La garde laissera probablement passer la question : c'est la consigne du modèle qui est testée.

### C05 — « Combien de jours ouvrés de congé ai-je pour la rentrée scolaire de mon enfant ? »

- **Réponse attendue** : un refus (« Je n'ai pas trouvé cette information dans les documents fournis. »)
- **Réponse obtenue** : aucune (erreur technique)
- **Cause probable** : erreur technique, l'application n'a pas répondu : 503 : "{"erreur":"Ollama injoignable sur http://localhost:11434 (lancer « ollama serve », puis « ollama pull bge-m3 ») : I/O error on POST request for \"http://localhost:11434/api/embed\": null"}"
- **Note du cas** : Piège de vocabulaire : « jours ouvrés », « congé », « enfant » saturent l'article 5.7 et L. 3142-1, mais la rentrée scolaire n'y figure pas. Une réponse qui donne un nombre de jours est une invention.

### C06 — « Combien de jours de RTT ai-je par an ? »

- **Réponse attendue** : un refus (« Je n'ai pas trouvé cette information dans les documents fournis. »)
- **Réponse obtenue** : aucune (erreur technique)
- **Cause probable** : erreur technique, l'application n'a pas répondu : 503 : "{"erreur":"Ollama injoignable sur http://localhost:11434 (lancer « ollama serve », puis « ollama pull bge-m3 ») : I/O error on POST request for \"http://localhost:11434/api/embed\": null"}"
- **Note du cas** : RH hors corpus : les RTT relèvent de l'accord de branche du 22 juin 1999, absent ; l'article 6.1 y renvoie en une phrase. « réduction du temps de travail » apparaît dans 8 morceaux (congés de solidarité, formation) sans rapport.

## Cas bloquants (non-régression)

| Cas | Promu le | Motif | État |
|---|---|---|---|
| C01 | 2026-09-05 | question de culture générale (capitale du Japon) que le système doit refuser sans même consulter le modèle | **ÉCHEC** |
| C02 | 2026-09-05 | question RH hors des documents (télétravail depuis l'étranger) que le système doit refuser | **ÉCHEC** |

## Comment lire ce rapport

- **Famille A** : la réponse est dans les documents seuls. Un cas réussit si le juge déclare la réponse correcte **et** si l'une des sources citées par l'application est bien l'article attendu. Une bonne réponse mal sourcée est comptée en échec : devant un salarié, une règle sans sa référence ne se défend pas.
- **Famille B** : la réponse demande les documents et la fiche du salarié dans la base RH (ancienneté, solde, statut). Même règle, la source n'étant exigée que si le cas en attend une.
- **Famille C** : le système doit refuser par la phrase exacte « Je n'ai pas trouvé cette information dans les documents fournis. ». Le contrôle est fait par le code, sans juge.
- **La garde** est le contrôle que l'application fait avant d'appeler le modèle : si la recherche ne trouve aucun passage assez proche de la question (la **similarité**, ressemblance de sens entre la question et le meilleur passage, et la **densité lexicale**, part des mots de la question retrouvés dans le meilleur passage, restent toutes deux sous leur seuil), elle refuse d'office. Un refus de la garde prend une fraction de seconde ; c'est pourquoi les temps de réponse sont donnés pour les seules questions qui ont appelé le modèle.
- **Suspecte** : l'application marque ainsi une réponse qui ne cite aucun extrait fourni et ne s'appuie sur aucune donnée de la fiche du salarié. Le harnais compte une telle réponse en échec même si le juge la trouve juste : une réponse sans ancrage vérifiable ne se défend pas.
- **Le juge** est un second appel à un modèle de langage : il compare la réponse obtenue à la réponse attendue, écrite à la main, avec une consigne stricte (chaque information attendue doit être présente, aucune contredite ; un chiffre en lettres vaut un chiffre, une unité différente est une erreur), sans aucun aléa. Ses limites : il peut se tromper comme tout modèle (une tolérance ou une sévérité injustifiée sur une formulation) ; il ne voit pas les documents et ne vérifie que ce que la réponse attendue contient, donc une réponse attendue incomplète ou erronée fausse le verdict et une précision supplémentaire fausse passe inaperçue si la réponse attendue ne la contredit pas ; il ne sait pas juger une nuance juridique que l'expert n'a pas écrite ; quand il est le même modèle que le système évalué, il peut être indulgent avec son propre style ; et un verdict illisible est compté comme non mesurable, donc en échec. C'est pourquoi les refus, l'ancrage et les citations sont jugés par le code, que chaque échec porte la raison du juge pour être relu par un humain, et que seuls des cas relus à la main sont promus bloquants.
- **La cause probable** est déduite des signaux que l'application renvoie : refus décidé par la garde (la recherche n'a pas trouvé), refus ou erreur du modèle alors que le passage attendu lui avait été fourni (défaut de rédaction ou de raisonnement), passage attendu absent des extraits fournis (défaut de recherche), source citée différente de la source attendue.
- **Le temps de réponse** est celui mesuré par l'application elle-même pour chaque question, recherche et génération comprises. **Le coût** est calculé à partir des jetons consommés et des prix configurés (`-Deval.prix.entree-par-million`, `-Deval.prix.sortie-par-million`, en euros) ; pour un modèle local il est nul, le coût réel étant le temps machine. Le coût du juge n'est pas compté.
- Ce rapport est régénéré par `./mvnw -pl eval test -Dtest=GoldenSetRunner`, l'application étant démarrée.
