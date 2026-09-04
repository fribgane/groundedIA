package fr.groundedia.core.ingestion;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Découpe le texte d'un document, page par page, en morceaux prêts à être cités.
 *
 * <p>Règles, par ordre de priorité :
 * <ol>
 *   <li>on coupe aux titres (articles, chapitres, sections, lignes en majuscules) : un morceau ne mélange jamais
 *       deux titres, et le chevauchement ne franchit jamais un titre ;</li>
 *   <li>on ne coupe jamais au milieu d'une phrase : les unités insécables sont les phrases (« . ! ? … ») et les
 *       alinéas d'énumération (« 1° … ; 2° … ») ;</li>
 *   <li>un morceau ne dépasse jamais 1200 caractères, sauf si une seule phrase est plus longue : on remplit chaque
 *       morceau au plus près du maximum, ce qui donne des morceaux de 800 à 1200 caractères dans les sections longues ;
 *       un article court forme un morceau court ;</li>
 *   <li>chevauchement de 100 caractères (arrondi au mot entier, précédé de « … ») entre deux morceaux consécutifs
 *       d'un même titre.</li>
 * </ol>
 * Chaque morceau conserve le document, le titre en vigueur (chapitre ou article) et la page où commence son texte.
 *
 * <p>Nettoyage, appris sur des PDF réels : les en-têtes et pieds de page (lignes d'extrémité de page répétées, numéro
 * de page neutralisé), les numéros de page isolés, les lignes de sommaire à points de suite, les lignes de liens
 * (« > … », « service-public.fr ») et leur intitulé sont ignorés.
 */
public final class Chunker {

    /**
     * Version des règles de découpage. Elle entre dans l'empreinte enregistrée pour chaque document : l'incrémenter
     * après une modification des règles force la ré-ingestion des documents, même inchangés.
     */
    public static final int VERSION = 5;

    /** Titre attribué aux morceaux qui précèdent le premier titre détecté. */
    public static final String SANS_TITRE = "Début du document";

    /** « Article 24 », « Article 1er », « Article premier », « Art. L3141-3 », « Article L. 3141-3 », « Article 24 : Congés payés ». */
    private static final Pattern ARTICLE = Pattern.compile(
            "^(?:Article|ARTICLE|Art\\.)\\s+(?:(?:[LRD]\\.?\\s*)?\\d+(?:[.\\-]\\d+)*"
            + "(?:\\s?(?:er|ère|bis|ter|quater|quinquies|sexies))?|premier|première)\\.?(?:\\s*[:\\-–—(].*)?$");

    /**
     * Titres de structure : « Chapitre Ier : Congés payés », « TITRE II », « Section 1 », « Sous-section 2 »,
     * « Paragraphe 1 », « Titre premier », « Première partie : … », « Préambule », « Avenant n° 46 du 16 juillet 2021 … ».
     */
    private static final Pattern STRUCTURE = Pattern.compile(
            "^(?:(?:Titre|TITRE|Chapitre|CHAPITRE|Section|SECTION|Sous-[Ss]ection|SOUS-SECTION|Paragraphe|PARAGRAPHE"
            + "|Sous-[Pp]aragraphe|SOUS-PARAGRAPHE|Livre|LIVRE|Partie|PARTIE|Annexe|ANNEXE)"
            + "(?:\\s+(?:[IVXLC]+|\\d+)(?:er|ère|re|e)?(?:\\s+(?:bis|ter|quater))?"
            + "|\\s+premier|\\s+première|\\s+unique|\\s+préliminaire|\\s+législative|\\s+réglementaire)?"
            + "|(?:Première|Deuxième|Troisième|Quatrième|Cinquième|Sixième|Septième|Huitième|Neuvième)\\s+partie"
            + "|(?:Avenant|Accord)(?:\\s+n[°º]\\s*\\d+[\\w\\-]*)?\\s+du\\s+\\d{1,2}(?:er)?\\s+\\p{L}+\\s+\\d{4}\\b.*"
            + "|Préambule|PRÉAMBULE)\\.?(?:\\s*[:\\-–—(].*)?$");

    /**
     * Numéro d'article seul en tête de ligne, éventuellement suivi de son historique (codes compilés, ex. codes.droit.org :
     * « L. 3141-1  LOI n°2016-1088 du 8 août 2016 - art. 8 (V) … »). Une suite en minuscules (« L. 3141-1 et suivants »)
     * est une phrase, pas un titre.
     */
    private static final Pattern NUMERO_D_ARTICLE_SEUL = Pattern.compile(
            "^([LRD]\\.?\\s?\\d{3,4}-\\d+(?:-\\d+)*)(?:\\s+(?=[A-ZÀ-Ü0-9(]).*)?$");

