import { SELF } from "cloudflare:test";
import { env } from "cloudflare:workers";
import { describe, expect, it } from "vitest";

const BASE = "https://patoune.test";

async function call(path: string, init: { method?: string; token?: string; body?: unknown; raw?: BodyInit } = {}) {
  const headers: Record<string, string> = {};
  if (init.token) headers.authorization = `Bearer ${init.token}`;
  if (init.body !== undefined) headers["content-type"] = "application/json";
  const res = await SELF.fetch(BASE + path, {
    method: init.method ?? (init.body !== undefined || init.raw ? "POST" : "GET"),
    headers,
    body: init.raw ?? (init.body !== undefined ? JSON.stringify(init.body) : undefined),
  });
  const text = await res.text();
  let data: any = null;
  try { data = JSON.parse(text); } catch { data = text; }
  return { status: res.status, data };
}

const b64 = (bytes: Uint8Array) => btoa(String.fromCharCode(...bytes)).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
const hex = async (s: string) => [...new Uint8Array(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(s)))].map((x) => x.toString(16).padStart(2, "0")).join("");

const unb64 = (s: string) => {
  const t = s.replace(/-/g, "+").replace(/_/g, "/");
  return Uint8Array.from(atob(t + "=".repeat((4 - (t.length % 4)) % 4)), (c) => c.charCodeAt(0));
};

/** Chiffre comme le téléphone (AES-GCM, nonce préfixé). */
async function encryptBytes(key: CryptoKey, plain: string): Promise<Uint8Array> {
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const ct = new Uint8Array(await crypto.subtle.encrypt({ name: "AES-GCM", iv }, key, new TextEncoder().encode(plain)));
  const out = new Uint8Array(12 + ct.length); out.set(iv); out.set(ct, 12);
  return out;
}
const encrypt = async (key: CryptoKey, plain: string) => b64(await encryptBytes(key, plain));

async function createHousehold() {
  const r = await call("/v1/households", { body: { publicKey: "cHViLWNyZWF0b3I", fcmToken: "fcm-creator" } });
  expect(r.status).toBe(201);
  return r.data as { householdId: string; memberId: string; token: string; epoch: number };
}

async function invite(token: string) {
  const secret = b64(crypto.getRandomValues(new Uint8Array(24)));
  const r = await call("/v1/invites", { token, body: { secretHash: await hex(secret) } });
  expect(r.status).toBe(201);
  return { inviteId: r.data.inviteId as string, secret };
}

async function join(inv: { inviteId: string; secret: string }, pub = "cHViLW1lbWJyZQ") {
  return call(`/v1/invites/${inv.inviteId}/join`, { body: { secret: inv.secret, publicKey: pub, fcmToken: "fcm-" + pub } });
}

describe("appairage", () => {
  it("crée un foyer, invite et rejoint avec le secret du QR", async () => {
    const h = await createHousehold();
    const inv = await invite(h.token);
    const j = await join(inv);
    expect(j.status).toBe(201);
    expect(j.data.householdId).toBe(h.householdId);
    expect(j.data.creatorPublicKey).toBe("cHViLWNyZWF0b3I");
    const members = await call("/v1/members", { token: j.data.token });
    expect(members.data.members).toHaveLength(2);
  });

  it("refuse un mauvais secret, une invitation réutilisée et un foyer plein", async () => {
    const h = await createHousehold();
    const inv = await invite(h.token);
    expect((await call(`/v1/invites/${inv.inviteId}/join`, { body: { secret: "faux", publicKey: "eA" } })).status).toBe(403);
    expect((await join(inv)).status).toBe(201);
    expect((await join(inv, "YXV0cmU")).status).toBe(403);
    for (let i = 0; i < 4; i++) expect((await join(await invite(h.token), "bTI" + i)).status).toBe(201);
    const full = await call("/v1/invites", { token: h.token, body: { secretHash: await hex("x") } });
    expect(full.status).toBe(409);
  });

  it("exige un jeton valide", async () => {
    expect((await call("/v1/blobs")).status).toBe(401);
    expect((await call("/v1/blobs", { token: "inconnu" })).status).toBe(401);
  });
});

