-- Foyers : seul l'epoch de clé (numéro de rotation) est connu du serveur.
CREATE TABLE households (
  id TEXT PRIMARY KEY,
  epoch INTEGER NOT NULL DEFAULT 1,
  created_at INTEGER NOT NULL
);

-- Membres : jeton (haché), clé publique ECDH, jeton FCM. Aucun prénom (il est chiffré dans les blobs).
CREATE TABLE members (
  id TEXT PRIMARY KEY,
  household_id TEXT NOT NULL REFERENCES households(id),
  token_hash TEXT NOT NULL UNIQUE,
  public_key TEXT NOT NULL,
  fcm_token TEXT,
  is_creator INTEGER NOT NULL DEFAULT 0,
  joined_at INTEGER NOT NULL
);
CREATE INDEX members_household ON members(household_id);

-- Invitations : le secret (dans le QR) n'est stocké que haché. La clé du foyer n'y figure jamais.
CREATE TABLE invites (
  id TEXT PRIMARY KEY,
  household_id TEXT NOT NULL REFERENCES households(id),
  secret_hash TEXT NOT NULL,
  expires_at INTEGER NOT NULL,
  used INTEGER NOT NULL DEFAULT 0
);

-- Journal d'événements chiffrés (blobs opaques), ordonné par seq.
CREATE TABLE blobs (
  seq INTEGER PRIMARY KEY AUTOINCREMENT,
  household_id TEXT NOT NULL,
  id TEXT NOT NULL,
  epoch INTEGER NOT NULL,
  sender TEXT NOT NULL,
  data TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  UNIQUE(household_id, id)
);
CREATE INDEX blobs_household_seq ON blobs(household_id, seq);

-- Nouvelle clé de foyer chiffrée pour chaque membre restant (ECDH), après un retrait.
CREATE TABLE wrapped_keys (
  household_id TEXT NOT NULL,
  epoch INTEGER NOT NULL,
  member_id TEXT NOT NULL,
  data TEXT NOT NULL,
  PRIMARY KEY (household_id, epoch, member_id)
);

-- Médias chiffrés stockés sur R2 (clé : householdId/id).
CREATE TABLE media (
  household_id TEXT NOT NULL,
  id TEXT NOT NULL,
  size INTEGER NOT NULL,
  created_at INTEGER NOT NULL,
  PRIMARY KEY (household_id, id)
);