    /** Début d'un titre de structure en majuscules : une ligne de ce type n'est jamais la suite du titre précédent. */
    private static final Pattern STRUCTURE_EN_MAJUSCULES = Pattern.compile(
            "^(?:TITRE|CHAPITRE|SECTION|SOUS-SECTION|PARAGRAPHE|ARTICLE|ANNEXE|PARTIE|LIVRE|PRÉAMBULE|AVENANT)\\b.*");

    /** Métadonnées Légifrance placées sous un titre d'article : jamais prises pour l'intitulé de l'article. */
    private static final Pattern METADONNEE = Pattern.compile(
            "^(?:En vigueur|Non en vigueur|Modifié par|Créé par|Création|Abrogé par|Remplacé par|Étendu par|Version|NOTA)\\b.*");

    /** Numéros de page isolés : « 12 », « p. 12 », « Page 12 sur 300 », « - 12 - ». */
    private static final Pattern MARQUE_DE_PAGE = Pattern.compile(
            "^(?:(?:page|p)\\.?\\s*)?[-–—\\s]*\\d{1,4}[-–—\\s]*(?:(?:/|sur)\\s*\\d{1,4})?$", Pattern.CASE_INSENSITIVE);

    /** « Page 10 sur 60 », « p.538 », nombre en début ou en fin de ligne : la partie variable d'un en-tête. */
    private static final Pattern NUMERO_DANS_L_EN_TETE = Pattern.compile(
            "(?i)\\bpage\\s*\\d+(?:\\s*(?:sur|/)\\s*\\d+)?|\\bp\\.\\s?\\d+\\b|^\\d+\\b|\\b\\d+$");

    /** Lignes de liens ou de navigation (« > Un salarié peut-il… », « service-public.fr ») : pas du contenu. */
    private static final Pattern LIEN = Pattern.compile("^(?:>\\s.*|[a-z0-9-]+(?:\\.[a-z0-9-]+)+)$");

    /** Ligne de sommaire, avec points de suite et numéro de page : « TITRE 4 RUPTURE DU CONTRAT ........ 18 ». */
    private static final Pattern SOMMAIRE = Pattern.compile(".*\\.{4,}\\s*\\d+\\s*$");

    /**
     * Fin de phrase : « . ! ? … » (éventuellement suivi d'une fermeture « ) » ou « » »), puis un blanc, puis une
     * majuscule, un chiffre ou une ouverture (guillemet, parenthèse, tiret, puce) ; en excluant les abréviations des
     * textes juridiques (« L. », « art. », « al. », « p. ») et la numérotation de liste « 1. » (en début de phrase).
     * Ou fin d'alinéa d'énumération : « ; » suivi d'un marqueur d'item (« 2° », « b) », « - »).
     */
    private static final Pattern FIN_DE_PHRASE = Pattern.compile(
            "(?:(?<=[.!?…][)»\"']?)(?<!\\b[A-Za-z]\\.)(?<!\\b(?:art|Art|al|cf|ex|chap|sect|p|pp)\\.)"
            + "(?<![.;:!?…]\\s\\d{1,2}\\.)(?<!^\\d{1,2}\\.)"
            + "\\s+(?=[A-ZÀ-ÖØ-Þ0-9«\"(\\[\\-–—•§])"
            + "|(?<=;)\\s+(?=\\d+°|[a-z]\\)|[-–—•]\\s))");

    /** Nombre de lignes, en haut et en bas de chaque page, où peuvent se trouver en-têtes et pieds de page. */
    private static final int LIGNES_D_EXTREMITE = 3;

    private final int tailleMax;
    private final int chevauchement;

    public Chunker() {
        this(1200, 100);
    }

    public Chunker(int tailleMax, int chevauchement) {
        this.tailleMax = tailleMax;
        this.chevauchement = chevauchement;
    }

    public List<Chunk> decouper(String document, List<PageText> pages) {
        return assembler(document, segmenter(pages));
    }

    /** Une unité insécable, titre ou phrase, avec la page où elle commence et le titre en vigueur. */
    private record Unite(String titre, int page, String texte, boolean estTitre) {
    }

    /** Une ligne normalisée et la page dont elle vient. */
    private record Ligne(int page, String texte) {
    }

    // ----- 1. Segmentation : lignes -> titres et phrases, avec leur provenance -----