describe("retrait de membre", () => {
  it("le créateur retire un membre : jeton révoqué, epoch de clé incrémentée, nouvelles clés publiées", async () => {
    const h = await createHousehold();
    const a = (await join(await invite(h.token), "QQ")).data;
    const b = (await join(await invite(h.token), "Qg")).data;
    expect((await call(`/v1/members/${a.memberId}`, { method: "DELETE", token: b.token })).status).toBe(403);
    const r = await call(`/v1/members/${a.memberId}`, { method: "DELETE", token: h.token });
    expect(r.data.epoch).toBe(2);
    expect((await call("/v1/blobs", { token: a.token })).status).toBe(401);
    const post = await call("/v1/keys", { token: h.token, body: { epoch: 2, wrapped: { [b.memberId]: "d3JhcHBlZA" } } });
    expect(post.status).toBe(200);
    const keys = await call("/v1/keys", { token: b.token });
    expect(keys.data).toMatchObject({ epoch: 2, wrapped: "d3JhcHBlZA", creatorPublicKey: "cHViLWNyZWF0b3I" });
  });

  it("quitter le foyer : le créateur parti, le plus ancien membre prend le relais", async () => {
    const h = await createHousehold();
    const a = (await join(await invite(h.token))).data;
    expect((await call("/v1/me", { method: "DELETE", token: h.token })).status).toBe(200);
    const members = await call("/v1/members", { token: a.token });
    expect(members.data.members).toEqual([expect.objectContaining({ id: a.memberId, isCreator: true })]);
  });
});

describe("blobs et médias", () => {
  it("envoie et récupère des blobs dans l'ordre, sans doublon", async () => {
    const h = await createHousehold();
    const other = (await join(await invite(h.token))).data;
    const blobs = [1, 2, 3].map((i) => ({ id: `blob-000${i}`, epoch: 1, data: b64(new Uint8Array([i, i, i])) }));
    await call("/v1/blobs", { token: h.token, body: { blobs } });
    await call("/v1/blobs", { token: h.token, body: { blobs: [blobs[0]] } });
    const r = await call("/v1/blobs?after=0", { token: other.token });
    expect(r.data.blobs.map((x: any) => x.id)).toEqual(["blob-0001", "blob-0002", "blob-0003"]);
    const after = await call(`/v1/blobs?after=${r.data.blobs[1].seq}`, { token: other.token });
    expect(after.data.blobs.map((x: any) => x.id)).toEqual(["blob-0003"]);
    // Un autre foyer ne voit rien.
    const stranger = await createHousehold();
    expect((await call("/v1/blobs", { token: stranger.token })).data.blobs).toHaveLength(0);
  });

  it("stocke les médias sur R2 avec limite de taille et quota", async () => {
    const h = await createHousehold();
    const put = await call("/v1/media/media-0001", { method: "PUT", token: h.token, raw: new Uint8Array(800) });
    expect(put.status).toBe(201);
    const got = await SELF.fetch(`${BASE}/v1/media/media-0001`, { headers: { authorization: `Bearer ${h.token}` } });
    expect((await got.arrayBuffer()).byteLength).toBe(800);
    expect((await call("/v1/media/media-0002", { method: "PUT", token: h.token, raw: new Uint8Array(2000) })).status).toBe(413);
    for (const i of [2, 3, 4]) await call(`/v1/media/media-000${i}`, { method: "PUT", token: h.token, raw: new Uint8Array(1000) });
    expect((await call("/v1/media/media-0005", { method: "PUT", token: h.token, raw: new Uint8Array(1000) })).status).toBe(507);
    expect((await call("/v1/quota", { token: h.token })).data).toMatchObject({ used: 3800, max: 4096, maxMedia: 1024 });
  });

  it("push : relaie un blob chiffré aux autres membres seulement", async () => {
    const h = await createHousehold();
    await join(await invite(h.token));
    const r = await call("/v1/push", { token: h.token, body: { data: "Y2hpZmZyw6k" } });
    expect(r.data.sent).toBe(1);
  });
});

