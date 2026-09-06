package fr.groundedia.eval;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Le verdict du juge : sa décision, sa raison en une phrase, et les jetons qu'il a coûtés. */
public record Verdict(Decision decision, String raison, int tokens) {

    public enum Decision { CORRECT, INCORRECT, INDETERMINE }

    /** « INCORRECT » d'abord : il contient « CORRECT ». Tolère le féminin (« CORRECTE »), guillemets, astérisques, espaces. */
    private static final Pattern DECISION = Pattern.compile("^[\\s*#\"«»'`_.-]*(INCORRECT|CORRECT)E?\\b", Pattern.CASE_INSENSITIVE);

    /** Lit la première ligne utile de la réponse du juge ; tout ce qui ne commence pas par la décision est indéterminé. */
    public static Verdict analyser(String texte, int tokens) {
        String t = texte == null ? "" : texte.strip();
        Matcher m = DECISION.matcher(t);
        if (!m.find()) {
            return new Verdict(Decision.INDETERMINE, "« " + resume(t) + " »", tokens);
        }
        Decision decision = m.group(1).equalsIgnoreCase("INCORRECT") ? Decision.INCORRECT : Decision.CORRECT;
        String reste = t.substring(m.end()).replaceFirst("^[\\s*:.\\-–—»\"]+", "");
        return new Verdict(decision, resume(reste), tokens);
    }

    private static String resume(String s) {
        String uneLigne = s.replaceAll("\\s+", " ").strip();
        return uneLigne.length() > 300 ? uneLigne.substring(0, 297) + "…" : uneLigne;
    }
}
