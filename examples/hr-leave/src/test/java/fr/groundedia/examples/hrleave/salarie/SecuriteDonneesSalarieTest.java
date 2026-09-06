package fr.groundedia.examples.hrleave.salarie;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

import fr.groundedia.core.recherche.Resultat;
import fr.groundedia.core.reponse.ReponseService;
import fr.groundedia.core.reponse.Situation;
import fr.groundedia.core.reponse.Situation.Champ;

/**
 * Règle de sécurité de l'étape 4 : le modèle ne voit QUE les champs du salarié concerné. Jamais un autre salarié,
 * jamais un champ non prévu. Et jamais son nom : aucune règle n'en a besoin.
 */
class SecuriteDonneesSalarieTest {

    private static final Salarie AMINA = new Salarie(1, "Amina Diallo", LocalDate.of(2019, 3, 1), "CDI", "CADRE",
            new BigDecimal("100.00"), new BigDecimal("12.50"), "Syntec");
    private static final Salarie MARC = new Salarie(2, "Marc Petit", LocalDate.of(2024, 9, 15), "CDI", "NON_CADRE",
            new BigDecimal("100.00"), new BigDecimal("4.00"), "Syntec");
    private static final Salarie JULIE = new Salarie(3, "Julie Nguyen", LocalDate.of(2021, 1, 10), "CDD", "NON_CADRE",
            new BigDecimal("80.00"), new BigDecimal("8.50"), "Syntec");
    private static final Salarie SANS_CONVENTION = new Salarie(4, "Test Sans-Convention", LocalDate.of(2020, 6, 1), "CDI",
            "CADRE", new BigDecimal("100.00"), new BigDecimal("0.00"), null);

    /** Un dépôt en mémoire qui connaît les salariés de test. */
    static class DepotDeTest extends SalarieRepository {
        private final Map<Long, Salarie> salaries = Map.of(1L, AMINA, 2L, MARC, 3L, JULIE, 4L, SANS_CONVENTION);

        DepotDeTest() {
            super((JdbcClient) null);
        }

        @Override
        public Optional<Salarie> trouver(long id) {
            return Optional.ofNullable(salaries.get(id));
        }
    }

    private static final Clock LE_5_SEPTEMBRE_2026 = Clock.fixed(Instant.parse("2026-09-05T10:00:00Z"), ZoneId.of("Europe/Paris"));

    private final SituationSalarieService service = new SituationSalarieService(new DepotDeTest(), LE_5_SEPTEMBRE_2026);

    private static final List<Resultat> EXTRAITS = List.of(new Resultat(1, 0.03, "convention.pdf",
            "Article 5.7 – Congés pour évènements familiaux", 26, "Se marier : quatre (4) jours ouvrés."));

    @Test
    void le_prompt_pour_un_salarie_ne_contient_que_ses_donnees_et_aucun_nom() {
        Situation situation = service.situation(1).orElseThrow();
        String prompt = ReponseService.message("Combien de jours pour mon mariage ?", EXTRAITS, situation, "documents");

        assertThat(prompt).contains("SITUATION DU SALARIÉ (source : base de données RH)")
                .contains("- Date d'embauche : 1 mars 2019")
                .contains("- Ancienneté : 7 ans et 6 mois au 5 septembre 2026, soit 7,5 années")
                .contains("- Solde de congés payés : 12,5 jours ouvrés")
                .contains("- Statut : cadre (catégorie « ingénieurs et cadres » de la convention)");
        // aucun nom, ni celui du salarié concerné ni ceux des autres
        assertThat(prompt).doesNotContain("Amina").doesNotContain("Diallo")
                .doesNotContain("Marc").doesNotContain("Petit").doesNotContain("Julie").doesNotContain("Nguyen");
        // rien des deux autres salariés : ni leurs dates, ni leurs valeurs propres
        assertThat(prompt).doesNotContain("15 septembre 2024").doesNotContain("10 janvier 2021")
                .doesNotContain(": 4 jours").doesNotContain("8,5 jours").doesNotContain("80 %").doesNotContain("CDD");
    }

    @Test
    void les_champs_montres_sont_exactement_la_liste_autorisee_ni_plus_ni_moins() {
        for (long id : List.of(1L, 2L, 3L, 4L)) {
            Situation situation = service.situation(id).orElseThrow();

            assertThat(situation.champs()).extracting(Champ::libelle)
                    .containsExactlyElementsOf(SituationSalarieService.CHAMPS_AUTORISES);
            // ni l'identifiant technique ni le nom ne sont des champs montrés
            assertThat(situation.champs()).extracting(Champ::valeur)
                    .noneMatch(v -> v.equals(String.valueOf(id)))
                    .noneMatch(v -> v.contains("Diallo") || v.contains("Petit") || v.contains("Nguyen"));
        }
    }

    @Test
    void la_requete_sql_est_constante_nomme_ses_colonnes_et_ne_lit_que_la_ligne_de_l_identifiant() {
        assertThat(SalarieRepository.COLONNES).isEqualTo(
                "id, nom, date_embauche, type_contrat, statut, temps_travail, solde_conges, convention");
        assertThat(SalarieRepository.REQUETE)
                .isEqualTo("SELECT " + SalarieRepository.COLONNES + " FROM salarie WHERE id = :id")
                .doesNotContain("*");
        // une colonne lue = un composant du record : ajouter une colonne oblige à passer par ici
        assertThat(Salarie.class.getRecordComponents()).hasSize(SalarieRepository.COLONNES.split(",").length);
    }

    @Test
    void un_identifiant_inconnu_ne_donne_aucune_situation() {
        assertThat(service.situation(42)).isEmpty();
    }

    @Test
    void la_situation_de_marc_traduit_son_statut_dans_le_vocabulaire_de_la_convention() {
        Situation situation = service.situation(2).orElseThrow();

        assertThat(situation.champs()).containsExactly(
                new Champ("Date d'embauche", "15 septembre 2024"),
                new Champ("Ancienneté", "1 an et 11 mois au 5 septembre 2026, soit 1,9 année"),
                new Champ("Type de contrat", "CDI"),
                new Champ("Statut", "non-cadre (catégorie « ETAM » de la convention)"),
                new Champ("Temps de travail", "100 % d'un temps plein"),
                new Champ("Solde de congés payés", "4 jours ouvrés"),
                new Champ("Convention collective", "Syntec"));
    }

    @Test
    void une_convention_absente_est_dite_telle_et_le_statut_reste_neutre() {
        Situation situation = service.situation(4).orElseThrow();

        assertThat(situation.champs()).contains(
                new Champ("Statut", "cadre"),
                new Champ("Convention collective", "non renseignée"));
        assertThat(ReponseService.message("q", EXTRAITS, situation, "documents")).doesNotContain("null");
    }
}