describe("chiffrement de bout en bout", () => {
  it("le serveur ne reçoit aucune donnée en clair", async () => {
    const SECRET = "Mochi a reçu Flixotide à 8h02 par Camille";
    const key = (await crypto.subtle.generateKey({ name: "AES-GCM", length: 256 }, true, ["encrypt", "decrypt"])) as CryptoKey;
    const h = await createHousehold();
    const other = (await join(await invite(h.token))).data;
    const data = await encrypt(key, JSON.stringify([{ entity: "dose", field: "createdBy", value: SECRET }]));
    await call("/v1/blobs", { token: h.token, body: { blobs: [{ id: "blob-e2e-01", epoch: 1, data }] } });
    await call("/v1/media/media-e2e-1", { method: "PUT", token: h.token, raw: await encryptBytes(key, SECRET) });
    await call("/v1/push", { token: h.token, body: { data: await encrypt(key, SECRET) } });

    // Examine tout ce que le serveur a stocké.
    const dump: string[] = [];
    for (const t of ["households", "members", "invites", "blobs", "wrapped_keys", "media", "media_parts"]) {
      const rows = await env.DB.prepare(`SELECT * FROM ${t}`).all();
      dump.push(JSON.stringify(rows.results));
    }
    const listed = await env.MEDIA.list();
    for (const o of listed.objects) dump.push(new TextDecoder().decode(await (await env.MEDIA.get(o.key))!.arrayBuffer()));
    const all = dump.join("\n");
    for (const word of ["Mochi", "Flixotide", "Camille", "createdBy"]) expect(all).not.toContain(word);

    // Le membre récupère le blob et le déchiffre avec la clé du foyer (jamais transmise au serveur).
    const r = await call("/v1/blobs", { token: other.token });
    const raw = unb64(r.data.blobs[0].data);
    const plain = new TextDecoder().decode(await crypto.subtle.decrypt({ name: "AES-GCM", iv: raw.slice(0, 12) }, key, raw.slice(12)));
    expect(plain).toContain(SECRET);
  });
});

/** Chiffre un morceau comme le téléphone : [epoch 2 octets][nonce][AES-GCM], AAD householdId|media|id|n|total. */
async function sealPart(key: CryptoKey, hid: string, id: string, n: number, total: number, plain: Uint8Array): Promise<Uint8Array> {
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const additionalData = new TextEncoder().encode(`${hid}|media|${id}|${n}|${total}`);
  const ct = new Uint8Array(await crypto.subtle.encrypt({ name: "AES-GCM", iv, additionalData }, key, plain));
  const out = new Uint8Array(2 + 12 + ct.length); out.set([0, 1]); out.set(iv, 2); out.set(ct, 14);
  return out;
}

const putPart = (token: string, id: string, n: number, raw: Uint8Array) => call(`/v1/media/${id}/parts/${n}`, { method: "PUT", token, raw });
const getPart = async (token: string, id: string, n: number) => {
  const r = await SELF.fetch(`${BASE}/v1/media/${id}/parts/${n}`, { headers: { authorization: `Bearer ${token}` } });
  return { status: r.status, bytes: new Uint8Array(await r.arrayBuffer()) };
};
const filled = (size: number, v: number) => new Uint8Array(size).fill(v);

