package fr.groundedia.examples.hrleave;

import java.time.Clock;
import java.util.Arrays;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;

/**
 * Point d'entrée du cas d'usage « hr-leave » : questions sur les congés, ancrées sur les documents RH
 * et sur la table {@code salarie}.
 *
 * <p>Le scan des composants couvre tout {@code fr.groundedia} afin que les beans du socle ({@code core})
 * soient détectés sans configuration supplémentaire.
 *
 * <p>Lancée avec une commande ({@code --ingest=<dossier>} ou {@code --embed}), l'application l'exécute
 * sans démarrer de serveur web, puis s'arrête.
 */
@SpringBootApplication(scanBasePackages = "fr.groundedia")
public class HrLeaveApplication {

    public static void main(String[] args) {
        boolean commande = Arrays.stream(args).anyMatch(arg -> arg.startsWith("--ingest") || arg.startsWith("--embed"));
        SpringApplication application = new SpringApplication(HrLeaveApplication.class);
        if (commande) {
            application.setWebApplicationType(WebApplicationType.NONE);
        }
        ConfigurableApplicationContext contexte = application.run(args);
        if (commande) {
            contexte.close();
        }
    }

    /** L'horloge du système : injectée pour que l'ancienneté « à ce jour » soit testable à date fixe. */
    @Bean
    Clock horloge() {
        return Clock.systemDefaultZone();
    }
}
