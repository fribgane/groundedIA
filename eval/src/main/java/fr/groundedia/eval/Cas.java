package fr.groundedia.eval;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Un cas d'évaluation du jeu de référence ({@code golden-set.yaml}).
 *
 * <ul>
 *   <li>Famille A : la réponse est dans les documents seuls ; on attend une réponse et au moins une source
 *       (document, article). Plusieurs sources attendues = plusieurs articles également valables (la convention et le
 *       Code disent la même chose) : en citer un suffit.</li>
 *   <li>Famille B : la réponse demande les documents ET la base de données ; on attend une réponse pour un salarié
 *       donné ({@code salarieId}), la source est facultative (une réponse peut venir de la seule base).</li>
 *   <li>Famille C : le système DOIT refuser ; ni réponse ni source attendues.</li>
 * </ul>
 * Le {@code commentaire} (morceau source, nuance, piège) est repris dans le rapport quand le cas échoue.
 */
public record Cas(String id, Famille famille, String question, Long salarieId, String reponseAttendue,
                  List<Source> sourcesAttendues, String commentaire) {

    public Cas {
        sourcesAttendues = sourcesAttendues == null ? List.of() : List.copyOf(sourcesAttendues);
    }

    public boolean attendUneSource() {
        return !sourcesAttendues.isEmpty();
    }

    /** « 5.7 (convention Syntec) ou L. 3142-4 (Code du travail, partie législative) », pour le rapport. */
    public String sourcesAttenduesLisibles() {
        return sourcesAttendues.stream().map(Source::lisible).collect(Collectors.joining(" ou "));
    }

    public enum Famille {
        A("documents seuls"), B("documents et fiche du salarié"), C("refus attendu");

        private final String libelle;

        Famille(String libelle) {
            this.libelle = libelle;
        }

        public String libelle() {
            return libelle;
        }
    }

    /**
     * Une source attendue : une sous-chaîne du nom du document (« syntec », « legislative », « reglementaire ») et le
     * numéro d'article tel qu'il apparaît dans le titre du morceau (« 5.7 », « L. 3142-4 »), comparés sans espaces
     * ni casse.
     */
    public record Source(String document, String article) {

        public String lisible() {
            return document == null || document.isBlank() ? article : article + " (" + nomDocument(document) + ")";
        }

        /** Le nom lisible des documents du corpus de démonstration ; une clé inconnue est rendue telle quelle. */
        static String nomDocument(String cle) {
            String c = cle.toLowerCase();
            if (c.contains("syntec")) {
                return "convention Syntec";
            }
            if (c.contains("legislative")) {
                return "Code du travail, partie législative";
            }
            if (c.contains("reglementaire")) {
                return "Code du travail, partie réglementaire";
            }
            return cle;
        }
    }
}
