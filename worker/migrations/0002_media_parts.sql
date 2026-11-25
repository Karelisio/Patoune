-- Médias volumineux envoyés par morceaux (format v2).
-- parts = 0 : ancien format (un seul objet R2 householdId/id) ; parts > 0 : morceaux householdId/id/n.
ALTER TABLE media ADD COLUMN parts INTEGER NOT NULL DEFAULT 0;

-- Morceaux chiffrés reçus (envoi en cours ou média complet). Leur taille compte dans le quota du foyer.
CREATE TABLE media_parts (
  household_id TEXT NOT NULL,
  id TEXT NOT NULL,
  n INTEGER NOT NULL,
  size INTEGER NOT NULL,
  created_at INTEGER NOT NULL,
  PRIMARY KEY (household_id, id, n)
);
