package fr.groundedia.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import fr.groundedia.eval.Cas.Famille;
import fr.groundedia.eval.Cas.Source;
import fr.groundedia.eval.ReponseAsk.Confiance;
import fr.groundedia.eval.ReponseAsk.Morceau;
import fr.groundedia.eval.ReponseAsk.Tokens;
import fr.groundedia.eval.Verdict.Decision;

/** La décision par famille et la cause probable de chaque échec, avec une application et un juge simulés. */
class EvaluateurTest {

    private static final String REFUS = "Je n'ai pas trouvé cette information dans les documents fournis.";
    private static final Morceau MORCEAU_57 = new Morceau("convention-collective-syntec-avenant-46-2021.pdf",
            "Article 5.7 – Congés pour évènements familiaux", 26);
    private static final String CITATION_57 = "[convention-collective-syntec-avenant-46-2021.pdf, Article 5.7 – Congés pour évènements familiaux, p. 26]";
    private static final Cas MARIAGE = new Cas("A01", Famille.A, "Combien de jours pour un mariage ?", null,
            "Quatre jours ouvrés.", List.of(new Source("syntec", "5.7")), null);
    private static final Cas SOLDE = new Cas("B01", Famille.B, "Ai-je assez de solde ?", 1L, "Non, il manque 2,5 jours.", List.of(), null);
    private static final Cas JAPON = new Cas("C01", Famille.C, "Quelle est la capitale du Japon ?", null, null, List.of(), null);

    /** Une application simulée : renvoie la réponse préparée, ou lève l'erreur préparée. */
    static class ApplicationSimulee extends AskClient {
        ReponseAsk reponse;
        RuntimeException erreur;

        ApplicationSimulee() {
            super("http://localhost:0");
        }

        @Override
        public ReponseAsk demander(String question, Long salarieId) {
            if (erreur != null) {
                throw erreur;
            }
            return reponse;
        }
    }

    static class JugeSimule implements Juge {
        Verdict verdict;
        RuntimeException erreur;
        int appels;

        @Override
        public Verdict juger(String question, String attendue, String obtenue) {
            appels++;
            if (erreur != null) {
                throw erreur;
            }
            return verdict;
        }

        @Override
        public String modele() {
            return "juge-test";
        }
    }

    private final ApplicationSimulee application = new ApplicationSimulee();
    private final JugeSimule juge = new JugeSimule();
    private final Evaluateur evaluateur = new Evaluateur(application, juge);

    private static ReponseAsk reponse(String texte, boolean refus, List<String> citations, List<Morceau> morceaux,
                                      int tokens, boolean couverte) {
        return new ReponseAsk(texte, refus, !refus && citations.isEmpty(), citations, morceaux, 1000,
                new Tokens(tokens, tokens == 0 ? 0 : 50, tokens == 0 ? 0 : tokens + 50), "local", "m",
                new Confiance(couverte ? 0.70 : 0.30, couverte ? 0.50 : 0.17, false, 0.58, 0.40, couverte));
    }

    @Test
    void famille_C_reussit_sur_un_refus_sans_appeler_le_juge() {
        application.reponse = reponse(REFUS, true, List.of(), List.of(), 0, false);

        ResultatCas r = evaluateur.evaluerUn(JAPON);

        assertThat(r.reussi()).isTrue();
        assertThat(r.citationCorrecte()).isNull();
        assertThat(juge.appels).isZero();
    }

    @Test
    void famille_C_echoue_si_le_systeme_repond_et_dit_pourquoi() {
        application.reponse = reponse("Tokyo.", false, List.of(), List.of(), 2000, true);

        ResultatCas r = evaluateur.evaluerUn(JAPON);

        assertThat(r.reussi()).isFalse();
        assertThat(r.cause()).contains("a répondu au lieu de refuser").contains("similarité 0,700").contains("suspecte");
        assertThat(juge.appels).isZero();
    }

    @Test
    void un_refus_a_tort_distingue_la_garde_du_modele_et_le_defaut_de_recherche() {
        application.reponse = reponse(REFUS, true, List.of(), List.of(), 0, false);
        ResultatCas garde = evaluateur.evaluerUn(MARIAGE);
        assertThat(garde.cause()).contains("par la garde").contains("similarité 0,300").contains("n'est pas parmi les morceaux trouvés");
        assertThat(garde.citationCorrecte()).as("pas de citation à juger sur un refus").isNull();

        application.reponse = reponse(REFUS, true, List.of(), List.of(new Morceau("code.pdf", "Article L. 3141-3", 2)), 2000, true);
        assertThat(evaluateur.evaluerUn(MARIAGE).cause()).contains("refus du modèle").contains("n'était pas dans les extraits");

        application.reponse = reponse(REFUS, true, List.of(), List.of(MORCEAU_57), 2000, true);
        assertThat(evaluateur.evaluerUn(MARIAGE).cause()).contains("refus du modèle").contains("était dans les extraits");

        application.reponse = reponse(REFUS, true, List.of(), List.of(), 2000, true);
        assertThat(evaluateur.evaluerUn(SOLDE).cause()).contains("refus du modèle").contains("fiche du salarié");
        assertThat(juge.appels).isZero();
    }

