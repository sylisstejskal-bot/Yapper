import {
  OLLAMA_FALLBACK_MODEL,
  clipReply,
  cohereRequest,
  cohereV1Request,
  failureKind,
  ollamaChatRequest,
  ollamaTagsUrl,
  readCohereReply,
  readOllamaModel,
  readOllamaReply,
} from "./logic.js";

async function loadServerNet() {
  // Split so the pack does not statically depend on a dedicated-server module.
  const moduleName = "@minecraft/" + "server-net";
  return import(moduleName);
}

function applyMethod(request, method) {
  if (typeof request.setMethod === "function") request.setMethod(method);
  else request.method = method;
}

function applyBody(request, body) {
  if (body === undefined) return;
  const payload = JSON.stringify(body);
  if (typeof request.setBody === "function") request.setBody(payload);
  else request.body = payload;
}

function applyHeaders(request, net, headers) {
  const built = headers.map(([name, value]) => new net.HttpHeader(name, value));
  request.headers = built;
  if (typeof request.setHeaders === "function") {
    try {
      request.setHeaders(built);
    } catch (error) {
      // The headers array is already set.
    }
  }
  if (typeof request.addHeader === "function") {
    try {
      for (const [name, value] of headers) request.addHeader(name, value);
    } catch (error) {
      // Property headers remain in place.
    }
  }
}

async function send(net, url, method, headers, body) {
  const request = new net.HttpRequest(url);
  applyBody(request, body);
  applyMethod(request, method);
  applyHeaders(request, net, headers);
  const response = await net.http.request(request);
  return { status: Number(response.status), body: response.body };
}

async function postBuilt(net, built) {
  const response = await send(net, built.url, net.HttpRequestMethod.Post, built.headers, built.body);
  if (response.status === 401 || response.status === 403) return { ok: false, reason: "denied", status: response.status };
  if (response.status < 200 || response.status >= 300) {
    return { ok: false, reason: failureKind(response.status, response.body), status: response.status };
  }
  const text = clipReply(readCohereReply(response.body));
  if (!text) return { ok: false, reason: "empty" };
  return { ok: true, text };
}

export async function askObesity(kind, secret, turns, hunger) {
  let net;
  try {
    net = await loadServerNet();
  } catch (error) {
    return { ok: false, reason: "no-net" };
  }
  try {
    if (kind === "cohere") {
      let last = { ok: false, reason: "failed" };
      for (const built of [cohereV1Request(secret, turns, hunger), cohereRequest(secret, turns, hunger)]) {
        try {
          const result = await postBuilt(net, built);
          if (result.ok || result.reason === "denied" || result.reason === "blocked") return result;
          last = result;
        } catch (error) {
          const message = error && error.message ? error.message : error;
          last = { ok: false, reason: failureKind(0, message) };
          if (last.reason === "blocked") return last;
        }
      }
      return last;
    }
    const tags = await send(net, ollamaTagsUrl(secret), net.HttpRequestMethod.Get, [], undefined);
    let model = OLLAMA_FALLBACK_MODEL;
    if (tags.status >= 200 && tags.status < 300) {
      try {
        model = readOllamaModel(tags.body) || model;
      } catch (error) {
        model = OLLAMA_FALLBACK_MODEL;
      }
    }
    const built = ollamaChatRequest(secret, model, turns, hunger);
    const response = await send(net, built.url, net.HttpRequestMethod.Post, built.headers, built.body);
    if (response.status < 200 || response.status >= 300) return { ok: false, reason: "http", status: response.status };
    const text = clipReply(readOllamaReply(response.body));
    if (!text) return { ok: false, reason: "empty" };
    return { ok: true, text };
  } catch (error) {
    const message = error && error.message ? error.message : error;
    return { ok: false, reason: failureKind(0, message) };
  }
}
