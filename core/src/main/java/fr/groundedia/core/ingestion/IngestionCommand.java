package fr.groundedia.core.ingestion;

import java.nio.file.Path;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import fr.groundedia.core.ingestion.IngestionService.Resultat;
import fr.groundedia.core.ingestion.IngestionService.Statut;

/**
 * Commande d'ingestion, déclenchée par l'argument {@code --ingest=<dossier>} :
 * {@code ./mvnw spring-boot:run -Dspring-boot.run.arguments=--ingest=documents/}.
 * Sans cet argument, elle ne fait rien et l'application démarre normalement.
 * Si un document n'a pas pu être lu, la commande échoue après avoir traité les autres.
 */
@Component
public class IngestionCommand implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(IngestionCommand.class);

    private final IngestionService ingestion;

    public IngestionCommand(IngestionService ingestion) {
        this.ingestion = ingestion;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!args.containsOption("ingest")) {
            return;
        }
        List<String> valeurs = args.getOptionValues("ingest");
        if (valeurs.isEmpty() || valeurs.getFirst().isBlank()) {
            throw new IllegalArgumentException("Usage : --ingest=<dossier contenant les PDF>");
        }
        Path dossier = Path.of(valeurs.getFirst());
        log.info("Ingestion des PDF de {}", dossier.toAbsolutePath().normalize());

        List<Resultat> resultats = ingestion.ingerer(dossier);
        int ingeres = 0;
        int inchanges = 0;
        int erreurs = 0;
        int morceaux = 0;
        for (Resultat r : resultats) {
            switch (r.statut()) {
                case INGERE -> {
                    ingeres++;
                    morceaux += r.morceaux();
                    log.info("{} : {} pages, {} titres détectés, {} morceaux (taille médiane {} caractères)",
                            r.document(), r.pages(), r.titres(), r.morceaux(), r.tailleMediane());
                }
                case INCHANGE -> {
                    inchanges++;
                    log.info("{} : inchangé, {} morceaux conservés", r.document(), r.morceaux());
                }
                case SANS_TEXTE -> log.warn("{} : aucun texte extrait ({} pages) ; PDF scanné ou vide ?",
                        r.document(), r.pages());
                case ERREUR -> erreurs++;
            }
        }
        if (resultats.isEmpty()) {
            log.warn("Aucun PDF dans {}", dossier.toAbsolutePath().normalize());
        }
        log.info("Ingestion terminée : {} document(s) traité(s), {} ignoré(s) car inchangé(s), {} en erreur, "
                + "{} morceaux insérés", ingeres, inchanges, erreurs, morceaux);
        if (erreurs > 0) {
            throw new IllegalStateException(erreurs + " document(s) n'ont pas pu être lus (voir les erreurs ci-dessus)");
        }
    }
}
