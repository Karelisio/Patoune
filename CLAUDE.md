# Patoune — notes pour Claude

Carnet de soins pour animaux (chat asthmatique d'abord). Android natif Kotlin + Jetpack Compose (Material 3 Expressive),
package `com.karelisio.patoune`. Worker Cloudflare dans `/worker`. Tout en français (UI, commits, docs).

## Commandes
- Appli : `./gradlew assembleRelease lint testDebugUnitTest` (SDK Android requis ; `local.properties` → `sdk.dir`).
- Worker : `cd worker && npm ci && npm run typecheck && npm test` (vitest + `@cloudflare/vitest-plugin`/Miniflare).
- Version : fichier `VERSION` (X.Y.Z) = versionName ; versionCode = `GITHUB_RUN_NUMBER`.

## Standards
- Dépendances uniquement dans `gradle/libs.versions.toml`, versions stables. Pas de DI framework : `AppContainer`.
- Logique pure et testée dans `domain/` (JVM) ; Robolectric pour Room/DataStore (`app/src/test`).
- Toute écriture de données passe par `Store.save/delete` (jamais un DAO directement), sauf champs locaux (`saveLocal`).
- Respecter « réduire les animations » (`rememberReduceMotion`) ; textes d'aide médicale discrets (`VetNote`).
- Lint en erreur = build rouge. Garder les gardes d'API (minSdk 29, compileSdk 37.2).
- Rien de sensible commité : google-services.json, keystore, `.dev.vars` sont ignorés.

## Architecture (app/src/main/java/com/karelisio/patoune)
- `data/` : entités Room (toutes `Syncable` : id UUID, createdBy, updatedAt, deleted), DAO, `Store`, `Settings` (DataStore).
- `domain/` : HLC, fusion d'événements, crypto (AES-GCM, PBKDF2, ECDH P-256, HKDF), respiration, garde-fou double dose,
  stock, planification des rappels, observance, détection de pics.
- `reminders/` : AlarmManager `setExactAndAllowWhileIdle`, actions de notification, `DailyWorker` (ordonnances, soins, pollens).
- `sync/` : `HttpApi`, `SessionStore` (secrets scellés par Android Keystore), `SyncEngine`, `HouseholdManager`, FCM.
- `media/` (CameraX, Media3 720p, analyse de mouvement), `report/` (PDF), `air/` (Open-Meteo), `backup/`, `widget/` (Glance, tuile).
- `ui/` : `AppNav` (navigation type-safe), écrans dans `ui/screens`, composants dans `ui/components`.

## Modes solo / partagé
- Solo (défaut) : aucun appel au Worker, aucun membre affiché. `SessionStore.load()` renvoie null → la sync ne fait rien.
- Partagé : foyer de 2 à 6 membres. Passage solo → partagé : `Store.queueSnapshot()` envoie tout l'état chiffré.
  Quitter : rapatriement des médias puis `Settings.leaveHousehold()` (données locales intactes). 401 = retiré → solo.
- Retrait d'un membre (créateur) : epoch +1, nouvelle clé enveloppée par ECDH pour chaque membre restant (`/v1/keys`),
  puis ré-envoi de l'état sous la nouvelle clé.

## Protocole de sync
- Chaque changement de champ = `FieldEvent` (eventId, entity, rowId, field, value JSON, HLC, deviceId) dans `sync_events`.
- Envoi : lots chiffrés `Envelope` = [epoch 2 octets][AES-GCM(gzip(JSON))], AAD `householdId|blob|blobId`.
- Réception : `/v1/blobs?after=seq`, fusion « dernier écrit gagne par champ » (HLC, puis deviceId), horloges par champ
  dans `field_clocks`. Suppressions = champ `deleted` (tombstone). Idempotent, indépendant de l'ordre → convergence.
- Médias : chiffrés (AAD `householdId|media|id`) sur R2 ; pushs FCM « data » chiffrés (AAD `householdId|push`).
- Le Worker ne voit que des identifiants aléatoires, des clés publiques, des jetons hachés et des blobs opaques.
  La clé du foyer ne transite que par le QR code (ou le code de secours).

## CI
- `.github/workflows/android.yml` : build/lint/tests ; sur `main`, si le tag `vX.Y.Z` n'existe pas → APK signé, tag, Release.
- `.github/workflows/worker.yml` (si `/worker` change) : tests ; sur `main` → création D1/R2 si absentes, migrations,
  déploiement, secret `FCM_SERVICE_ACCOUNT`.
