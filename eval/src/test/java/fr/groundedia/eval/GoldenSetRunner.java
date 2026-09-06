package fr.groundedia.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.groundedia.eval.Cas.Famille;

/**
 * L'exécuteur du jeu de référence. Il exige l'application démarrée (base, Ollama, embeddings) et dure environ une
 * heure avec un modèle 7B sur processeur ; il est exclu du build ordinaire et se lance explicitement :
 *
 * <pre>./mvnw -pl eval test -Dtest=GoldenSetRunner</pre>
 *
 * Il vérifie d'abord que l'application et le juge répondent, évalue tous les cas de {@code golden-set.yaml} en
 * réécrivant {@code evaluation.md} après chaque cas, puis échoue si un cas promu bloquant dans
 * {@code regression-set.yaml} a échoué : c'est le mécanisme de non-régression.
 */
class GoldenSetRunner {

    private static final Logger log = LoggerFactory.getLogger(GoldenSetRunner.class);

    @Test
    void evaluer_le_jeu_de_reference_et_ecrire_le_rapport() {
        Configuration config = Configuration.depuisProprietes();
        JeuDeCas jeu = JeuDeCas.charger(config.goldenSet());
        assertThat(jeu.cas()).as("%s ne contient aucun cas", config.goldenSet()).isNotEmpty();
        Regression regression = Regression.charger(config.regressionSet());
        if (regression.bloquants().isEmpty()) {
            log.warn("Aucun cas bloquant dans {} : la porte de non-régression est inactive", config.regressionSet());
        } else {
            log.info("{} cas bloquant(s) : {}", regression.bloquants().size(),
                    regression.bloquants().stream().map(Regression.Bloquant::id).toList());
        }
        List<String> bloquantsInconnus = regression.bloquants().stream().map(Regression.Bloquant::id)
                .filter(id -> jeu.parId(id).isEmpty()).toList();
        assertThat(bloquantsInconnus).as("cas bloquants de %s absents de %s", config.regressionSet(), config.goldenSet()).isEmpty();

        AskClient client = new AskClient(config.baseUrl());
        client.verifierDisponible();
        OllamaJuge juge = new OllamaJuge(config.jugeBaseUrl(), config.jugeModele());
        juge.verifierDisponible();
        log.info("Évaluation de {} cas ({} A, {} B, {} C) contre {} ; juge {} ; rapport réécrit après chaque cas dans {}",
                jeu.cas().size(), jeu.famille(Famille.A).size(), jeu.famille(Famille.B).size(), jeu.famille(Famille.C).size(),
                config.baseUrl(), config.jugeModele(), config.rapport().toAbsolutePath());

        String commit = Git.commit();
        boolean modifications = Git.modificationsLocales();
        List<ResultatCas> resultats = new Evaluateur(client, juge).evaluer(jeu.cas(),
                acquis -> ecrire(config, acquis, jeu.cas().size(), commit, modifications, juge.modele(), regression));

        Metriques metriques = Metriques.calculer(resultats, config.prix());
        log.info("Rapport écrit dans {} : score global {} ({} / {}), {} échec(s) dont {} non mesurable(s)",
                config.rapport().toAbsolutePath(), metriques.global().pourcentage(), metriques.global().reussis(),
                metriques.global().total(), metriques.global().total() - metriques.global().reussis(), metriques.nonMesurables());

        List<String> bloquantsEnEchec = resultats.stream()
                .filter(r -> regression.estBloquant(r.cas().id()) && !r.reussi())
                .map(r -> r.cas().id() + " : " + r.cause()).toList();
        assertThat(bloquantsEnEchec).as("cas bloquants en échec (détail dans %s)", config.rapport().toAbsolutePath()).isEmpty();
    }

    private static void ecrire(Configuration config, List<ResultatCas> resultats, int casTotal, String commit,
                               boolean modifications, String jugeModele, Regression regression) {
        ResultatCas premiere = resultats.stream().filter(r -> r.reponse() != null).findFirst().orElse(null);
        Rapport.Contexte contexte = new Rapport.Contexte(LocalDateTime.now(), commit, modifications, config.baseUrl(),
                premiere == null ? "inconnu" : premiere.reponse().fournisseur(),
                premiere == null ? "inconnu" : premiere.reponse().modele(), jugeModele, config.prix(), casTotal);
        String rapport = Rapport.rendre(contexte, resultats, Metriques.calculer(resultats, config.prix()), regression);
        try {
            Files.writeString(config.rapport(), rapport);
        } catch (IOException e) {
            // fichier ouvert dans un autre programme (verrou Windows), disque plein… : le fichier garde son contenu
            // précédent, l'évaluation continue et la prochaine écriture réessaie
            log.warn("Rapport non réécrit dans {} ({}) : nouvelle tentative après le cas suivant",
                    config.rapport().toAbsolutePath(), e.getMessage());
        }
    }
}
