/** Envoi de messages FCM HTTP v1 avec le compte de service (JWT RS256 signé via WebCrypto). */

export interface ServiceAccount {
  client_email: string;
  private_key: string;
  token_uri?: string;
}

let cached: { token: string; exp: number } | null = null;

function b64url(data: ArrayBuffer | Uint8Array | string): string {
  const bytes = typeof data === "string" ? new TextEncoder().encode(data) : new Uint8Array(data);
  let s = "";
  for (const b of bytes) s += String.fromCharCode(b);
  return btoa(s).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function pemToDer(pem: string): ArrayBuffer {
  const body = pem.replace(/-----[^-]+-----/g, "").replace(/\s+/g, "");
  const bin = atob(body);
  const out = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
  return out.buffer;
}

async function accessToken(sa: ServiceAccount, fetcher: typeof fetch): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  if (cached && cached.exp - 60 > now) return cached.token;
  const tokenUri = sa.token_uri ?? "https://oauth2.googleapis.com/token";
  const header = b64url(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const claims = b64url(JSON.stringify({
    iss: sa.client_email,
    scope: "https://www.googleapis.com/auth/firebase.messaging",
    aud: tokenUri,
    iat: now,
    exp: now + 3600,
  }));
  const key = await crypto.subtle.importKey("pkcs8", pemToDer(sa.private_key), { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["sign"]);
  const sig = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, new TextEncoder().encode(`${header}.${claims}`));
  const res = await fetcher(tokenUri, {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body: `grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer&assertion=${header}.${claims}.${b64url(sig)}`,
  });
  if (!res.ok) throw new Error(`oauth ${res.status}`);
  const json = (await res.json()) as { access_token: string; expires_in: number };
  cached = { token: json.access_token, exp: now + json.expires_in };
  return json.access_token;
}

/**
 * Envoie un message « data » (pas de notification visible côté serveur) à chaque jeton.
 * Le contenu est un blob chiffré : seul le téléphone destinataire sait l'afficher.
 * Retourne les jetons invalides (à oublier).
 */
export async function sendData(
  saJson: string | undefined,
  projectId: string,
  tokens: string[],
  data: Record<string, string>,
  fetcher: typeof fetch = fetch,
): Promise<string[]> {
  if (!saJson || tokens.length === 0) return [];
  // Le secret peut être le JSON brut ou son encodage base64.
  const raw = saJson.trim().startsWith("{") ? saJson : atob(saJson.trim());
  const sa = JSON.parse(raw) as ServiceAccount;
  const token = await accessToken(sa, fetcher);
  const invalid: string[] = [];
  await Promise.all(tokens.map(async (t) => {
    const res = await fetcher(`https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`, {
      method: "POST",
      headers: { authorization: `Bearer ${token}`, "content-type": "application/json" },
      body: JSON.stringify({ message: { token: t, data, android: { priority: "high" } } }),
    });
    if (res.status === 404 || res.status === 400) invalid.push(t);
  }));
  return invalid;
}

export function resetFcmCache() { cached = null; }