    private List<Unite> segmenter(List<PageText> pages) {
        List<Ligne> lignes = lignesUtiles(pages);
        List<Unite> unites = new ArrayList<>();
        String titre = SANS_TITRE;
        StringBuilder tampon = new StringBuilder();
        int pageTampon = 0;
        for (int i = 0; i < lignes.size(); i++) {
            Ligne ligne = lignes.get(i);
            Ligne suivante = i + 1 < lignes.size() ? lignes.get(i + 1) : null;
            if (LIEN.matcher(ligne.texte()).matches()
                    || (suivante != null && estIntituleDeLiens(ligne.texte(), suivante.texte()))) {
                continue; // lien, ou intitulé d'un bloc de liens (« Dictionnaire du Droit privé »), même en bas de page
            }
            String nouveauTitre = titre(ligne.texte());
            if (nouveauTitre != null) {
                vider(unites, titre, tampon, pageTampon);
                if (ARTICLE.matcher(nouveauTitre).matches() && suivante != null && estUnIntitule(suivante.texte())) {
                    nouveauTitre = nouveauTitre + " – " + suivante.texte();
                    i++;
                } else if (STRUCTURE.matcher(nouveauTitre).matches() && suivante != null
                        && estSuiteDeTitre(suivante.texte()) && nouveauTitre.length() + suivante.texte().length() < 250) {
                    // un titre de structure coupé sur deux lignes est recollé (« Sous-section 4 : Congés de … » / « bénévoles … »)
                    nouveauTitre = nouveauTitre + " " + suivante.texte();
                    i++;
                } else if (sansMinuscule(ligne.texte())) {
                    // un titre en majuscules coupé sur plusieurs lignes est recollé (« TITRE 4 » / « RUPTURE » / « DU CONTRAT… »)
                    while (i + 1 < lignes.size() && estSuiteDeTitreEnMajuscules(lignes.get(i + 1).texte())
                            && nouveauTitre.length() + lignes.get(i + 1).texte().length() < 150) {
                        nouveauTitre = nouveauTitre + " " + lignes.get(i + 1).texte();
                        i++;
                    }
                }
                titre = nouveauTitre;
                unites.add(new Unite(titre, ligne.page(), titre, true));
                continue;
            }
            if (tampon.isEmpty()) {
                pageTampon = ligne.page();
            } else {
                tampon.append(' ');
            }
            tampon.append(ligne.texte());
            pageTampon = extrairePhrases(unites, titre, tampon, pageTampon, ligne.page());
        }
        vider(unites, titre, tampon, pageTampon);
        return unites;
    }

    /**
     * Les lignes de toutes les pages, normalisées, sans les vides ni les sommaires, et sans les marques de page,
     * en-têtes et pieds de page situés aux extrémités des pages.
     */
    private static List<Ligne> lignesUtiles(List<PageText> pages) {
        Set<String> enTetesEtPieds = lignesRepetees(pages);
        List<Ligne> utiles = new ArrayList<>();
        for (PageText page : pages) {
            String[] lignes = page.texte().split("\\R");
            Set<Integer> extremites = extremites(lignes);
            for (int i = 0; i < lignes.length; i++) {
                String ligne = normaliser(lignes[i]);
                if (ligne.isEmpty() || SOMMAIRE.matcher(ligne).matches()
                        || (extremites.contains(i)
                            && (MARQUE_DE_PAGE.matcher(ligne).matches() || estRepetee(ligne, enTetesEtPieds)))) {
                    continue;
                }
                utiles.add(new Ligne(page.numero(), ligne));
            }
        }
        return utiles;
    }

    /** Sort du tampon les phrases complètes ; retourne la page du texte qui y reste. */
    private static int extrairePhrases(List<Unite> unites, String titre, StringBuilder tampon, int pageTampon,
                                       int pageCourante) {
        Matcher fin = FIN_DE_PHRASE.matcher(tampon);
        int debut = 0;
        int page = pageTampon;
        while (fin.find()) {
            String phrase = tampon.substring(debut, fin.start()).strip();
            if (!phrase.isEmpty()) {
                unites.add(new Unite(titre, page, phrase, false));
            }
            debut = fin.end();
            page = pageCourante;
        }
        tampon.delete(0, debut);
        return page;
    }

    /** Ce qui reste dans le tampon (fin de section ou de document) devient une unité, même sans point final. */
    private static void vider(List<Unite> unites, String titre, StringBuilder tampon, int page) {
        String reste = tampon.toString().strip();
        if (!reste.isEmpty()) {
            unites.add(new Unite(titre, page, reste, false));
        }
        tampon.setLength(0);
    }

    static String normaliser(String ligne) {
        return ligne.replaceAll("[\\s\\p{Zs}]+", " ").strip();
    }