    @Test
    void une_reponse_juste_et_bien_sourcee_reussit() {
        application.reponse = reponse("Quatre jours ouvrés " + CITATION_57 + ".", false, List.of(CITATION_57), List.of(MORCEAU_57), 2000, true);
        juge.verdict = new Verdict(Decision.CORRECT, "tout y est", 100);

        ResultatCas r = evaluateur.evaluerUn(MARIAGE);

        assertThat(r.reussi()).isTrue();
        assertThat(r.citationCorrecte()).isTrue();
        assertThat(r.verdict().decision()).isEqualTo(Decision.CORRECT);
        assertThat(r.nonMesurable()).isFalse();
    }

    @Test
    void une_reponse_juste_mais_mal_sourcee_echoue() {
        String autre = "[code-du-travail-partie-legislative-titre-IV-conges.pdf, Article L. 3142-4, p. 11]";
        application.reponse = reponse("Quatre jours " + autre, false, List.of(autre), List.of(MORCEAU_57), 2000, true);
        juge.verdict = new Verdict(Decision.CORRECT, "ok", 100);

        ResultatCas r = evaluateur.evaluerUn(MARIAGE);

        assertThat(r.reussi()).isFalse();
        assertThat(r.citationCorrecte()).isFalse();
        assertThat(r.cause()).contains("mal sourcée").contains("5.7 (convention Syntec)").contains("L. 3142-4");
    }

    @Test
    void une_reponse_inexacte_porte_la_raison_du_juge_et_le_diagnostic_de_recherche() {
        application.reponse = reponse("Trois jours " + CITATION_57, false, List.of(CITATION_57), List.of(MORCEAU_57), 2000, true);
        juge.verdict = new Verdict(Decision.INCORRECT, "trois au lieu de quatre", 100);

        ResultatCas r = evaluateur.evaluerUn(MARIAGE);

        assertThat(r.reussi()).isFalse();
        assertThat(r.cause()).contains("inexacte").contains("trois au lieu de quatre").contains("était dans les extraits fournis");
    }

    @Test
    void famille_B_sans_source_attendue_ne_juge_que_la_reponse_et_son_ancrage() {
        application.reponse = new ReponseAsk("Non : 12,5 jours (source : base de données RH), il manque 2,5 jours.", false, false,
                List.of(), List.of(), 1000, new Tokens(2000, 50, 2050), "local", "m", new Confiance(0.52, 0.47, false, 0.58, 0.40, true));
        juge.verdict = new Verdict(Decision.CORRECT, "ok", 100);
        assertThat(evaluateur.evaluerUn(SOLDE).reussi()).isTrue();
        assertThat(evaluateur.evaluerUn(SOLDE).citationCorrecte()).isNull();

        // même réponse, mais marquée suspecte par l'application (aucun ancrage) : juste selon le juge, comptée en échec
        application.reponse = reponse("Non, il manque 2,5 jours.", false, List.of(), List.of(), 2000, true);
        ResultatCas suspecte = evaluateur.evaluerUn(SOLDE);
        assertThat(suspecte.reussi()).isFalse();
        assertThat(suspecte.cause()).contains("non ancrée").contains("suspecte");
    }

    @Test
    void les_pannes_du_harnais_sont_des_echecs_non_mesurables_et_n_arretent_pas_la_campagne() {
        application.reponse = reponse("Quatre jours " + CITATION_57, false, List.of(CITATION_57), List.of(MORCEAU_57), 2000, true);
        juge.verdict = new Verdict(Decision.INDETERMINE, "« peut-être »", 100);
        ResultatCas illisible = evaluateur.evaluerUn(MARIAGE);
        assertThat(illisible.reussi()).isFalse();
        assertThat(illisible.nonMesurable()).isTrue();
        assertThat(illisible.cause()).contains("verdict lisible").contains("peut-être");

        juge.erreur = new IllegalStateException("404 modèle absent");
        ResultatCas jugeEnPanne = evaluateur.evaluerUn(MARIAGE);
        assertThat(jugeEnPanne.nonMesurable()).isTrue();
        assertThat(jugeEnPanne.cause()).contains("juge n'a pas pu être consulté").contains("404");
        assertThat(jugeEnPanne.reponse()).as("la réponse de l'application est conservée").isNotNull();

        application.erreur = new IllegalStateException("503 Ollama arrêté");
        ResultatCas applicationEnPanne = evaluateur.evaluerUn(MARIAGE);
        assertThat(applicationEnPanne.nonMesurable()).isTrue();
        assertThat(applicationEnPanne.reussi()).isFalse();
        assertThat(applicationEnPanne.cause()).contains("erreur technique").contains("503");
    }

    @Test
    void evaluer_transmet_les_resultats_acquis_apres_chaque_cas_et_survit_a_leur_traitement() {
        application.reponse = reponse(REFUS, true, List.of(), List.of(), 0, false);
        List<Integer> tailles = new java.util.ArrayList<>();

        List<ResultatCas> resultats = evaluateur.evaluer(List.of(JAPON, JAPON), acquis -> tailles.add(acquis.size()));
        List<ResultatCas> malgreLErreur = evaluateur.evaluer(List.of(JAPON, JAPON), acquis -> {
            throw new IllegalStateException("fichier verrouillé");
        });

        assertThat(resultats).hasSize(2);
        assertThat(tailles).containsExactly(1, 2);
        assertThat(malgreLErreur).as("un rapport intermédiaire impossible n'arrête pas la campagne").hasSize(2);
    }
}
