// FactoryNetwork program share: anonymous upload, fetch by code, public list.
//
// Nothing about the uploader is stored — no IP, no account, no name. A
// program is text and never runs here; it only travels from one disk to
// another, and the game loads it into a draft that someone has to apply.

const MAX_FILES = 64;
const MAX_BYTES = 256 * 1024;
const MAX_TITLE = 60;
const PAGE = 20;
const HIDE_AFTER_REPORTS = 3;
const FILE_NAME = /^([a-z0-9_]{1,32}\/)*[a-z0-9_]{1,32}\.mf$/;
// Without 0/O, 1/I/L: a code read off a screen must not be ambiguous.
const ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    const parts = url.pathname.split("/").filter(Boolean);
    try {
      if (parts[0] !== "v1" || parts[1] !== "programs") {
        return json({ error: "not found" }, 404);
      }
      if (parts.length === 2 && request.method === "POST") {
        return await upload(request, env);
      }
      if (parts.length === 2 && request.method === "GET") {
        return await list(url, env);
      }
      if (parts.length === 3 && request.method === "GET") {
        return await fetchOne(parts[2], env);
      }
      if (parts.length === 4 && parts[3] === "report" && request.method === "POST") {
        return await report(parts[2], env);
      }
      return json({ error: "not found" }, 404);
    } catch (error) {
      return json({ error: "server error" }, 500);
    }
  },
};

function json(body, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json; charset=utf-8" },
  });
}

function newCode() {
  const bytes = new Uint8Array(6);
  crypto.getRandomValues(bytes);
  let code = "FN-";
  for (const byte of bytes) {
    code += ALPHABET[byte % ALPHABET.length];
  }
  return code;
}

/** Checks an upload; returns an error text or null. */
export function validate(body) {
  if (!body || typeof body !== "object") return "body must be an object";
  if (typeof body.title !== "string" || body.title.trim().length === 0) return "title missing";
  if (body.title.length > MAX_TITLE) return "title too long";
  if (!body.files || typeof body.files !== "object") return "files missing";
  const names = Object.keys(body.files);
  if (names.length === 0 || names.length > MAX_FILES) return "between 1 and 64 files";
  let bytes = 0;
  for (const name of names) {
    if (!FILE_NAME.test(name)) return "bad file name: " + name;
    if (typeof body.files[name] !== "string") return "file is not text: " + name;
    bytes += body.files[name].length;
  }
  if (bytes > MAX_BYTES) return "program too large";
  return null;
}

async function upload(request, env) {
  let body;
  try {
    body = await request.json();
  } catch {
    return json({ error: "not json" }, 400);
  }
  const problem = validate(body);
  if (problem) return json({ error: problem }, 400);
  const files = JSON.stringify(body.files);
  const lines = Object.values(body.files).reduce((sum, text) => sum + text.split("\n").length, 0);
  for (let attempt = 0; attempt < 5; attempt++) {
    const code = newCode();
    const result = await env.DB.prepare(
      "INSERT OR IGNORE INTO programs (code, title, files, file_count, lines, created) VALUES (?, ?, ?, ?, ?, ?)"
    ).bind(code, body.title.trim(), files, Object.keys(body.files).length, lines, Date.now()).run();
    if (result.meta.changes === 1) return json({ code }, 201);
  }
  return json({ error: "no free code" }, 503);
}

async function fetchOne(code, env) {
  const row = await env.DB.prepare(
    "SELECT code, title, files, created, downloads FROM programs WHERE code = ?"
  ).bind(code.toUpperCase()).first();
  if (!row) return json({ error: "unknown code" }, 404);
  await env.DB.prepare("UPDATE programs SET downloads = downloads + 1 WHERE code = ?").bind(row.code).run();
  return json({ code: row.code, title: row.title, files: JSON.parse(row.files),
    created: row.created, downloads: row.downloads + 1 });
}

async function list(url, env) {
  const sort = url.searchParams.get("sort") === "popular" ? "downloads DESC, created DESC" : "created DESC";
  const page = Math.max(0, parseInt(url.searchParams.get("page") || "0", 10) || 0);
  const query = (url.searchParams.get("q") || "").trim().slice(0, 40);
  const where = query ? "hidden = 0 AND title LIKE ?" : "hidden = 0";
  const statement = env.DB.prepare(
    `SELECT code, title, file_count, lines, downloads, created FROM programs WHERE ${where} ORDER BY ${sort} LIMIT ? OFFSET ?`
  );
  const bound = query
    ? statement.bind("%" + query.replace(/[%_]/g, "") + "%", PAGE, page * PAGE)
    : statement.bind(PAGE, page * PAGE);
  const { results } = await bound.all();
  return json({ page, programs: results });
}

async function report(code, env) {
  const result = await env.DB.prepare(
    `UPDATE programs SET reports = reports + 1, hidden = CASE WHEN reports + 1 >= ${HIDE_AFTER_REPORTS} THEN 1 ELSE hidden END WHERE code = ?`
  ).bind(code.toUpperCase()).run();
  if (result.meta.changes === 0) return json({ error: "unknown code" }, 404);
  return json({ reported: true });
}