    /** Le titre que porte cette ligne, ou {@code null} si ce n'est pas un titre. */
    static String titre(String ligne) {
        if (ligne.length() > 200) {
            return null;
        }
        Matcher numero = NUMERO_D_ARTICLE_SEUL.matcher(ligne);
        if (numero.matches()) {
            return "Article " + numero.group(1); // l'historique et les liens qui suivent ne sont pas du contenu
        }
        if (ARTICLE.matcher(ligne).matches() || STRUCTURE.matcher(ligne).matches()) {
            return ligne;
        }
        return ligne.length() <= 150 && estEnMajuscules(ligne) ? ligne : null;
    }

    static boolean estUnTitre(String ligne) {
        return titre(ligne) != null;
    }

    /** Débuts de phrase (article, pronom, conjonction) : un intitulé est un groupe nominal, il ne commence pas ainsi. */
    private static final Pattern DEBUT_DE_PHRASE = Pattern.compile(
            "^(?:Le|La|Les|L['’]\\S*|Un|Une|Des|Tout|Toute|Tous|Toutes|Il|Ils|Elle|Elles|On|Ce|Cet|Cette|Ces|Sont|Est"
            + "|Lorsque|Lorsqu['’]\\S*|En|Dans|Pour|Par|Si|Au|Aux|À|A|Sauf|Sous|Après|Avant|Nul|Aucun|Aucune|Chaque"
            + "|Selon|Conformément|Toutefois|Cependant|Néanmoins|Ainsi|Afin)\\b.*");

    /**
     * Sous « Article 1.2 », une ligne courte, sans ponctuation finale, sans chiffre ni point, commençant par une
     * majuscule mais pas comme une phrase, est l'intitulé de l'article (« Définition des ETAM ») : on le rattache au
     * titre pour que la citation soit parlante.
     */
    static boolean estUnIntitule(String ligne) {
        return !ligne.isEmpty() && ligne.length() <= 90
                && Character.isUpperCase(ligne.charAt(0))
                && !ligne.matches(".*[.;:,!?]$") && !ligne.matches(".*[0-9°.].*")
                && !DEBUT_DE_PHRASE.matcher(ligne).matches()
                && !ARTICLE.matcher(ligne).matches() && !STRUCTURE.matcher(ligne).matches()
                && !METADONNEE.matcher(ligne).matches();
    }

    /** L'intitulé d'un bloc de liens : une ligne courte sans ponctuation finale, suivie d'un lien. */
    private static boolean estIntituleDeLiens(String ligne, String suivante) {
        return LIEN.matcher(suivante).matches() && ligne.length() <= 80 && !ligne.matches(".*[.;:!?…]$");
    }

    /** La suite d'un titre coupé par le retour à la ligne : commence par une minuscule, ne termine pas une phrase. */
    private static boolean estSuiteDeTitre(String ligne) {
        return !ligne.isEmpty() && Character.isLowerCase(ligne.charAt(0)) && !ligne.matches(".*[.;:!?…]$");
    }

    /** La suite d'un titre en majuscules : une ligne sans minuscule (même un seul mot), qui n'est pas elle-même un titre numéroté. */
    private static boolean estSuiteDeTitreEnMajuscules(String ligne) {
        return sansMinuscule(ligne) && compterLettres(ligne) >= 3
                && !STRUCTURE_EN_MAJUSCULES.matcher(ligne).matches()
                && !ARTICLE.matcher(ligne).matches() && !NUMERO_D_ARTICLE_SEUL.matcher(ligne).matches();
    }

    /** Une ligne en majuscules d'au moins huit lettres, plus de lettres que de chiffres (« PRÉAMBULE », « DISPOSITIONS GÉNÉRALES »). */
    private static boolean estEnMajuscules(String ligne) {
        int lettres = compterLettres(ligne);
        int chiffres = (int) ligne.chars().filter(Character::isDigit).count();
        return sansMinuscule(ligne) && lettres >= 8 && lettres > chiffres;
    }

    private static boolean sansMinuscule(String ligne) {
        return ligne.chars().noneMatch(Character::isLowerCase);
    }

    private static int compterLettres(String ligne) {
        return (int) ligne.chars().filter(Character::isLetter).count();
    }

