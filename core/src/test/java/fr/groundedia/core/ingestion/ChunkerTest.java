package fr.groundedia.core.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ChunkerTest {

    private static final String DOC = "convention.pdf";

    private final Chunker chunker = new Chunker();

    /** Avec un maximum de 1 caractère et sans chevauchement, chaque phrase devient un morceau : on observe la segmentation. */
    private final Chunker parPhrase = new Chunker(1, 0);

    /** Des phrases numérotées d'environ 130 caractères, pour des tailles prévisibles. */
    private static String phrases(int de, int a) {
        return IntStream.rangeClosed(de, a)
                .mapToObj(i -> "Phrase numéro " + i + " : le salarié bénéficie de droits que la présente convention "
                        + "décrit avec précision et sans aucune ambiguïté possible.")
                .collect(Collectors.joining(" "));
    }

    /** Une phrase d'environ 620 caractères. */
    private static String phraseLongue(int i) {
        return "Phrase longue numéro " + i + " : "
                + "le salarié bénéficie de droits que la présente convention décrit avec précision, ".repeat(7)
                + "sans aucune ambiguïté.";
    }

    // ----- Titres -----

    @ParameterizedTest
    @ValueSource(strings = {
            "Article 24", "Article 1er", "Article premier", "Article 24 bis", "Article 24 : Congés payés", "Article 24-1",
            "Article 4.1.2", "Art. L3141-3", "Article L. 3141-3", "Article R3141-3 (abrogé)", "ARTICLE 12",
            "Chapitre Ier : Congés payés (Articles L3141-1 à L3141-33)", "Chapitre premier : Champ d'application",
            "Chapitre II bis : Don de congés et de jours de repos", "CHAPITRE Ier - CHAMP D'APPLICATION",
            "Section 1 : Droit au congé", "Titre II", "Titre premier", "TITRE Ier : DISPOSITIONS GÉNÉRALES",
            "Sous-section 2 : Durée du congé", "Paragraphe 1 : Ordre public",
            "Paragraphe 1er : Ordre public (Articles L3121-9 à L3121-10)", "Sous-paragraphe 2",
            "Sous-Paragraphe 3 : Dispositions supplétives",
            "Première partie : Les relations individuelles de travail", "Préambule", "PRÉAMBULE",
            "Avenant n° 46 du 16 juillet 2021 relatif à la classification",
            "Accord du 22 juin 1999 relatif à la durée du travail",
            "TITRE II : CONGÉS PAYÉS", "DISPOSITIONS GÉNÉRALES", "SOMMAIRE"})
    void reconnait_les_titres(String ligne) {
        assertThat(Chunker.estUnTitre(ligne)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Le salarié a droit à un congé.", "article L3141-3 du code du travail, les salariés",
            "Article 3 du présent accord prévoit un délai.", "NOTA :", "SMIC", "12", "L3141-33)",
            "IDCC 1486", "CFTC ;", "III. -", "ETAM 1.1 230", "CFDT / F3C",
            "Modifié par Avenant n° 46 du 16 juillet 2021 - art. 3",
            "Titre de séjour et autorisation de travail", "Section syndicale d'entreprise", "En vigueur étendu"})
    void ne_prend_pas_une_phrase_pour_un_titre(String ligne) {
        assertThat(Chunker.estUnTitre(ligne)).isFalse();
    }

    @Test
    void un_numero_d_article_seul_en_tete_de_ligne_devient_un_titre_sans_son_historique() {
        assertThat(Chunker.titre("L. 3141-1 LOI n°2016-1088 du 8 août 2016 - art. 8 (V) - Conseil Constit. 2016-736 DC "
                + "Legif. Plan Jp.Judi. Jp.Admin. Juricaf")).isEqualTo("Article L. 3141-1");
        assertThat(Chunker.titre("D. 3141-1 Décret n°2016-1553 du 18 novembre 2016 - art. 4")).isEqualTo("Article D. 3141-1");
        assertThat(Chunker.titre("R. 3142-1")).isEqualTo("Article R. 3142-1");
        assertThat(Chunker.titre("L3141-3-1")).isEqualTo("Article L3141-3-1");
        // une phrase qui commence par une référence d'article n'est pas un titre
        assertThat(Chunker.titre("L. 3141-1 et suivants ;")).isNull();
        assertThat(Chunker.titre("L. 3141-1 à L. 3141-3 sont applicables.")).isNull();
        assertThat(Chunker.titre("Article 24")).isEqualTo("Article 24");
        assertThat(Chunker.titre("Le salarié a droit à un congé.")).isNull();
    }

    @Test
    void coupe_aux_titres_et_conserve_le_titre_de_chaque_morceau() {
        var pages = List.of(new PageText(1, """
                Article 24
                Le salarié a droit à un congé. Il est pris en accord avec l'employeur.
                Article 25
                Les jours fériés sont chômés.
                """));

        var morceaux = chunker.decouper(DOC, pages);

        assertThat(morceaux).extracting(Chunk::chapitre).containsExactly("Article 24", "Article 25");
        assertThat(morceaux.get(0).texte())
                .isEqualTo("Article 24\nLe salarié a droit à un congé. Il est pris en accord avec l'employeur.");
        assertThat(morceaux.get(1).texte()).isEqualTo("Article 25\nLes jours fériés sont chômés.");
        assertThat(morceaux).allSatisfy(m -> {
            assertThat(m.document()).isEqualTo(DOC);
            assertThat(m.page()).isEqualTo(1);
        });
    }

    @Test
    void deux_articles_de_meme_numero_dans_deux_chapitres_restent_separes() {
        var pages = List.of(new PageText(1, """
                Chapitre I
                Article 1
                Premier texte.
                Chapitre II
                Article 1
                Second texte.
                """));

        var morceaux = chunker.decouper(DOC, pages);

        assertThat(morceaux).extracting(Chunk::texte)
                .containsExactly("Article 1\nPremier texte.", "Article 1\nSecond texte.");
    }

    @Test
    void rattache_au_titre_l_intitule_qui_suit_un_numero_d_article() {
        var pages = List.of(new PageText(1, """
                Article 1.2
                Définition des ETAM, ingénieurs et cadres
                Sont considérés comme ETAM les salariés dont les fonctions sont définies par la grille.
                Article 1.3
                En vigueur étendu
                Cet article est applicable.
                Article 1.4
                Le salarié a droit à un congé.
                Article 7.1
                Généralités
                Le salaire est basé sur la durée légale.
                Article 5.8
                Absences au titre des périodes d'activité accomplies dans la réserve militaire
                Les absences au titre des périodes d'activité accomplies dans la réserve sont autorisées.
                Article 7
                Le salarié a droit à un congé de deux jours et demi ouvrables par mois de travail
                effectif chez le même employeur.
                """));

        var morceaux = chunker.decouper(DOC, pages);

        assertThat(morceaux).extracting(Chunk::chapitre).containsExactly(
                "Article 1.2 – Définition des ETAM, ingénieurs et cadres",
                "Article 1.3",
                "Article 1.4",
                "Article 7.1 – Généralités",
                "Article 5.8 – Absences au titre des périodes d'activité accomplies dans la réserve militaire",
                "Article 7"); // une première ligne de phrase coupée par le retour à la ligne n'est pas un intitulé
        assertThat(morceaux.getLast().texte()).isEqualTo("Article 7\nLe salarié a droit à un congé de deux jours et demi "
                + "ouvrables par mois de travail effectif chez le même employeur.");
        assertThat(morceaux.get(0).texte()).isEqualTo("Article 1.2 – Définition des ETAM, ingénieurs et cadres\n"
                + "Sont considérés comme ETAM les salariés dont les fonctions sont définies par la grille.");
        assertThat(morceaux.get(1).texte()).isEqualTo("Article 1.3\nEn vigueur étendu Cet article est applicable.");
    }

    @Test
    void l_intitule_d_article_ne_contient_ni_chiffre_ni_point() {
        var pages = List.of(new PageText(1, """
                Article 5
                Legif. Plan Jp.Judi. Jp.Admin. Juricaf
                Texte cinq.
                Article 6
                Création LOI n°2008-789 du 20 août 2008 - art. 1
                Texte six.
                """));

        var morceaux = chunker.decouper(DOC, pages);

        assertThat(morceaux).extracting(Chunk::chapitre).containsExactly("Article 5", "Article 6");
    }

    @Test
    void recolle_un_titre_de_structure_coupe_sur_deux_lignes() {
        var pages = List.of(new PageText(1, """
                Sous-section 4 : Congés de formation de cadres et d'animateurs pour la jeunesse, des responsables associatifs
                bénévoles, des titulaires de mandats mutualistes autres qu'administrateurs et des membres des conseils citoyens
                Paragraphe 1 : Ordre public
                L. 3142-54
                Tout salarié a droit à un congé de formation."""));

        var morceaux = chunker.decouper(DOC, pages);

        assertThat(morceaux).singleElement().satisfies(m -> {
            assertThat(m.chapitre()).isEqualTo("Article L. 3142-54");
            assertThat(m.texte()).isEqualTo("Article L. 3142-54\nTout salarié a droit à un congé de formation.");
        });
    }

    @Test
    void recolle_un_titre_en_majuscules_coupe_sur_plusieurs_lignes() {
        var pages = List.of(new PageText(1, """
                TITRE 11 DÉPLACEMENTS ET CHANGEMENTS
                DE RÉSIDENCE EN
                FRANCE
                MÉTROPOLITAINE
                Les déplacements sont remboursés sur justificatifs.
                TITRE 4
                RUPTURE
                DU CONTRAT DE TRAVAIL
                Le contrat peut être rompu.
                TITRE 12 DÉPLACEMENTS HORS DE FRANCE
                Article 12.1
                Un ordre de mission est établi.
                """));

        var morceaux = chunker.decouper(DOC, pages);

        assertThat(morceaux).extracting(Chunk::chapitre).containsExactly(
                "TITRE 11 DÉPLACEMENTS ET CHANGEMENTS DE RÉSIDENCE EN FRANCE MÉTROPOLITAINE",
                "TITRE 4 RUPTURE DU CONTRAT DE TRAVAIL",
                "Article 12.1");
        assertThat(morceaux.get(0).texte()).endsWith("\nLes déplacements sont remboursés sur justificatifs.");
        assertThat(morceaux.get(1).texte()).isEqualTo("TITRE 4 RUPTURE DU CONTRAT DE TRAVAIL\nLe contrat peut être rompu.");
    }

    @Test
    void sans_titre_le_chapitre_vaut_debut_du_document() {
        var morceaux = chunker.decouper(DOC, List.of(new PageText(1, "Texte liminaire sans aucun titre.")));

        assertThat(morceaux).singleElement().satisfies(m -> {
            assertThat(m.chapitre()).isEqualTo(Chunker.SANS_TITRE);
            assertThat(m.page()).isEqualTo(1);
            assertThat(m.texte()).isEqualTo("Texte liminaire sans aucun titre.");
        });
    }

    // ----- Phrases -----

    @Test
    void ne_coupe_jamais_au_milieu_d_une_phrase_et_ne_perd_rien() {
        var morceaux = chunker.decouper(DOC, List.of(new PageText(1, "Article 1\n" + phrases(1, 40))));

        assertThat(morceaux).hasSizeGreaterThan(3);
        assertThat(morceaux).allSatisfy(m -> assertThat(m.texte()).endsWith("."));
        String tout = morceaux.stream().map(Chunk::texte).collect(Collectors.joining(" "));
        for (int i = 1; i <= 40; i++) {
            assertThat(tout).contains("Phrase numéro " + i + " :");
        }
    }

    @Test
    void ne_prend_pas_une_abreviation_ni_une_numerotation_pour_une_fin_de_phrase() {
        var pages = List.of(new PageText(1, "Article 3\n"
                + "Selon l'art. L. 3141-3 du code, cf. p. 12, le congé est dû. Il est pris en une fois. "
                + "1. Les congés annuels. 2. Les congés exceptionnels : 1° le mariage ; 2° la naissance."));

        var morceaux = parPhrase.decouper(DOC, pages);

        assertThat(morceaux).extracting(Chunk::texte).containsExactly(
                "Selon l'art. L. 3141-3 du code, cf. p. 12, le congé est dû.",
                "Il est pris en une fois.",
                "1. Les congés annuels.",
                "2. Les congés exceptionnels : 1° le mariage ;",
                "2° la naissance.");
    }

    @Test
    void coupe_apres_une_reference_d_article_en_fin_de_phrase() {
        var pages = List.of(new PageText(1, "Article 5\n"
                + "Le congé est pris dans les conditions prévues à l'article L. 3141-3. Le salarié part en congé."));

        var morceaux = parPhrase.decouper(DOC, pages);

        assertThat(morceaux).extracting(Chunk::texte).containsExactly(
                "Le congé est pris dans les conditions prévues à l'article L. 3141-3.",
                "Le salarié part en congé.");
    }

    @Test
    void ne_coupe_a_un_point_virgule_qu_entre_les_items_d_une_enumeration() {
        var pages = List.of(new PageText(1, "Article 6\n"
                + "Le salarié peut demander le report ; l'employeur ne peut s'y opposer que pour un motif légitime. "
                + "Sont assimilées : 1° les périodes de congé ; 2° les jours fériés."));

        var morceaux = parPhrase.decouper(DOC, pages);

        assertThat(morceaux).extracting(Chunk::texte).containsExactly(
                "Le salarié peut demander le report ; l'employeur ne peut s'y opposer que pour un motif légitime.",
                "Sont assimilées : 1° les périodes de congé ;",
                "2° les jours fériés.");
    }

    @Test
    void une_phrase_plus_longue_que_le_maximum_forme_son_propre_morceau_sans_etre_coupee() {
        String enorme = "Cette énumération comprend " + "un élément de liste, ".repeat(80) + "et se termine ici.";
        var pages = List.of(new PageText(1, "Article 9\nCourte phrase. " + enorme + " Dernière phrase."));

        var morceaux = chunker.decouper(DOC, pages);

        assertThat(enorme.length()).isGreaterThan(1200);
        assertThat(morceaux).extracting(Chunk::texte).contains(enorme);
        assertThat(morceaux).allSatisfy(m -> assertThat(m.chapitre()).isEqualTo("Article 9"));
    }

    // ----- Taille et chevauchement -----

    @Test
    void vise_une_taille_de_800_a_1200_caracteres() {
        var morceaux = chunker.decouper(DOC, List.of(new PageText(1, "Article 1\n" + phrases(1, 40))));

        var saufLeDernier = morceaux.subList(0, morceaux.size() - 1);
        assertThat(saufLeDernier).allSatisfy(m -> assertThat(m.texte().length()).isBetween(800, 1200));
        assertThat(morceaux.getLast().texte().length()).isLessThanOrEqualTo(1200);
    }

    @Test
    void un_morceau_ne_depasse_jamais_le_maximum_sauf_phrase_seule_trop_longue() {
        String texte = IntStream.rangeClosed(1, 6).mapToObj(ChunkerTest::phraseLongue).collect(Collectors.joining(" "));
        var morceaux = chunker.decouper(DOC, List.of(new PageText(1, "Article 1\n" + texte)));

        assertThat(morceaux).hasSizeGreaterThanOrEqualTo(3);
        assertThat(morceaux).allSatisfy(m -> assertThat(m.texte().length()).isLessThanOrEqualTo(1200));
        for (int i = 1; i <= 6; i++) {
            String phrase = phraseLongue(i);
            assertThat(morceaux).anySatisfy(m -> assertThat(m.texte()).contains(phrase));
        }
    }

    @Test
    void reprend_environ_100_caracteres_du_morceau_precedent_arrondis_au_mot() {
        var morceaux = chunker.decouper(DOC, List.of(new PageText(1, "Article 1\n" + phrases(1, 40))));

        assertThat(morceaux.get(0).texte()).startsWith("Article 1\nPhrase numéro 1 :");
        for (int i = 1; i < morceaux.size(); i++) {
            String precedent = morceaux.get(i - 1).texte();
            String texte = morceaux.get(i).texte();
            assertThat(texte).startsWith("… ");
            String repris = texte.substring(2, texte.indexOf(". Phrase numéro") + 1);
            assertThat(repris.length()).isBetween(100, 130);
            assertThat(precedent).endsWith(repris);
            assertThat(precedent.charAt(precedent.length() - repris.length() - 1))
                    .as("le chevauchement commence à un mot entier").isEqualTo(' ');
        }
    }

    @Test
    void le_chevauchement_ne_franchit_jamais_un_titre() {
        var pages = List.of(new PageText(1, "Article 1\n" + phrases(1, 10) + "\nArticle 2\n" + phrases(11, 12)));

        var morceaux = chunker.decouper(DOC, pages);

        var article2 = morceaux.stream().filter(m -> m.chapitre().equals("Article 2")).toList();
        assertThat(article2).singleElement().satisfies(m -> assertThat(m.texte()).startsWith("Article 2\nPhrase numéro 11"));
    }

    // ----- Pages -----

    @Test
    void une_regle_qui_s_etale_sur_deux_pages_reste_entiere_et_garde_son_titre() {
        var pages = List.of(
                new PageText(1, """
                        Article 7
                        Le salarié a droit à un congé de deux jours et demi ouvrables par mois de travail effectif
                        chez le même"""),
                new PageText(2, """
                        employeur. La durée totale du congé exigible ne peut excéder trente jours ouvrables.
                        Article 8
                        Les jours fériés sont chômés."""));

        var morceaux = chunker.decouper(DOC, pages);

        assertThat(morceaux).extracting(Chunk::chapitre).containsExactly("Article 7", "Article 8");
        assertThat(morceaux.get(0).page()).isEqualTo(1);
        assertThat(morceaux.get(0).texte()).isEqualTo("Article 7\n"
                + "Le salarié a droit à un congé de deux jours et demi ouvrables par mois de travail effectif "
                + "chez le même employeur. La durée totale du congé exigible ne peut excéder trente jours ouvrables.");
        assertThat(morceaux.get(1).page()).isEqualTo(2);
    }

    @Test
    void une_phrase_a_cheval_sur_deux_pages_porte_la_page_ou_elle_commence() {
        var pages = List.of(
                new PageText(1, "Article 7\nPremière phrase. Début de la seconde"),
                new PageText(2, "phrase. Troisième phrase."));

        var morceaux = parPhrase.decouper(DOC, pages);

        assertThat(morceaux).extracting(Chunk::texte)
                .containsExactly("Première phrase.", "Début de la seconde phrase.", "Troisième phrase.");
        assertThat(morceaux).extracting(Chunk::page).containsExactly(1, 1, 2);
    }

    @Test
    void un_article_long_a_cheval_sur_deux_pages_donne_des_morceaux_pagines_correctement() {
        var pages = List.of(
                new PageText(1, "Article 7\n" + phrases(1, 5)), // trop court pour fermer un morceau sur la page 1
                new PageText(2, phrases(6, 12))); // la suite de l'article, sans titre répété

        var morceaux = chunker.decouper(DOC, pages);

        assertThat(morceaux).hasSize(2);
        assertThat(morceaux).extracting(Chunk::chapitre).containsOnly("Article 7");
        assertThat(morceaux.get(0).page()).isEqualTo(1);
        assertThat(morceaux.get(0).texte()).contains("Phrase numéro 6"); // la page est celle où le morceau commence
        assertThat(morceaux.get(1).page()).isEqualTo(2);
        assertThat(morceaux.get(1).texte()).contains("Phrase numéro 12");
    }

    // ----- Nettoyage : en-têtes, pieds de page, sommaires, liens -----

    @Test
    void ignore_les_en_tetes_et_pieds_de_page_repetes_et_les_numeros_de_page() {
        var pages = IntStream.rangeClosed(1, 4)
                .mapToObj(p -> new PageText(p, "Code du travail - Dernière modification le 1 mars 2026\n"
                        + "Article " + p + "\nTexte de l'article " + p + ", qui tient en une phrase.\np." + p))
                .toList();

        var morceaux = chunker.decouper(DOC, pages);

        assertThat(morceaux).hasSize(4);
        for (int p = 1; p <= 4; p++) {
            assertThat(morceaux.get(p - 1).texte())
                    .isEqualTo("Article " + p + "\nTexte de l'article " + p + ", qui tient en une phrase.");
            assertThat(morceaux.get(p - 1).page()).isEqualTo(p);
        }
    }

    @Test
    void filtre_aussi_l_en_tete_et_le_pied_de_page_d_un_document_de_deux_pages() {
        var pages = List.of(
                new PageText(1, """
                        Code du travail - Dernière modification le 1 mars 2026
                        Article L3141-3
                        Le salarié a droit à un congé de deux jours et demi ouvrables par mois de travail effectif chez le même
                        p.1 Code du travail"""),
                new PageText(2, """
                        Code du travail - Dernière modification le 1 mars 2026
                        employeur. La durée totale du congé exigible ne peut excéder trente jours ouvrables.
                        p.2 Code du travail"""));

        var morceaux = chunker.decouper(DOC, pages);

        assertThat(morceaux).singleElement().satisfies(m -> {
            assertThat(m.chapitre()).isEqualTo("Article L3141-3");
            assertThat(m.page()).isEqualTo(1);
            assertThat(m.texte()).isEqualTo("Article L3141-3\nLe salarié a droit à un congé de deux jours et demi "
                    + "ouvrables par mois de travail effectif chez le même employeur. La durée totale du congé exigible "
                    + "ne peut excéder trente jours ouvrables.");
        });
    }

    @Test
    void ignore_un_en_tete_repete_meme_si_son_numero_de_page_change_mais_garde_les_articles() {
        var pages = IntStream.rangeClosed(1, 4)
                .mapToObj(p -> new PageText(p, "BRANCHE DES BUREAUX D'ÉTUDES TECHNIQUES\n"
                        + "Avenant n°46 à la convention collective du 16/12/1987 Page " + p + " sur 4\n"
                        + "Article " + p + "\nTexte de l'article " + p + ", qui tient en une phrase."))
                .toList();

        var morceaux = chunker.decouper(DOC, pages);

        assertThat(morceaux).extracting(Chunk::chapitre).containsExactly("Article 1", "Article 2", "Article 3", "Article 4");
        assertThat(morceaux).extracting(Chunk::texte).allSatisfy(t -> assertThat(t)
                .contains("Texte de l'article").doesNotContain("Avenant").doesNotContain("BRANCHE"));
    }

    @Test
    void une_ligne_repetee_au_coeur_des_pages_n_est_pas_un_en_tete() {
        // « En vigueur étendu » revient sur chaque page, mais jamais dans les 3 premières ni les 3 dernières lignes.
        var pages = IntStream.rangeClosed(1, 4)
                .mapToObj(p -> new PageText(p, "Chapitre " + p + "\nTexte introductif " + p + ".\nDeuxième phrase " + p
                        + ".\nArticle " + p + "\nEn vigueur étendu\nTexte de l'article " + p + ".\nAutre alinéa " + p
                        + ".\nEncore un alinéa " + p + ".\nDernière ligne " + p + "."))
                .toList();

        var morceaux = chunker.decouper(DOC, pages);

        var articles = morceaux.stream().filter(m -> m.chapitre().startsWith("Article")).toList();
        assertThat(articles).hasSize(4);
        assertThat(articles).extracting(Chunk::texte).allSatisfy(t -> assertThat(t).contains("En vigueur étendu"));
    }

    @Test
    void conserve_une_ligne_de_chiffres_au_coeur_de_la_page_mais_pas_le_numero_de_page_en_bas() {
        var pages = List.of(new PageText(1, """
                Article 3
                Première phrase.
                Deuxième phrase.
                Troisième phrase.
                230
                Quatrième phrase.
                Cinquième phrase.
                Sixième phrase.
                12"""));

        var morceaux = chunker.decouper(DOC, pages);

        assertThat(morceaux).singleElement().satisfies(m -> assertThat(m.texte())
                .contains("Troisième phrase. 230 Quatrième phrase.").endsWith("Sixième phrase."));
    }

    @Test
    void ignore_les_lignes_de_sommaire_a_points_de_suite() {
        var pages = List.of(new PageText(1, """
                SOMMAIRE
                PRÉAMBULE ................................................................ 6
                TITRE 4 RUPTURE DU CONTRAT DE TRAVAIL .................................. 18
                PRÉAMBULE
                Les parties signataires conviennent de ce qui suit.
                """));

        var morceaux = chunker.decouper(DOC, pages);

        assertThat(morceaux).singleElement().satisfies(m -> {
            assertThat(m.chapitre()).isEqualTo("PRÉAMBULE");
            assertThat(m.texte()).isEqualTo("PRÉAMBULE\nLes parties signataires conviennent de ce qui suit.");
        });
    }

    @Test
    void ignore_les_liens_leurs_intitules_le_fil_d_ariane_et_le_pied_de_page_numerote() {
        var pages = IntStream.rangeClosed(1, 3)
                .mapToObj(p -> new PageText(p, """
                        Partie législative - Troisième partie : Durée du travail - Livre Ier : Durée du travail, repos et congés
                        Section %d : Droit au congé
                        L. 3141-%d LOI n°2016-1088 du 8 août 2016 - art. 8 (V) Legif. Plan Jp.Judi. Jp.Admin. Juricaf
                        Tout salarié a droit chaque année à un congé payé numéro %d.
                        Dictionnaire du Droit privé
                        > Congés payés
                        service-public.fr
                        > Un salarié peut-il reporter ses jours de congés non pris ? : Absence du salarié
                        p.%d Code du travail""".formatted(p, p, p, 537 + p)))
                .toList();

        var morceaux = chunker.decouper(DOC, pages);

        assertThat(morceaux).extracting(Chunk::chapitre)
                .containsExactly("Article L. 3141-1", "Article L. 3141-2", "Article L. 3141-3");
        assertThat(morceaux).extracting(Chunk::texte).allSatisfy(t -> assertThat(t)
                .contains("Tout salarié a droit")
                .doesNotContain("LOI n°").doesNotContain("Juricaf").doesNotContain("service-public")
                .doesNotContain("reporter").doesNotContain("Code du travail").doesNotContain("Dictionnaire")
                .doesNotContain("Partie législative"));
    }

    @Test
    void une_phrase_terminee_qui_precede_un_lien_n_est_pas_un_intitule_de_liens() {
        var pages = List.of(new PageText(1, """
                L. 3141-2 LOI n°2016-1088 du 8 août 2016 - art. 8 (V)
                Les salariés ont droit à leur congé payé annuel, quelle que soit la période retenue pour
                le personnel de l'entreprise.
                > Un salarié peut-il reporter ses jours de congés non pris ? : Absence du salarié
                L. 3141-3 LOI n°2016-1088 du 8 août 2016 - art. 8 (V)
                Le salarié a droit à un congé de deux jours et demi ouvrables par mois."""));

        var morceaux = chunker.decouper(DOC, pages);

        assertThat(morceaux.get(0).texte()).isEqualTo("Article L. 3141-2\nLes salariés ont droit à leur congé payé "
                + "annuel, quelle que soit la période retenue pour le personnel de l'entreprise.");
    }

    @Test
    void l_intitule_d_un_bloc_de_liens_en_bas_de_page_est_ignore_meme_si_les_liens_sont_page_suivante() {
        var pages = List.of(
                new PageText(1, """
                        L. 3141-1 LOI n°2016-1088 du 8 août 2016 - art. 8 (V)
                        Tout salarié a droit chaque année à un congé payé à la charge de l'employeur.
                        Récemment au Bulletin de la Cour de Cassation"""),
                new PageText(2, """
                        > Chambre sociale, 26 Janvier 2022, n°20-15.755, (B)
                        L. 3141-2 LOI n°2016-1088 du 8 août 2016 - art. 8 (V)
                        Les salariés de retour d'un congé de maternité ont droit à leur congé payé annuel."""));

        var morceaux = chunker.decouper(DOC, pages);

        assertThat(morceaux).extracting(Chunk::texte).containsExactly(
                "Article L. 3141-1\nTout salarié a droit chaque année à un congé payé à la charge de l'employeur.",
                "Article L. 3141-2\nLes salariés de retour d'un congé de maternité ont droit à leur congé payé annuel.");
    }
}
