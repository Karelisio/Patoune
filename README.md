# Patoune

Carnet de soins pour animaux, pensé d'abord pour un chat asthmatique : traitements et rappels, fréquence respiratoire,
crises filmées, carnet de santé, rapport PDF pour le véto. Fonctionne entièrement en solo, sans compte ni réseau ;
le partage avec d'autres personnes (conjoint, famille, pet-sitter) est une option chiffrée de bout en bout.

> Patoune aide au suivi et n'est pas un avis médical. Les seuils d'alerte sont ceux fixés avec votre vétérinaire.

## Fonctionnalités
- **Animaux** : plusieurs profils (photo, espèce, race, sexe, naissance, puce, stérilisation, vétos), sélecteur en haut d'écran.
- **Traitements** : inhalateur avec chambre, comprimé, liquide… ; rappels exacts avec actions « Donné / Reporter 15 min / Sauté » ;
  historique (et qui a donné, en partagé) ; garde-fou double dose ; stock, alerte de fin et rappel d'ordonnance.
- **Respiration** : compteur au tap (15/30/60 s), mode nuit, courbes 7 j / 30 j / tout avec moyenne mobile et seuil véto,
  alerte au-dessus du seuil, comptage auto expérimental sur vidéo.
- **Crises et toux** : bouton « Crise en cours » (vidéo immédiate et horodatage), intensité, type, déclencheurs, traitement d'urgence.
- **Photos et vidéos** : CameraX (vidéo avec son), Photo Picker, compression 720p, galerie filtrable.
- **Carnet de santé** : vaccins, vermifuges, antipuces avec rappels ; visites ; courbe de poids ; documents PDF/photo.
- **Observations rapides**, **pollens et qualité de l'air** (Open-Meteo, ville choisie à la main), **rapport PDF**,
  **fiche urgence** (appel en un tap, raccourci d'appli), **sauvegarde chiffrée** (.zip + mot de passe, médias inclus).
- Widget (prochain médicament + « Donné », dernière fréquence, « Compter »), tuile Réglages rapides, mise à jour in-app.
- **Langues** : français (par défaut) et anglais, selon la langue du téléphone (dates et nombres au format local).
  Accessibilité TalkBack : libellés des boutons et icônes, compteur respiratoire annoncé, graphiques résumés.


## Étapes manuelles
1. **Secrets GitHub** (déjà créés) : `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`,
   `GOOGLE_SERVICES_JSON`, `FCM_SERVICE_ACCOUNT`, `CLOUDFLARE_API_TOKEN`, `CLOUDFLARE_ACCOUNT_ID`.
2. **Déployer le Worker** : fusionner sur `main` (ou lancer le workflow « Worker » à la main). Il crée la base D1 `patoune`
   et le bucket R2 `patoune-media` s'ils n'existent pas, applique les migrations, déploie et pousse `FCM_SERVICE_ACCOUNT`.
   Le jeton Cloudflare doit avoir les droits Workers Scripts, D1 et R2 (édition).
3. **Adresse du Worker dans l'appli** : copier l'URL affichée dans le résumé du job (`https://patoune.<compte>.workers.dev`)
   dans la **variable** de dépôt `WORKER_URL` (Settings → Secrets and variables → Actions → Variables), puis relancer le build.
   Sans elle, l'adresse peut être saisie dans *Réglages → Mode → Serveur (avancé)*.
4. **Release** : chaque push sur `main` dont la `VERSION` n'a pas encore de tag publie l'APK signé et le changelog.
   Pour une nouvelle version, augmenter `VERSION`.
5. **Sur le téléphone (Honor/MagicOS)** : autoriser « Batterie sans restriction » depuis l'onboarding, et dans les réglages du
   téléphone activer la gestion manuelle du lancement de Patoune (lancement auto + activité en arrière-plan).

## Appairage (mode partagé)
1. Sur le premier téléphone : *Réglages → Mode → Créer le foyer* (prénom affiché aux autres membres).
   Les données existantes sont chiffrées puis envoyées.
2. *Inviter* affiche un QR code (valable 15 min, usage unique) et un code de secours.
3. Sur l'autre téléphone : *Réglages → Mode → Scanner* (ou *Saisir le code*). Ses données locales éventuelles sont fusionnées.
4. Le créateur peut retirer un membre : la clé du foyer change et n'est transmise qu'aux membres restants.
   « Quitter le foyer » repasse en solo avec une copie locale complète.

## Choix faits (points ambigus)
- **Ordre des événements** : horloge logique hybride (HLC) ; égalité départagée par identifiant d'appareil, puis d'événement.
- **Code de secours** : il contient l'identifiant d'invitation, le secret et la clé du foyer (≈ 80 caractères en groupes de 4),
  car la clé ne doit jamais passer par le serveur. Il utilise le serveur configuré sur le téléphone qui rejoint.
- **Rotation de clé** : chaque membre a une paire ECDH P-256 ; le créateur enveloppe la nouvelle clé pour chacun
  et ré-envoie l'état (et les médias) sous la nouvelle clé. Un membre retiré perd l'accès au serveur (jeton révoqué).
- **Créateur qui quitte** : le membre le plus ancien devient créateur ; le foyer est supprimé quand plus personne n'y est.
- **Scan QR** : ZXing (hors ligne, sans Google Play Services).
- **Fenêtre du garde-fou** : de mi-chemin entre l'horaire précédent et l'horaire courant jusqu'à maintenant (prise en avance
  de 30 min incluse) ; « si besoin » : 4 h glissantes.
- **Pushs** : l'émetteur envoie toujours, chaque membre filtre selon ses réglages ; « Prise donnée » annule le rappel.
- **Pollens** : maximum horaire du jour (grains/m³), barres de fond normalisées à 200 ; AQI européen.
- **Limites Worker** : 200 Mo par média (envoyé par morceaux de 1 Mio chiffrés, avec reprise), quota R2 de 2 Go par foyer (`worker/wrangler.toml`).
- **Comptage auto** : différence de luminance (grille 24×24 sur la zone) par rapport à la première image, à 10 i/s,
  puis détection de pics ; toujours présenté comme une estimation à valider.
- **Import de sauvegarde** : fusion (rien n'est effacé), même règle que la sync.
- **Historique modifiable** : prises, mesures, crises, observations, pesées et documents se corrigent ou s'ajoutent après coup
  (l'auteur affiché reste celui de la saisie d'origine ; garde-fou double dose sur la fenêtre de la date choisie ; une prise
  ou une mesure datée de plus d'1 h ne déclenche ni push « Prise donnée », ni annulation de rappel, ni alerte de seuil). L'historique des changements d'un
  traitement reste en lecture seule : c'est la trace de ce qui a été modifié, par qui et quand, et elle ne doit pas pouvoir être réécrite.
- **Traduction** : les valeurs stockées et synchronisées (options d'observation, types et déclencheurs de crise, espèce, sexe)
  restent des clés françaises ; seul l'affichage est traduit (repli sur la valeur brute si inconnue). Les textes rédigés
  au moment de l'action (historique d'un traitement, pushs du foyer) sont dans la langue de la personne qui agit.


## Développement
- Appli : `./gradlew assembleRelease lint testDebugUnitTest`
- Worker : `cd worker && npm ci && npm test` · local : `npx wrangler dev` (secrets locaux dans `worker/.dev.vars`, ignoré par git).
