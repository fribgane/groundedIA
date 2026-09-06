package fr.groundedia.eval;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import fr.groundedia.eval.Cas.Famille;

/**
 * Les indicateurs du rapport, calculés depuis les résultats : score par famille et global ; taux de citations correctes
 * (réponses données quand une source était attendue) ; latence médiane et p95 (rang le plus proche) des questions qui
 * ont appelé le modèle, les refus décidés par la garde étant comptés à part ; coût moyen et jetons moyens par question ;
 * réponses suspectes ; cas non mesurables (erreur technique ou juge illisible), comptés en échec.
 */
public record Metriques(Map<Famille, Score> parFamille, Score global, Score citations, int refusSansModele,
                        long latenceMedianeMs, long latenceP95Ms, double coutMoyenEuros, double jetonsMoyens,
                        int suspectes, int nonMesurables) {

    /** Un score : réussis sur total, et le taux (0 quand le total est nul). */
    public record Score(int total, int reussis) {
        public double taux() {
            return total == 0 ? 0 : (double) reussis / total;
        }

        public String pourcentage() {
            return total == 0 ? "—" : Math.round(taux() * 100) + " %";
        }
    }

    /** Le prix d'un million de jetons d'entrée et de sortie, en euros ; zéro pour un modèle local. */
    public record Prix(double entreeParMillion, double sortieParMillion) {
        public double cout(ReponseAsk.Tokens t) {
            return t == null ? 0 : (t.prompt() * entreeParMillion + t.reponse() * sortieParMillion) / 1_000_000;
        }

        public boolean renseigne() {
            return entreeParMillion > 0 || sortieParMillion > 0;
        }
    }

    public Score refus() {
        return parFamille.get(Famille.C);
    }

    public static Metriques calculer(List<ResultatCas> resultats, Prix prix) {
        Map<Famille, Score> parFamille = new EnumMap<>(Famille.class);
        for (Famille f : Famille.values()) {
            List<ResultatCas> deLaFamille = resultats.stream().filter(r -> r.cas().famille() == f).toList();
            parFamille.put(f, new Score(deLaFamille.size(), (int) deLaFamille.stream().filter(ResultatCas::reussi).count()));
        }
        Score global = new Score(resultats.size(), (int) resultats.stream().filter(ResultatCas::reussi).count());
        List<ResultatCas> avecCitation = resultats.stream().filter(r -> r.citationCorrecte() != null).toList();
        Score citations = new Score(avecCitation.size(), (int) avecCitation.stream().filter(r -> r.citationCorrecte()).count());

        List<Long> latences = new ArrayList<>();
        double coutTotal = 0;
        long jetonsTotal = 0;
        int avecReponse = 0;
        int refusSansModele = 0;
        int suspectes = 0;
        for (ResultatCas r : resultats) {
            if (r.reponse() == null) {
                continue;
            }
            avecReponse++;
            coutTotal += prix.cout(r.reponse().tokens());
            if (r.reponse().modeleAppele()) {
                latences.add(r.reponse().latenceMs());
                jetonsTotal += r.reponse().tokens() == null ? 0 : r.reponse().tokens().total();
            } else {
                refusSansModele++;
            }
            if (r.reponse().suspecte()) {
                suspectes++;
            }
        }
        int nonMesurables = (int) resultats.stream().filter(ResultatCas::nonMesurable).count();
        return new Metriques(parFamille, global, citations, refusSansModele, mediane(latences), percentile(latences, 95),
                avecReponse == 0 ? 0 : coutTotal / avecReponse,
                latences.isEmpty() ? 0 : (double) jetonsTotal / latences.size(), suspectes, nonMesurables);
    }

    static long mediane(List<Long> valeurs) {
        if (valeurs.isEmpty()) {
            return 0;
        }
        List<Long> triees = new ArrayList<>(valeurs);
        Collections.sort(triees);
        int n = triees.size();
        return n % 2 == 1 ? triees.get(n / 2) : Math.round((triees.get(n / 2 - 1) + triees.get(n / 2)) / 2.0);
    }

    /** Percentile au rang le plus proche : la valeur sous laquelle se trouvent p % des mesures. */
    static long percentile(List<Long> valeurs, int p) {
        if (valeurs.isEmpty()) {
            return 0;
        }
        List<Long> triees = new ArrayList<>(valeurs);
        Collections.sort(triees);
        int rang = (int) Math.ceil(p / 100.0 * triees.size());
        return triees.get(Math.max(0, Math.min(triees.size(), rang) - 1));
    }
}
