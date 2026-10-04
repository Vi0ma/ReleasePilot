# Migrations

Flyway controle le schema. V1__create_users.sql ajoute la table users en Phase 1A.
V2__normalize_user_emails.sql normalise les emails existants pour Auth et impose leur forme canonique. Une collision de comptes existants doit etre resolue explicitement avant application ; aucun compte n'est fusionne automatiquement.
Hibernate reste en ddl-auto=validate. Les migrations appliquees ne doivent pas etre modifiees.

V3__create_core_domain.sql ajoute projects, environments, feature_flags, flag_configurations et audit_events. Les configurations sont contraintes au meme Project via FK composites ; rollout_percentage est borne a 0..100. Les identifiants historiques d'audit survivent a la suppression des ressources, avec un owner_user_id pour l'authorization. Aucune cascade de suppression SQL.
