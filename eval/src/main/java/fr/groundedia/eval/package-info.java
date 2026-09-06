/**
 * Harnais d'évaluation de groundedIA : le jeu de référence ({@code golden-set.yaml}, trois familles), l'exécuteur
 * qui interroge l'application par son API et fait juger chaque réponse, les métriques (justesse, citations, refus,
 * latence, coût), le rapport {@code evaluation.md} et la porte de non-régression ({@code regression-set.yaml}).
 *
 * <p>Le module évalue l'application de l'extérieur, par HTTP, et ne dépend d'aucun autre module du projet.
 */
package fr.groundedia.eval;