describe("médias par morceaux (v2)", () => {
  it("envoie par morceaux, termine, puis un autre membre relit chaque morceau", async () => {
    const h = await createHousehold();
    const other = (await join(await invite(h.token))).data;
    const sizes = [400, 400, 200];
    for (const [n, s] of sizes.entries()) expect((await putPart(h.token, "video-v2-01", n, filled(s, n + 1))).status).toBe(201);
    // Tant que le média n'est pas terminé, il n'existe pas pour les autres.
    expect((await call("/v1/media/video-v2-01/info", { token: other.token })).status).toBe(404);
    const done = await call("/v1/media/video-v2-01/complete", { token: h.token, body: { parts: 3, size: 1000 } });
    expect(done.status).toBe(200);
    expect((await call("/v1/media/video-v2-01/info", { token: other.token })).data).toEqual({ parts: 3, size: 1000, format: 2 });
    for (const [n, s] of sizes.entries()) {
      const got = await getPart(other.token, "video-v2-01", n);
      expect(got.status).toBe(200);
      expect(got.bytes).toEqual(filled(s, n + 1));
    }
    expect((await getPart(other.token, "video-v2-01", 3)).status).toBe(404);
    // L'ancienne route ne renvoie rien pour un média v2.
    expect((await call("/v1/media/video-v2-01", { token: other.token })).status).toBe(404);
    expect((await call("/v1/quota", { token: h.token })).data.used).toBe(1000);
  });

  it("ré-envoi idempotent d'un morceau et du complete, sans double comptage", async () => {
    const h = await createHousehold();
    await putPart(h.token, "video-v2-02", 0, filled(500, 1));
    await putPart(h.token, "video-v2-02", 1, filled(300, 2));
    expect((await putPart(h.token, "video-v2-02", 1, filled(300, 2))).status).toBe(201);
    expect((await call("/v1/quota", { token: h.token })).data.used).toBe(800);
    for (let i = 0; i < 2; i++) expect((await call("/v1/media/video-v2-02/complete", { token: h.token, body: { parts: 2 } })).status).toBe(200);
    expect((await call("/v1/media/video-v2-02/info", { token: h.token })).data).toMatchObject({ parts: 2, size: 800 });
    expect((await call("/v1/quota", { token: h.token })).data.used).toBe(800);
  });

  it("refuse de terminer s'il manque un morceau ou si la taille annoncée diffère", async () => {
    const h = await createHousehold();
    await putPart(h.token, "video-v2-03", 0, filled(100, 1));
    await putPart(h.token, "video-v2-03", 2, filled(100, 3));
    expect((await call("/v1/media/video-v2-03/complete", { token: h.token, body: { parts: 3 } })).status).toBe(409);
    expect((await call("/v1/media/video-v2-03/info", { token: h.token })).status).toBe(404);
    await putPart(h.token, "video-v2-03", 1, filled(100, 2));
    expect((await call("/v1/media/video-v2-03/complete", { token: h.token, body: { parts: 3, size: 999 } })).status).toBe(409);
    expect((await call("/v1/media/video-v2-03/complete", { token: h.token, body: { parts: 0 } })).status).toBe(400);
    expect((await call("/v1/media/video-v2-03/complete", { token: h.token, body: { parts: 3, size: 300 } })).status).toBe(200);
  });

  it("quota du foyer dépassé : 507, en comptant les morceaux déjà présents", async () => {
    const h = await createHousehold();
    for (const id of ["video-q-01", "video-q-02", "video-q-03"]) {
      await putPart(h.token, id, 0, filled(500, 1));
      await putPart(h.token, id, 1, filled(500, 2));
      await call(`/v1/media/${id}/complete`, { token: h.token, body: { parts: 2 } });
    }
    expect((await putPart(h.token, "video-q-04", 0, filled(500, 1))).status).toBe(201);
    expect((await putPart(h.token, "video-q-04", 1, filled(500, 2))).status).toBe(201);
    // 3000 + 1000 déjà présents (morceaux non terminés compris) : 200 de plus dépasse 4096.
    expect((await putPart(h.token, "video-q-05", 0, filled(200, 1))).status).toBe(507);
    // Remplacer un morceau existant par un plus petit reste possible.
    expect((await putPart(h.token, "video-q-04", 0, filled(300, 1))).status).toBe(201);
    expect((await call("/v1/quota", { token: h.token })).data.used).toBe(3800);
  });

  it("taille d'un morceau ou taille totale au-delà du maximum : 413", async () => {
    const h = await createHousehold();
    expect((await putPart(h.token, "video-big-1", 0, filled(2000, 1))).status).toBe(413);
    expect((await putPart(h.token, "video-big-1", 0, filled(600, 1))).status).toBe(201);
    expect((await putPart(h.token, "video-big-1", 1, filled(600, 2))).status).toBe(413);
    expect((await putPart(h.token, "video-big-1", 1, filled(424, 2))).status).toBe(201);
    expect((await call("/v1/media/video-big-1/complete", { token: h.token, body: { parts: 2 } })).data.size).toBe(1024);
  });

  it("isolement entre foyers", async () => {
    const h = await createHousehold();
    const stranger = await createHousehold();
    await putPart(h.token, "video-iso-1", 0, filled(100, 7));
    await call("/v1/media/video-iso-1/complete", { token: h.token, body: { parts: 1 } });
    expect((await getPart(stranger.token, "video-iso-1", 0)).status).toBe(404);
    expect((await call("/v1/media/video-iso-1/info", { token: stranger.token })).status).toBe(404);
    expect((await call("/v1/media/video-iso-1/complete", { token: stranger.token, body: { parts: 1 } })).status).toBe(409);
    // Un morceau du même identifiant dans l'autre foyer n'écrase rien.
    await putPart(stranger.token, "video-iso-1", 0, filled(50, 9));
    const mine = await getPart(h.token, "video-iso-1", 0);
    expect(mine.status).toBe(200);
    expect(mine.bytes).toEqual(filled(100, 7));
    expect((await call("/v1/media/video-iso-1/parts/0", { method: "PUT", raw: filled(10, 1) })).status).toBe(401);
  });

  it("suppression : morceaux effacés et quota libéré", async () => {
    const h = await createHousehold();
    for (const n of [0, 1, 2]) await putPart(h.token, "video-del-1", n, filled(100, n));
    await call("/v1/media/video-del-1/complete", { token: h.token, body: { parts: 3 } });
    expect((await call("/v1/media/video-del-1", { method: "DELETE", token: h.token })).status).toBe(200);
    expect((await call("/v1/media/video-del-1/info", { token: h.token })).status).toBe(404);
    expect((await getPart(h.token, "video-del-1", 0)).status).toBe(404);
    expect((await env.MEDIA.list({ prefix: `${h.householdId}/` })).objects).toHaveLength(0);
    expect((await call("/v1/quota", { token: h.token })).data.used).toBe(0);
  });

  it("l'ancien format reste lisible et se signale par parts = 0", async () => {
    const h = await createHousehold();
    const other = (await join(await invite(h.token))).data;
    await call("/v1/media/photo-v1-01", { method: "PUT", token: h.token, raw: filled(300, 5) });
    expect((await call("/v1/media/photo-v1-01/info", { token: other.token })).data).toEqual({ parts: 0, size: 300, format: 1 });
    const got = await SELF.fetch(`${BASE}/v1/media/photo-v1-01`, { headers: { authorization: `Bearer ${other.token}` } });
    expect(new Uint8Array(await got.arrayBuffer())).toEqual(filled(300, 5));
    expect((await call("/v1/quota", { token: h.token })).data.used).toBe(300);
  });

  it("aucune donnée en clair : les morceaux sont chiffrés par le téléphone", async () => {
    const SECRET = "Mochi tousse fort, vidéo filmée par Camille";
    const key = (await crypto.subtle.generateKey({ name: "AES-GCM", length: 256 }, true, ["encrypt", "decrypt"])) as CryptoKey;
    const h = await createHousehold();
    const other = (await join(await invite(h.token))).data;
    const plain = new TextEncoder().encode(SECRET.repeat(10));
    const chunks = [plain.slice(0, 200), plain.slice(200, 400), plain.slice(400)];
    for (const [n, c] of chunks.entries()) await putPart(h.token, "video-e2e-2", n, await sealPart(key, h.householdId, "video-e2e-2", n, 3, c));
    await call("/v1/media/video-e2e-2/complete", { token: h.token, body: { parts: 3 } });

    const dump: string[] = [];
    for (const t of ["media", "media_parts"]) dump.push(JSON.stringify((await env.DB.prepare(`SELECT * FROM ${t}`).all()).results));
    for (const o of (await env.MEDIA.list({ prefix: `${h.householdId}/` })).objects) {
      dump.push(new TextDecoder().decode(await (await env.MEDIA.get(o.key))!.arrayBuffer()));
    }
    for (const word of ["Mochi", "tousse", "Camille"]) expect(dump.join("\n")).not.toContain(word);

    // Le membre déchiffre chaque morceau en vérifiant l'AAD (index et total).
    const out: number[] = [];
    for (let n = 0; n < 3; n++) {
      const raw = (await getPart(other.token, "video-e2e-2", n)).bytes;
      const additionalData = new TextEncoder().encode(`${h.householdId}|media|video-e2e-2|${n}|3`);
      out.push(...new Uint8Array(await crypto.subtle.decrypt({ name: "AES-GCM", iv: raw.slice(2, 14), additionalData }, key, raw.slice(14))));
    }
    expect(new TextDecoder().decode(new Uint8Array(out))).toBe(SECRET.repeat(10));
    // Morceau déplacé (mauvais index dans l'AAD) : le déchiffrement échoue.
    const moved = (await getPart(other.token, "video-e2e-2", 1)).bytes;
    const wrongAad = new TextEncoder().encode(`${h.householdId}|media|video-e2e-2|0|3`);
    await expect(crypto.subtle.decrypt({ name: "AES-GCM", iv: moved.slice(2, 14), additionalData: wrongAad }, key, moved.slice(14))).rejects.toThrow();
  });
});
