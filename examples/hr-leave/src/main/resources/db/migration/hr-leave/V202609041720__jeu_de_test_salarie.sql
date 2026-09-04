-- Jeu de 3 salariés de test pour la démonstration.

INSERT INTO salarie (nom, date_embauche, type_contrat, statut, temps_travail, solde_conges, convention) VALUES
    ('Amina Diallo', DATE '2019-03-01', 'CDI', 'CADRE',     100, 12.5, 'Syntec'),
    ('Marc Petit',   DATE '2024-09-15', 'CDI', 'NON_CADRE', 100,  4.0, 'Syntec'),
    ('Julie Nguyen', DATE '2021-01-10', 'CDD', 'NON_CADRE',  80,  8.5, 'Syntec');
