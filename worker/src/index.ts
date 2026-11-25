import { sendData } from "./fcm";

export interface Env {
  DB: D1Database;
  MEDIA: R2Bucket;
  FCM_SERVICE_ACCOUNT?: string;
  FCM_PROJECT_ID: string;
  MAX_MEDIA_BYTES: string;
  QUOTA_BYTES: string;
  MAX_MEMBERS: string;
}

interface Member {
  id: string;
  household_id: string;
  public_key: string;
  fcm_token: string | null;
  is_creator: number;
  joined_at: number;
}

class HttpError extends Error {
  constructor(public status: number, message: string) { super(message); }
}

const MAX_BLOB_CHARS = 700_000;
const MAX_BATCH = 100;
const INVITE_TTL_MS = 15 * 60_000;

const json = (data: unknown, status = 200) =>
  new Response(JSON.stringify(data), { status, headers: { "content-type": "application/json" } });

function randomId(bytes = 16): string {
  const b = crypto.getRandomValues(new Uint8Array(bytes));
  return [...b].map((x) => x.toString(16).padStart(2, "0")).join("");
}

async function sha256(s: string): Promise<string> {
  const d = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(s));
  return [...new Uint8Array(d)].map((x) => x.toString(16).padStart(2, "0")).join("");
}

const isB64 = (s: unknown): s is string => typeof s === "string" && /^[A-Za-z0-9_\-+/=]*$/.test(s);
const isId = (s: unknown): s is string => typeof s === "string" && /^[A-Za-z0-9_\-]{8,64}$/.test(s);

async function body<T>(req: Request): Promise<T> {
  try { return (await req.json()) as T; } catch { throw new HttpError(400, "invalid json"); }
}

async function auth(req: Request, env: Env): Promise<Member> {
  const h = req.headers.get("authorization") ?? "";
  const token = h.startsWith("Bearer ") ? h.slice(7) : "";
  if (!token) throw new HttpError(401, "missing token");
  const m = await env.DB.prepare("SELECT * FROM members WHERE token_hash = ?").bind(await sha256(token)).first<Member>();
  if (!m) throw new HttpError(401, "unknown token");
  return m;
}

async function newMember(env: Env, householdId: string, publicKey: string, fcmToken: string | null, creator: boolean) {
  const id = randomId(12);
  const token = randomId(32);
  await env.DB.prepare("INSERT INTO members (id, household_id, token_hash, public_key, fcm_token, is_creator, joined_at) VALUES (?, ?, ?, ?, ?, ?, ?)")
    .bind(id, householdId, await sha256(token), publicKey, fcmToken, creator ? 1 : 0, Date.now()).run();
  return { memberId: id, token };
}

async function household(env: Env, id: string) {
  const h = await env.DB.prepare("SELECT * FROM households WHERE id = ?").bind(id).first<{ id: string; epoch: number }>();
  if (!h) throw new HttpError(404, "household");
  return h;
}

async function creatorOf(env: Env, householdId: string) {
  return env.DB.prepare("SELECT * FROM members WHERE household_id = ? AND is_creator = 1").bind(householdId).first<Member>();
}

/**
 * Octets occupés par le foyer : médias à l'ancien format (un objet) + tous les morceaux reçus
 * (médias v2 complets ou en cours d'envoi). Un média v2 n'est compté qu'à travers ses morceaux.
 */
async function quotaUsed(env: Env, householdId: string): Promise<number> {
  const r = await env.DB.prepare(
    "SELECT (SELECT COALESCE(SUM(size), 0) FROM media WHERE household_id = ?1 AND parts = 0) + (SELECT COALESCE(SUM(size), 0) FROM media_parts WHERE household_id = ?1) AS used",
  ).bind(householdId).first<{ used: number }>();
  return r?.used ?? 0;
}

/** Supprime tous les objets R2 sous un préfixe (par pages, 1000 clés max par suppression). */
async function deletePrefix(env: Env, prefix: string) {
  let cursor: string | undefined;
  do {
    const page = await env.MEDIA.list({ prefix, cursor });
    if (page.objects.length) await env.MEDIA.delete(page.objects.map((o) => o.key));
    cursor = page.truncated ? page.cursor : undefined;
  } while (cursor);
}

/** Efface un média, quel que soit son format : objet unique, morceaux et lignes de suivi. */
async function deleteMedia(env: Env, hid: string, id: string) {
  await env.MEDIA.delete(`${hid}/${id}`);
  await deletePrefix(env, `${hid}/${id}/`);
  await env.DB.batch([
    env.DB.prepare("DELETE FROM media WHERE household_id = ? AND id = ?").bind(hid, id),
    env.DB.prepare("DELETE FROM media_parts WHERE household_id = ? AND id = ?").bind(hid, id),
  ]);
}

