// Explicit local-development entry point. Never started by the application or CI.
import https from 'node:https';
import http from 'node:http';
import { readFileSync, lstatSync, realpathSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

export const ORIGIN = 'https://localhost:4443';
const authority = new URL(ORIGIN).host;
const forbidden = /^(forwarded|x-forwarded-.*|x-original-.*|x-rewrite-.*|proxy-.*)$/i;

export function admitted(request) {
  let path;
  try { path = decodeURIComponent((request.url ?? '').split('?', 1)[0]); } catch { return false; }
  return request.headers.host === authority &&
    (!request.rawHeaders || request.rawHeaders.filter((name,index) => index % 2 === 0 && name.toLowerCase() === 'host').length === 1) &&
    !Object.keys(request.headers).some(name => forbidden.test(name)) &&
    (!request.headers.origin || request.headers.origin === ORIGIN) &&
    /^\/(?!\/)/.test(path) && !/[\\\u0000-\u0020\u007f]/.test(path) &&
    ['GET', 'HEAD', 'POST', 'PUT', 'PATCH', 'DELETE', 'OPTIONS'].includes(request.method);
}

export function createIngress(tls, upstreamPort) {
  if (!Number.isInteger(upstreamPort) || upstreamPort < 1024 || upstreamPort > 65535 || upstreamPort === 4443)
    throw new Error('Explicit unprivileged loopback upstream port required');
  const server = https.createServer({ ...tls, minVersion: 'TLSv1.2' }, (request, response) => {
    response.setHeader('Cache-Control', 'no-store');
    if (!admitted(request)) { response.writeHead(400); response.end(); return; }
    const headers = { ...request.headers, host: authority,
      'x-forwarded-host': authority, 'x-forwarded-proto': 'https',
      'x-forwarded-port': '4443', 'x-forwarded-for': '127.0.0.1' };
    delete headers.connection;
    const upstream = http.request({ hostname: '127.0.0.1', port: upstreamPort,
      path: request.url, method: request.method, headers, timeout: 15000 }, incoming => {
      response.writeHead(incoming.statusCode ?? 502, incoming.headers);
      incoming.pipe(response);
      incoming.on('error', () => response.destroy());
    });
    upstream.on('timeout', () => upstream.destroy());
    upstream.on('error', () => { if (!response.headersSent) response.writeHead(503); response.end(); });
    request.on('aborted', () => upstream.destroy());
    request.pipe(upstream);
  });
  server.requestTimeout = 30000;
  server.headersTimeout = 10000;
  server.on('upgrade', (_request, socket) => socket.destroy());
  return server;
}

function privateKey(path) {
  if (!path || resolve(path) !== path || realpathSync(path) !== path ||
      !lstatSync(path).isFile() || (lstatSync(path).mode & 0o777) !== 0o600 ||
      (lstatSync(dirname(path)).mode & 0o777) !== 0o700)
    throw new Error('Private canonical key file requires 0600 and parent 0700');
  return readFileSync(path);
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  try {
    if (process.env.CINENEST_LOCAL_HTTPS !== 'true') throw new Error('Local HTTPS is disabled');
    const key = privateKey(process.env.CINENEST_LOCAL_HTTPS_KEY);
    const cert = readFileSync(process.env.CINENEST_LOCAL_HTTPS_CERT);
    const server = createIngress({ key, cert }, Number(process.env.CINENEST_LOCAL_HTTPS_UPSTREAM_PORT));
    server.listen(4443, '127.0.0.1', () => console.log(`Local-only HTTPS ingress: ${ORIGIN}; provider qualification NOT established`));
    for (const signal of ['SIGINT', 'SIGTERM']) process.on(signal, () => server.close());
  } catch { console.error('Local HTTPS ingress configuration invalid; no secrets logged'); process.exitCode = 1; }
}
