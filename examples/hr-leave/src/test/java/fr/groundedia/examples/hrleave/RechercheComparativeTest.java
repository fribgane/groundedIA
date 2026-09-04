package fr.groundedia.examples.hrleave;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import fr.groundedia.core.ingestion.ChunkRepository;
import fr.groundedia.core.recherche.Mode;
import fr.groundedia.core.recherche.RechercheService;
import fr.groundedia.core.recherche.Resultat;

/**
 * Compare côte à côte les trois modes de recherche sur dix questions, cinq en langage naturel et cinq avec une
 * référence d'article exacte, puis vérifie que le mode hybride trouve le bon morceau dans le top 3 plus souvent
 * que chacun des deux modes pris isolément.
 *
 * <p>Les questions ont été fixées avant la mesure, selon deux règles : une question en langage naturel par thème
 * du Titre IV (événement familial, fixation des dates, acquisition pendant la maladie, fin de contrat, durée
 * maximale) ; pour les références, typographie compacte (« L3141-3 ») et typographie du corpus (« L. 3142-105 »)
 * en alternance. Un « bon morceau » porte le titre attendu et, quand l'article est long, contient le mot-clé
 * attendu : c'est le morceau qui répond, pas seulement l'article.
 *
 * <p>Test d'intégration : il a besoin de la base (morceaux ingérés et embeddings calculés) et d'Ollama.
 * Lancement : {@code ./mvnw test -pl examples/hr-leave -am -Dcomparatif=true}
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnabledIfSystemProperty(named = "comparatif", matches = "true")
class RechercheComparativeTest {

    /** Une question, sa famille, les titres (fragments) acceptés et, pour un article long, le mot-clé attendu. */
    record Question(String famille, String texte, List<String> attendus, String extrait) {
    }

    static final String LANGAGE_NATUREL = "Langage naturel";
    static final String REFERENCE_EXACTE = "Référence exacte";

    static final List<Question> QUESTIONS = List.of(
            new Question(LANGAGE_NATUREL, "Combien de jours de congé pour me marier ?",
                    List.of("L. 3142-4", "L. 3142-1", "Article 5.7"), "mari"),
            new Question(LANGAGE_NATUREL, "Mon employeur peut-il m'imposer mes dates de vacances ?",
                    List.of("L. 3141-16", "L. 3141-15", "Article 5.4"), null),
            new Question(LANGAGE_NATUREL, "Est-ce que je continue à acquérir des congés pendant un arrêt maladie ?",
                    List.of("L. 3141-5", "L. 3141-5-1", "Article 5.5"), "maladie"),
            new Question(LANGAGE_NATUREL, "Que devient mon solde de congés si je quitte l'entreprise ?",
                    List.of("L. 3141-28"), null),
            new Question(LANGAGE_NATUREL, "Puis-je prendre cinq semaines de vacances d'affilée ?",
                    List.of("L. 3141-17"), null),
            new Question(REFERENCE_EXACTE, "Que dit l'article L3141-3 ?", List.of("L. 3141-3"), null),
            new Question(REFERENCE_EXACTE, "Que prévoit l'article L. 3142-105 du code du travail ?",
                    List.of("L. 3142-105"), null),
            new Question(REFERENCE_EXACTE, "Que prévoit l'article 5.7 de la convention Syntec ?",
                    List.of("Article 5.7"), null),
            new Question(REFERENCE_EXACTE, "Contenu de l'article D3141-5", List.of("D. 3141-5"), null),
            new Question(REFERENCE_EXACTE, "Texte de l'article R. 3142-1", List.of("R. 3142-1"), null));

    /** Un bon morceau doit être dans les TOP premiers ; chaque mode est interrogé plus profond pour expliquer les rangs. */
    static final int TOP = 3;
    static final int PROFONDEUR = 20;

    @Autowired
    RechercheService recherche;

    @Autowired
    ChunkRepository chunks;

    @Value("${ollama.embedding-model}")
    String modele;

    @Test
    void le_mode_hybride_trouve_le_bon_morceau_dans_le_top_3_plus_souvent_que_chaque_mode_seul() {
        assertThat(chunks.compterSansEmbedding()).as("morceaux sans embedding : lancer --embed d'abord").isZero();

        Map<Mode, Integer> reussitesTotales = new EnumMap<>(Mode.class);
        Map<String, Map<Mode, Integer>> reussitesParFamille = new LinkedHashMap<>();
        StringBuilder rapport = new StringBuilder("\n\n=== Comparaison des trois modes de recherche ===\n");
        rapport.append(String.format("modele d'embedding %s - RRF k=60 - candidats fusionnes : %d vectoriels + %d plein texte"
                        + " - recherche vectorielle exacte (sans index approche) - bon morceau = dans le top %d%n",
                modele, recherche.candidatsVectoriels(), recherche.candidatsPleinTexte(), TOP));

        int numero = 0;
        for (Question q : QUESTIONS) {
            numero++;
            rapport.append(String.format("%nQ%-2d [%s] %s%n     attendu : %s%s%n", numero, q.famille(), q.texte(),
                    String.join(" | ", q.attendus()), q.extrait() == null ? "" : "  (contenant « " + q.extrait() + " »)"));
            Map<Mode, List<Resultat>> parMode = new EnumMap<>(Mode.class);
            for (Mode mode : Mode.values()) {
                parMode.put(mode, recherche.rechercher(q.texte(), mode, PROFONDEUR));
            }
            for (Mode mode : Mode.values()) {
                List<Resultat> resultats = parMode.get(mode);
                int rang = rang(resultats, q);
                boolean reussi = rang > 0 && rang <= TOP;
                if (reussi) {
                    reussitesTotales.merge(mode, 1, Integer::sum);
                    reussitesParFamille.computeIfAbsent(q.famille(), f -> new EnumMap<>(Mode.class))
                            .merge(mode, 1, Integer::sum);
                }
                String detail = mode == Mode.HYBRID
                        ? String.format(" (rang vectoriel %s, plein texte %s)",
                                rangTexte(rang(parMode.get(Mode.VECTOR), q)), rangTexte(rang(parMode.get(Mode.FULLTEXT), q)))
                        : "";
                // marqueurs ASCII : la console Windows n'affiche pas toujours les symboles Unicode
                rapport.append(String.format("     %-8s : rang %-5s %s%s%n         top %d : %s%n",
                        mode.cle(), rangTexte(rang) + "/" + PROFONDEUR, reussi ? "[OK]" : "[--]", detail, TOP,
                        resultats.stream().limit(TOP)
                                .map(r -> String.format("%s (p.%d, %.4f)", r.chapitre(), r.page(), r.score()))
                                .collect(Collectors.joining(" | "))));
            }
        }

        rapport.append("\n=== Bon morceau dans le top 3 ===\n");
        rapport.append(String.format("%-26s | %6s | %8s | %6s%n", "", "vector", "fulltext", "hybrid"));
        for (String famille : List.of(LANGAGE_NATUREL, REFERENCE_EXACTE)) {
            long total = QUESTIONS.stream().filter(q -> q.famille().equals(famille)).count();
            Map<Mode, Integer> r = reussitesParFamille.getOrDefault(famille, Map.of());
            rapport.append(String.format("%-26s | %6d | %8d | %6d%n", famille + " (" + total + ")",
                    r.getOrDefault(Mode.VECTOR, 0), r.getOrDefault(Mode.FULLTEXT, 0), r.getOrDefault(Mode.HYBRID, 0)));
        }
        rapport.append(String.format("%-26s | %6d | %8d | %6d%n", "Total (" + QUESTIONS.size() + ")",
                reussitesTotales.getOrDefault(Mode.VECTOR, 0), reussitesTotales.getOrDefault(Mode.FULLTEXT, 0),
                reussitesTotales.getOrDefault(Mode.HYBRID, 0)));
        System.out.println(rapport);

        int hybride = reussitesTotales.getOrDefault(Mode.HYBRID, 0);
        assertThat(hybride).as("hybride > vectoriel").isGreaterThan(reussitesTotales.getOrDefault(Mode.VECTOR, 0));
        assertThat(hybride).as("hybride > plein texte").isGreaterThan(reussitesTotales.getOrDefault(Mode.FULLTEXT, 0));
    }

    /** Rang (à partir de 1) du premier bon morceau, ou 0 s'il n'est pas dans la liste. */
    static int rang(List<Resultat> resultats, Question q) {
        for (int i = 0; i < resultats.size(); i++) {
            if (estUnBonMorceau(resultats.get(i), q)) {
                return i + 1;
            }
        }
        return 0;
    }

    static boolean estUnBonMorceau(Resultat r, Question q) {
        // la référence attendue ne doit pas être suivie d'un autre segment : « L. 3141-5 » ≠ « L. 3141-5-1 »
        boolean titre = q.attendus().stream()
                .anyMatch(a -> Pattern.compile(Pattern.quote(a) + "(?![-.\\d])").matcher(r.chapitre()).find());
        return titre && (q.extrait() == null || r.texte().toLowerCase().contains(q.extrait()));
    }

    static String rangTexte(int rang) {
        return rang > 0 ? String.valueOf(rang) : "-";
    }
}
