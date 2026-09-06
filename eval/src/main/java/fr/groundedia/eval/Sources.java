package fr.groundedia.eval;

import java.text.Normalizer;
import java.util.List;
import java.util.regex.Pattern;

import fr.groundedia.eval.Cas.Source;
import fr.groundedia.eval.ReponseAsk.Morceau;

/**
 * Compare la source attendue d'un cas aux citations renvoyées par l'application (« [document, Article 5.7 – …, p. 26] »)
 * et aux morceaux qu'elle a fournis au modèle. Comparaison sans casse, sans espaces ni accents ; le point qui suit une
 * lettre est ignoré (« L. 3142-4 » = « L3142-4 ») mais celui d'un numéro d'article est gardé (« 1.2 » ≠ « 12 »).
 * L'article attendu « L. 3142-4 » désigne « Article L. 3142-4 » mais ni « L. 3142-45 » ni « L. 3142-4-1 ».
 */
public final class Sources {

    private Sources() {
    }

    /** Au moins une citation désigne l'une des sources attendues. */
    public static boolean citationCorrecte(List<Source> attendues, List<String> citations) {
        return citations != null && attendues.stream().anyMatch(a -> citations.stream().anyMatch(c -> designe(a, c)));
    }

    /** L'un des morceaux attendus figurait parmi ceux fournis au modèle (défaut de recherche ou défaut de rédaction ?). */
    public static boolean fourni(List<Source> attendues, List<Morceau> morceaux) {
        return morceaux != null && attendues.stream().anyMatch(a -> morceaux.stream()
                .anyMatch(m -> designe(a, "[" + m.document() + ", " + m.chapitre() + ", p. " + m.page() + "]")));
    }

    static boolean designe(Source attendue, String citation) {
        String c = compacte(citation);
        String article = compacte(attendue.article());
        if (!article.startsWith("article")) {
            article = "article" + article;
        }
        // ni suivi d'un chiffre (5.7 ≠ 5.75), ni d'un tiret ou d'un point puis un chiffre (L. 3141-5 ≠ L. 3141-5-1) ;
        // le tiret du titre (« Article 5.7 – Congés… ») est suivi d'une lettre et ne gêne pas
        boolean memeArticle = Pattern.compile(Pattern.quote(article) + "(?!\\d|[-.]\\d)").matcher(c).find();
        boolean memeDocument = attendue.document() == null || attendue.document().isBlank()
                || c.contains(compacte(attendue.document()));
        return memeArticle && memeDocument;
    }

    static String compacte(String s) {
        String sansAccents = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sansAccents.toLowerCase().replaceAll("\\s", "").replaceAll("(?<=[a-z])\\.", "").replaceAll("[–—]", "-");
    }
}
