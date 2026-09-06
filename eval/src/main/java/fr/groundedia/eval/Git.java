package fr.groundedia.eval;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/** Le commit évalué, pour dater le rapport ; « inconnu » si git n'est pas disponible. */
final class Git {

    private Git() {
    }

    static String commit() {
        String sortie = executer("git", "rev-parse", "--short", "HEAD");
        return sortie == null || sortie.isBlank() ? "inconnu" : sortie.strip();
    }

    /** Vrai si le dépôt contient des modifications non enregistrées : le rapport ne décrit alors pas exactement le commit. */
    static boolean modificationsLocales() {
        String sortie = executer("git", "status", "--porcelain");
        return sortie != null && !sortie.isBlank();
    }

    private static String executer(String... commande) {
        Process p = null;
        try {
            p = new ProcessBuilder(commande).redirectErrorStream(true).start();
            // la sortie de ces deux commandes tient en quelques lignes : la lire avant d'attendre ne bloque pas
            String sortie = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return p.waitFor(5, TimeUnit.SECONDS) && p.exitValue() == 0 ? sortie : null;
        } catch (IOException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } finally {
            if (p != null && p.isAlive()) {
                p.destroyForcibly();
            }
        }
    }
}
