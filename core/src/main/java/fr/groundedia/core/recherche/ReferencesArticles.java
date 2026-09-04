package fr.groundedia.core.recherche;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reconnaît les références d'articles citées dans une question et les traduit en expression régulière PostgreSQL,
 * tolérante à la typographie : « L3141-3 », « L. 3141-3 » et « L 3141-3 » désignent le même article ;
 * « article 5.7 » désigne l'article 5.7 d'une convention.
 *
 * <p>Pourquoi : la recherche plein texte de PostgreSQL découpe « L. 3141-3 » en « 3141 » et « -3 » mais « L3141-3 »
 * en « l3141 » et « -3 » ; une référence exacte doit donc être cherchée telle quelle, indépendamment des espaces
 * et des points.
 */
final class ReferencesArticles {

    /** « L3141-3 », « L. 3141-3 », « R 3142-1 », « D3141-5-1 », en majuscules ou en minuscules. */
    private static final Pattern CODE = Pattern.compile("(?i)\\b([LRD])\\.?\\s?(\\d{3,4}(?:-\\d+)+)\\b");

    /** « article 5.7 », « Article 24 », « art. 12.4 » (numéros simples ou pointés des conventions). */
    private static final Pattern CONVENTION = Pattern.compile("(?i)\\bart(?:icle|\\.)\\s+(\\d+(?:\\.\\d+)*)\\b");

    private ReferencesArticles() {
    }

    /**
     * Une expression régulière (syntaxe PostgreSQL, à utiliser avec {@code ~*}) qui reconnaît toutes les références
     * de la question, ou une chaîne vide s'il n'y en a aucune.
     */
    static String expressionPostgres(String question) {
        List<String> motifs = new ArrayList<>();
        Matcher code = CODE.matcher(question);
        while (code.find()) {
            // \m et \M : début et fin de mot ; le numéro ne doit pas être suivi d'un autre segment « -1 »
            motifs.add("\\m" + code.group(1).toUpperCase() + "\\.?\\s?" + code.group(2) + "(?![-\\d])");
        }
        Matcher convention = CONVENTION.matcher(question);
        while (convention.find()) {
            motifs.add("\\marticle\\s+" + convention.group(1).replace(".", "\\.") + "(?![.\\d])");
        }
        return String.join("|", motifs);
    }
}