/** Lit un corps binaire en refusant (413) tout ce qui dépasse `max` octets. */
async function readLimited(req: Request, max: number): Promise<ArrayBuffer> {
  const len = Number(req.headers.get("content-length") ?? "0");
  if (len > max) throw new HttpError(413, "too large");
  const data = await req.arrayBuffer();
  if (data.byteLength > max) throw new HttpError(413, "too large");
  return data;
}

/** Taille max d'un morceau chiffré : 1 Mio de clair + epoch, nonce et tag AES-GCM, avec de la marge. */
const MAX_PART_BYTES = Math.floor(1.1 * 1024 * 1024);
const MAX_PARTS = 10_000;

/** Retire un membre ; si c'est le créateur, le plus ancien membre restant le devient. */
async function removeMember(env: Env, m: Member) {
  await env.DB.prepare("DELETE FROM members WHERE id = ?").bind(m.id).run();
  await env.DB.prepare("DELETE FROM wrapped_keys WHERE member_id = ?").bind(m.id).run();
  if (m.is_creator) {
    const next = await env.DB.prepare("SELECT id FROM members WHERE household_id = ? ORDER BY joined_at LIMIT 1").bind(m.household_id).first<{ id: string }>();
    if (next) await env.DB.prepare("UPDATE members SET is_creator = 1 WHERE id = ?").bind(next.id).run();
    else await deleteHousehold(env, m.household_id);
  }
}

async function deleteHousehold(env: Env, hid: string) {
  await deletePrefix(env, `${hid}/`);
  await env.DB.batch([
    env.DB.prepare("DELETE FROM blobs WHERE household_id = ?").bind(hid),
    env.DB.prepare("DELETE FROM media WHERE household_id = ?").bind(hid),
    env.DB.prepare("DELETE FROM media_parts WHERE household_id = ?").bind(hid),
    env.DB.prepare("DELETE FROM invites WHERE household_id = ?").bind(hid),
    env.DB.prepare("DELETE FROM wrapped_keys WHERE household_id = ?").bind(hid),
    env.DB.prepare("DELETE FROM households WHERE id = ?").bind(hid),
  ]);
}

