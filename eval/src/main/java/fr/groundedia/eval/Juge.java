package fr.groundedia.eval;

/**
 * Le juge : un second appel à un LLM qui compare la réponse obtenue à la réponse attendue. Il ne sert qu'aux
 * familles A et B ; les refus (famille C) et les citations sont jugés par le code, pas par un modèle.
 */
public interface Juge {

    Verdict juger(String question, String reponseAttendue, String reponseObtenue);

    /** Le modèle utilisé, pour le rapport. */
    String modele();
}
