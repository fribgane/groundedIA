package fr.groundedia.examples.hrleave;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Point d'entrée du cas d'usage « hr-leave » : questions sur les congés, ancrées sur les documents RH
 * et sur la table {@code salarie}.
 *
 * <p>Le scan des composants couvre tout {@code fr.groundedia} afin que les beans du socle ({@code core})
 * soient détectés sans configuration supplémentaire.
 */
@SpringBootApplication(scanBasePackages = "fr.groundedia")
public class HrLeaveApplication {

    public static void main(String[] args) {
        SpringApplication.run(HrLeaveApplication.class, args);
    }
}