async function route(req: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
  const url = new URL(req.url);
  const p = url.pathname.replace(/\/+$/, "");
  const m = req.method;

  if (p === "/v1/health") return json({ ok: true });

  // Création d'un foyer : le créateur devient le premier membre.
  if (p === "/v1/households" && m === "POST") {
    const b = await body<{ publicKey?: string; fcmToken?: string }>(req);
    if (!isB64(b.publicKey) || !b.publicKey) throw new HttpError(400, "publicKey");
    const id = randomId(16);
    await env.DB.prepare("INSERT INTO households (id, epoch, created_at) VALUES (?, 1, ?)").bind(id, Date.now()).run();
    const mem = await newMember(env, id, b.publicKey, b.fcmToken ?? null, true);
    return json({ householdId: id, ...mem, epoch: 1 }, 201);
  }

  // Rejoindre avec une invitation (identifiant + secret du QR).
  const join = p.match(/^\/v1\/invites\/([A-Za-z0-9_\-]+)\/join$/);
  if (join && m === "POST") {
    const b = await body<{ secret?: string; publicKey?: string; fcmToken?: string }>(req);
    if (!b.secret || !isB64(b.publicKey) || !b.publicKey) throw new HttpError(400, "secret/publicKey");
    const inv = await env.DB.prepare("SELECT * FROM invites WHERE id = ?").bind(join[1]).first<{ household_id: string; secret_hash: string; expires_at: number; used: number }>();
    if (!inv || inv.used || inv.expires_at < Date.now() || inv.secret_hash !== (await sha256(b.secret))) throw new HttpError(403, "invite");
    const count = await env.DB.prepare("SELECT COUNT(*) AS n FROM members WHERE household_id = ?").bind(inv.household_id).first<{ n: number }>();
    if ((count?.n ?? 0) >= Number(env.MAX_MEMBERS)) throw new HttpError(409, "household full");
    const claimed = await env.DB.prepare("UPDATE invites SET used = 1 WHERE id = ? AND used = 0").bind(join[1]).run();
    if (!claimed.meta.changes) throw new HttpError(403, "invite");
    const h = await household(env, inv.household_id);
    const mem = await newMember(env, h.id, b.publicKey, b.fcmToken ?? null, false);
    const creator = await creatorOf(env, h.id);
    return json({ householdId: h.id, ...mem, epoch: h.epoch, creatorPublicKey: creator?.public_key ?? null }, 201);
  }

  const me = await auth(req, env);
  const hid = me.household_id;

  if (p === "/v1/invites" && m === "POST") {
    const b = await body<{ secretHash?: string }>(req);
    if (typeof b.secretHash !== "string" || !/^[0-9a-f]{64}$/.test(b.secretHash)) throw new HttpError(400, "secretHash");
    const count = await env.DB.prepare("SELECT COUNT(*) AS n FROM members WHERE household_id = ?").bind(hid).first<{ n: number }>();
    if ((count?.n ?? 0) >= Number(env.MAX_MEMBERS)) throw new HttpError(409, "household full");
    const id = randomId(6);
    const expiresAt = Date.now() + INVITE_TTL_MS;
    await env.DB.prepare("INSERT INTO invites (id, household_id, secret_hash, expires_at) VALUES (?, ?, ?, ?)").bind(id, hid, b.secretHash, expiresAt).run();
    return json({ inviteId: id, expiresAt }, 201);
  }

  if (p === "/v1/members" && m === "GET") {
    const rows = await env.DB.prepare("SELECT id, public_key, is_creator, joined_at FROM members WHERE household_id = ? ORDER BY joined_at").bind(hid).all<Member>();
    const h = await household(env, hid);
    return json({ epoch: h.epoch, members: rows.results.map((r) => ({ id: r.id, publicKey: r.public_key, isCreator: !!r.is_creator, joinedAt: r.joined_at })) });
  }

  // Retrait d'un membre par le créateur : l'epoch de clé avance, le créateur doit publier la nouvelle clé.
  const del = p.match(/^\/v1\/members\/([A-Za-z0-9_\-]+)$/);
  if (del && m === "DELETE") {
    if (!me.is_creator) throw new HttpError(403, "creator only");
    if (del[1] === me.id) throw new HttpError(400, "use /v1/me");
    const target = await env.DB.prepare("SELECT * FROM members WHERE id = ? AND household_id = ?").bind(del[1], hid).first<Member>();
    if (!target) throw new HttpError(404, "member");
    await removeMember(env, target);
    await env.DB.prepare("UPDATE households SET epoch = epoch + 1 WHERE id = ?").bind(hid).run();
    const h = await household(env, hid);
    return json({ epoch: h.epoch });
  }

  if (p === "/v1/me" && m === "PUT") {
    const b = await body<{ fcmToken?: string }>(req);
    await env.DB.prepare("UPDATE members SET fcm_token = ? WHERE id = ?").bind(b.fcmToken ?? null, me.id).run();
    return json({ ok: true });
  }

  if (p === "/v1/me" && m === "DELETE") {
    await removeMember(env, me);
    return json({ ok: true });
  }

  // Clés enveloppées (ECDH) pour la nouvelle epoch.
  if (p === "/v1/keys" && m === "POST") {
    if (!me.is_creator) throw new HttpError(403, "creator only");
    const b = await body<{ epoch?: number; wrapped?: Record<string, string> }>(req);
    const h = await household(env, hid);
    if (b.epoch !== h.epoch || !b.wrapped) throw new HttpError(409, "epoch");
    const stmts = Object.entries(b.wrapped).filter(([k, v]) => isId(k) && isB64(v)).map(([k, v]) =>
      env.DB.prepare("INSERT OR REPLACE INTO wrapped_keys (household_id, epoch, member_id, data) VALUES (?, ?, ?, ?)").bind(hid, h.epoch, k, v));
    if (stmts.length) await env.DB.batch(stmts);
    return json({ ok: true });
  }

  if (p === "/v1/keys" && m === "GET") {
    const h = await household(env, hid);
    const w = await env.DB.prepare("SELECT data FROM wrapped_keys WHERE household_id = ? AND epoch = ? AND member_id = ?").bind(hid, h.epoch, me.id).first<{ data: string }>();
    const creator = await creatorOf(env, hid);
    return json({ epoch: h.epoch, wrapped: w?.data ?? null, creatorPublicKey: creator?.public_key ?? null });
  }

  // Journal d'événements chiffrés.
  if (p === "/v1/blobs" && m === "POST") {
    const b = await body<{ blobs?: { id: string; epoch: number; data: string }[] }>(req);
    const list = b.blobs ?? [];
    if (!Array.isArray(list) || list.length > MAX_BATCH) throw new HttpError(400, "blobs");
    for (const x of list) {
      if (!isId(x.id) || !Number.isInteger(x.epoch) || !isB64(x.data) || x.data.length > MAX_BLOB_CHARS) throw new HttpError(400, "blob");
    }
    const now = Date.now();
    if (list.length) await env.DB.batch(list.map((x) =>
      env.DB.prepare("INSERT OR IGNORE INTO blobs (household_id, id, epoch, sender, data, created_at) VALUES (?, ?, ?, ?, ?, ?)").bind(hid, x.id, x.epoch, me.id, x.data, now)));
    const last = await env.DB.prepare("SELECT MAX(seq) AS seq FROM blobs WHERE household_id = ?").bind(hid).first<{ seq: number | null }>();
    return json({ seq: last?.seq ?? 0 });
  }

  if (p === "/v1/blobs" && m === "GET") {
    const after = Number(url.searchParams.get("after") ?? 0) || 0;
    const limit = Math.min(Number(url.searchParams.get("limit") ?? 200) || 200, 500);
    const rows = await env.DB.prepare("SELECT seq, id, epoch, data FROM blobs WHERE household_id = ? AND seq > ? ORDER BY seq LIMIT ?").bind(hid, after, limit).all();
    const h = await household(env, hid);
    return json({ epoch: h.epoch, blobs: rows.results });
  }

  // Médias chiffrés sur R2. Ancien format : un seul objet `${hid}/${id}` (PUT/GET /v1/media/:id).
  const media = p.match(/^\/v1\/media\/([A-Za-z0-9_\-]{8,64})$/);
  if (media && m === "PUT") {
    const id = media[1];
    const max = Number(env.MAX_MEDIA_BYTES);
    const data = await readLimited(req, max);
    const existing = await env.DB.prepare(
      "SELECT (SELECT COALESCE(SUM(size), 0) FROM media WHERE household_id = ?1 AND id = ?2 AND parts = 0) + (SELECT COALESCE(SUM(size), 0) FROM media_parts WHERE household_id = ?1 AND id = ?2) AS size",
    ).bind(hid, id).first<{ size: number }>();
    if ((await quotaUsed(env, hid)) - (existing?.size ?? 0) + data.byteLength > Number(env.QUOTA_BYTES)) throw new HttpError(507, "quota");
    await deletePrefix(env, `${hid}/${id}/`);
    await env.MEDIA.put(`${hid}/${id}`, data);
    await env.DB.batch([
      env.DB.prepare("DELETE FROM media_parts WHERE household_id = ? AND id = ?").bind(hid, id),
      env.DB.prepare("INSERT OR REPLACE INTO media (household_id, id, size, parts, created_at) VALUES (?, ?, ?, 0, ?)").bind(hid, id, data.byteLength, Date.now()),
    ]);
    return json({ ok: true, size: data.byteLength }, 201);
  }

  if (media && m === "GET") {
    const obj = await env.MEDIA.get(`${hid}/${media[1]}`);
    if (!obj) throw new HttpError(404, "media");
    return new Response(obj.body, { headers: { "content-type": "application/octet-stream" } });
  }

  if (media && m === "DELETE") {
    await deleteMedia(env, hid, media[1]);
    return json({ ok: true });
  }

  // Format v2 : morceaux chiffrés `${hid}/${id}/${n}`, puis « complete » enregistre le média.
  const part = p.match(/^\/v1\/media\/([A-Za-z0-9_\-]{8,64})\/parts\/(\d{1,5})$/);
  if (part && m === "PUT") {
    const [, id, ns] = part;
    const n = Number(ns);
    if (n >= MAX_PARTS) throw new HttpError(400, "part");
    const max = Number(env.MAX_MEDIA_BYTES);
    const data = await readLimited(req, Math.min(MAX_PART_BYTES, max));
    const cur = await env.DB.prepare(
      "SELECT COALESCE(SUM(size), 0) AS total, COALESCE(SUM(CASE WHEN n = ? THEN size ELSE 0 END), 0) AS same FROM media_parts WHERE household_id = ? AND id = ?",
    ).bind(n, hid, id).first<{ total: number; same: number }>();
    const same = cur?.same ?? 0;
    // Ré-envoi du même morceau : il remplace l'ancien, sans être compté deux fois.
    if ((cur?.total ?? 0) - same + data.byteLength > max) throw new HttpError(413, "too large");
    if ((await quotaUsed(env, hid)) - same + data.byteLength > Number(env.QUOTA_BYTES)) throw new HttpError(507, "quota");
    await env.MEDIA.put(`${hid}/${id}/${n}`, data);
    await env.DB.prepare("INSERT OR REPLACE INTO media_parts (household_id, id, n, size, created_at) VALUES (?, ?, ?, ?, ?)")
      .bind(hid, id, n, data.byteLength, Date.now()).run();
    return json({ ok: true, size: data.byteLength }, 201);
  }

  if (part && m === "GET") {
    const obj = await env.MEDIA.get(`${hid}/${part[1]}/${Number(part[2])}`);
    if (!obj) throw new HttpError(404, "part");
    return new Response(obj.body, { headers: { "content-type": "application/octet-stream" } });
  }

  const complete = p.match(/^\/v1\/media\/([A-Za-z0-9_\-]{8,64})\/complete$/);
  if (complete && m === "POST") {
    const id = complete[1];
    const b = await body<{ parts?: number; size?: number }>(req);
    const parts = b.parts;
    if (typeof parts !== "number" || !Number.isInteger(parts) || parts < 1 || parts > MAX_PARTS) throw new HttpError(400, "parts");
    const have = await env.DB.prepare("SELECT COUNT(*) AS n, COALESCE(SUM(size), 0) AS size FROM media_parts WHERE household_id = ? AND id = ? AND n < ?")
      .bind(hid, id, parts).first<{ n: number; size: number }>();
    if ((have?.n ?? 0) !== parts) throw new HttpError(409, "missing parts");
    const size = have?.size ?? 0;
    if (b.size !== undefined && b.size !== size) throw new HttpError(409, "size");
    if (size > Number(env.MAX_MEDIA_BYTES)) throw new HttpError(413, "too large");
    // Morceaux au-delà du total (envoi précédent abandonné) et ancien objet unique : supprimés.
    const extra = await env.DB.prepare("SELECT n FROM media_parts WHERE household_id = ? AND id = ? AND n >= ?").bind(hid, id, parts).all<{ n: number }>();
    if (extra.results.length) await env.MEDIA.delete(extra.results.map((r) => `${hid}/${id}/${r.n}`));
    await env.MEDIA.delete(`${hid}/${id}`);
    await env.DB.batch([
      env.DB.prepare("DELETE FROM media_parts WHERE household_id = ? AND id = ? AND n >= ?").bind(hid, id, parts),
      env.DB.prepare("INSERT OR REPLACE INTO media (household_id, id, size, parts, created_at) VALUES (?, ?, ?, ?, ?)").bind(hid, id, size, parts, Date.now()),
    ]);
    return json({ ok: true, parts, size });
  }

  const info = p.match(/^\/v1\/media\/([A-Za-z0-9_\-]{8,64})\/info$/);
  if (info && m === "GET") {
    const row = await env.DB.prepare("SELECT size, parts FROM media WHERE household_id = ? AND id = ?").bind(hid, info[1]).first<{ size: number; parts: number }>();
    if (!row) throw new HttpError(404, "media");
    return json({ parts: row.parts, size: row.size, format: row.parts > 0 ? 2 : 1 });
  }

  if (p === "/v1/quota" && m === "GET") {
    return json({ used: await quotaUsed(env, hid), max: Number(env.QUOTA_BYTES), maxMedia: Number(env.MAX_MEDIA_BYTES) });
  }

  // Push « data » chiffré vers les autres membres (prise donnée, alerte, crise, stock).
  if (p === "/v1/push" && m === "POST") {
    const b = await body<{ data?: string }>(req);
    if (!isB64(b.data) || !b.data || b.data.length > 3000) throw new HttpError(400, "data");
    const rows = await env.DB.prepare("SELECT fcm_token FROM members WHERE household_id = ? AND id != ? AND fcm_token IS NOT NULL").bind(hid, me.id).all<{ fcm_token: string }>();
    const tokens = rows.results.map((r) => r.fcm_token);
    ctx.waitUntil((async () => {
      const invalid = await sendData(env.FCM_SERVICE_ACCOUNT, env.FCM_PROJECT_ID, tokens, { h: hid, d: b.data! }).catch(() => []);
      for (const t of invalid) await env.DB.prepare("UPDATE members SET fcm_token = NULL WHERE fcm_token = ?").bind(t).run();
    })());
    return json({ sent: tokens.length });
  }

  throw new HttpError(404, "not found");
}

export default {
  async fetch(req: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
    try {
      return await route(req, env, ctx);
    } catch (e) {
      if (e instanceof HttpError) return json({ error: e.message }, e.status);
      console.error(e);
      return json({ error: "internal" }, 500);
    }
  },
} satisfies ExportedHandler<Env>;
