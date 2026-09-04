package fr.groundedia.core.recherche;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reciprocal Rank Fusion : chaque classement apporte à un morceau 1 / (k + rang), les apports s'additionnent.
 * Un morceau bien classé par les deux recherches passe devant un morceau excellent dans une seule ;
 * k = 60 (valeur de l'article d'origine, Cormack et al. 2009) lisse l'écart entre les premiers rangs.
 */
final class Rrf {

    static final int K = 60;

    private Rrf() {
    }

    static List<Resultat> fusionner(List<List<Resultat>> classements, int limite) {
        Map<Long, Resultat> parId = new LinkedHashMap<>();
        Map<Long, Double> scores = new LinkedHashMap<>();
        for (List<Resultat> classement : classements) {
            for (int rang = 0; rang < classement.size(); rang++) {
                Resultat r = classement.get(rang);
                parId.putIfAbsent(r.id(), r);
                scores.merge(r.id(), 1.0 / (K + rang + 1), Double::sum);
            }
        }
        List<Resultat> fusion = new ArrayList<>();
        scores.entrySet().stream()
                // à score égal (ex. premier d'une seule liste chacun), l'identifiant départage : déterministe et neutre
                .sorted(Map.Entry.<Long, Double>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry::getKey))
                .limit(limite)
                .forEach(e -> fusion.add(parId.get(e.getKey()).avecScore(e.getValue())));
        return fusion;
    }
}
