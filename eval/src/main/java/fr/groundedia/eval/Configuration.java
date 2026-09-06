package fr.groundedia.eval;

import java.nio.file.Path;

import fr.groundedia.eval.Metriques.Prix;

/**
 * Les réglages du harnais, lus dans les propriétés système ({@code -Deval.base-url=…} sur la ligne Maven, transmises
 * par Surefire au JVM des tests), avec des valeurs par défaut pour le poste de développement. Les fichiers sont
 * relatifs au module {@code eval}, où Maven exécute les tests.
 */
public record Configuration(String baseUrl, String jugeBaseUrl, String jugeModele, Prix prix,
                            Path goldenSet, Path regressionSet, Path rapport) {

    public static Configuration depuisProprietes() {
        return new Configuration(
                valeur("eval.base-url", "http://localhost:8081"),
                valeur("eval.juge.base-url", "http://localhost:11434"),
                valeur("eval.juge.model", "qwen2.5:7b"),
                new Prix(nombre("eval.prix.entree-par-million"), nombre("eval.prix.sortie-par-million")),
                Path.of(valeur("eval.golden-set", "golden-set.yaml")),
                Path.of(valeur("eval.regression-set", "regression-set.yaml")),
                Path.of(valeur("eval.rapport", "evaluation.md")));
    }

    private static String valeur(String propriete, String defaut) {
        String p = System.getProperty(propriete);
        return p == null || p.isBlank() ? defaut : p.strip();
    }

    /** Un prix en euros, virgule ou point décimal ; zéro par défaut (modèle local). */
    private static double nombre(String propriete) {
        String p = valeur(propriete, "0").replace(',', '.');
        try {
            return Double.parseDouble(p);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("-D" + propriete + "=" + p + " : un nombre est attendu (euros par million de jetons)", e);
        }
    }
}
