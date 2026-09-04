package fr.groundedia.examples.hrleave;

import java.util.Arrays;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Point d'entrée du cas d'usage « hr-leave » : questions sur les congés, ancrées sur les documents RH
 * et sur la table {@code salarie}.
 *
 * <p>Le scan des composants couvre tout {@code fr.groundedia} afin que les beans du socle ({@code core})
 * soient détectés sans configuration supplémentaire.
 *
 * <p>Lancée avec {@code --ingest=<dossier>}, l'application exécute la commande d'ingestion des PDF
 * sans démarrer de serveur web, puis s'arrête.
 */
@SpringBootApplication(scanBasePackages = "fr.groundedia")
public class HrLeaveApplication {

    public static void main(String[] args) {
        boolean ingestion = Arrays.stream(args).anyMatch(arg -> arg.startsWith("--ingest"));
        SpringApplication application = new SpringApplication(HrLeaveApplication.class);
        if (ingestion) {
            application.setWebApplicationType(WebApplicationType.NONE);
        }
        ConfigurableApplicationContext contexte = application.run(args);
        if (ingestion) {
            contexte.close();
        }
    }
}