    /**
     * Gabarits des lignes d'extrémité de page présents sur au moins 20 % des pages (et au moins 3 pages, ou les 2 pages
     * d'un document de 2 pages) : en-têtes et pieds de page. Le numéro de page est neutralisé pour reconnaître
     * « Avenant n° 46 … Page 10 sur 60 » d'une page à l'autre.
     */
    private static Set<String> lignesRepetees(List<PageText> pages) {
        if (pages.size() < 2) {
            return Set.of();
        }
        Map<String, Integer> pagesParGabarit = new HashMap<>();
        for (PageText page : pages) {
            String[] lignes = page.texte().split("\\R");
            Set<String> vus = new HashSet<>();
            for (int i : extremites(lignes)) {
                String gabarit = gabarit(normaliser(lignes[i]));
                if (vus.add(gabarit)) {
                    pagesParGabarit.merge(gabarit, 1, Integer::sum);
                }
            }
        }
        int seuil = Math.max(Math.min(3, pages.size()), (int) Math.ceil(pages.size() * 0.2));
        return pagesParGabarit.entrySet().stream()
                .filter(e -> e.getValue() >= seuil)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    /** Indices des lignes non vides situées parmi les premières ou les dernières de la page. */
    private static Set<Integer> extremites(String[] lignes) {
        List<Integer> nonVides = new ArrayList<>();
        for (int i = 0; i < lignes.length; i++) {
            if (!normaliser(lignes[i]).isEmpty()) {
                nonVides.add(i);
            }
        }
        Set<Integer> extremites = new HashSet<>();
        for (int k = 0; k < nonVides.size(); k++) {
            if (k < LIGNES_D_EXTREMITE || k >= nonVides.size() - LIGNES_D_EXTREMITE) {
                extremites.add(nonVides.get(k));
            }
        }
        return extremites;
    }

    private static String gabarit(String ligne) {
        return NUMERO_DANS_L_EN_TETE.matcher(ligne).replaceAll("#");
    }

    /**
     * Une ligne d'extrémité répétée est un en-tête ou un pied de page, même si elle ressemble à un titre
     * (« Partie législative - Livre Ier : … » en haut de chaque page). Exception : un vrai titre dont seule la
     * neutralisation du numéro crée la répétition (« Article 12 », « Article 13 »… en haut de page).
     */
    private static boolean estRepetee(String ligne, Set<String> gabaritsRepetes) {
        String gabarit = gabarit(ligne);
        return gabaritsRepetes.contains(gabarit) && (gabarit.equals(ligne) || titre(ligne) == null);
    }

    // ----- 2. Assemblage : unités -> morceaux bornés par la taille maximale, avec chevauchement -----

    private List<Chunk> assembler(String document, List<Unite> unites) {
        List<Chunk> morceaux = new ArrayList<>();
        StringBuilder texte = new StringBuilder();
        String titreCourant = null;
        int pageCourante = 0;
        String chevauchementEnAttente = null;
        boolean precedentEstTitre = false;

        for (Unite unite : unites) {
            if (unite.estTitre() || !unite.titre().equals(titreCourant)) {
                fermer(morceaux, document, titreCourant, pageCourante, texte);
                chevauchementEnAttente = null; // le chevauchement ne franchit jamais un titre
                titreCourant = unite.titre();
            } else if (!texte.isEmpty() && texte.length() + 1 + unite.texte().length() > tailleMax) {
                chevauchementEnAttente = fermer(morceaux, document, titreCourant, pageCourante, texte);
            }
            if (texte.isEmpty()) {
                pageCourante = unite.page();
                if (chevauchementEnAttente != null) {
                    texte.append(chevauchementEnAttente).append(' ');
                    chevauchementEnAttente = null;
                }
            } else {
                texte.append(precedentEstTitre ? '\n' : ' ');
            }
            texte.append(unite.texte());
            precedentEstTitre = unite.estTitre();
        }
        fermer(morceaux, document, titreCourant, pageCourante, texte);
        return morceaux;
    }

    /** Enregistre le morceau en cours (sauf s'il se réduit à son titre) et retourne la queue à reprendre. */
    private String fermer(List<Chunk> morceaux, String document, String titre, int page, StringBuilder texte) {
        if (texte.isEmpty()) {
            return null;
        }
        String contenu = texte.toString();
        texte.setLength(0);
        if (contenu.equals(titre)) {
            return null;
        }
        morceaux.add(new Chunk(document, titre, page, contenu));
        return queue(contenu);
    }

    /** Les derniers caractères d'un morceau, étendus au mot entier et précédés de « … ». */
    private String queue(String contenu) {
        if (chevauchement <= 0 || contenu.length() <= chevauchement) {
            return null;
        }
        int debut = contenu.length() - chevauchement;
        while (debut > 0 && !Character.isWhitespace(contenu.charAt(debut - 1))) {
            debut--;
        }
        return "… " + contenu.substring(debut).strip();
    }
}
