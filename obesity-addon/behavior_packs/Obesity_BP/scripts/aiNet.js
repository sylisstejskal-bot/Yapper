import {
  OLLAMA_FALLBACK_MODEL,
  clipReply,
  cohereRequest,
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

async function send(net, url, method, headers, body) {
  const request = new net.HttpRequest(url);
  request.method = method;
  request.timeout = 12;
  if (body !== undefined) request.body = JSON.stringify(body);
  request.headers = headers.map(([name, value]) => new net.HttpHeader(name, value));
  const response = await net.http.request(request);
  return { status: Number(response.status), body: response.body };
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
      const built = cohereRequest(secret, turns, hunger);
      const response = await send(net, built.url, net.HttpRequestMethod.Post, built.headers, built.body);
      if (response.status < 200 || response.status >= 300) return { ok: false, reason: "http", status: response.status };
      const text = clipReply(readCohereReply(response.body));
      if (!text) return { ok: false, reason: "empty" };
      return { ok: true, text };
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
    return { ok: false, reason: "failed" };
  }
}
