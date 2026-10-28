import test from 'node:test';
import assert from 'node:assert/strict';
import { admitted, createIngress, ORIGIN } from './local-https-ingress.mjs';
import http from 'node:http';
import https from 'node:https';
import { mkdtempSync, readFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { execFileSync } from 'node:child_process';

test('standalone ingress remains disabled without explicit opt-in', () => {
  const env={...process.env}; delete env.CINENEST_LOCAL_HTTPS;
  assert.throws(()=>execFileSync(process.execPath,['scripts/local-https-ingress.mjs'],{env,stdio:'pipe'}),error=>error.status===1 && !error.stderr.toString().includes('PRIVATE KEY'));
});

test('exact authority, same-origin and trusted header boundary', () => {
  const request = (headers = {}, url = '/login', method = 'GET') => ({ headers: { host: 'localhost:4443', ...headers }, url, method });
  assert.equal(admitted(request()), true);
  assert.equal(admitted(request({ origin: ORIGIN }, '/api/auth/login', 'POST')), true);
  for (const headers of [{host:'evil.invalid'}, {host:'localhost:4443.evil.invalid'}, {origin:'http://localhost:4443'}, {origin:'null'}, {forwarded:'proto=https'}, {'x-forwarded-host':'evil.invalid'}, {'x-forwarded-proto':'https'}, {'x-original-url':'/account'}])
    assert.equal(admitted(request(headers)), false);
  for (const path of ['//evil.invalid', 'https://evil.invalid', '', '/\\evil.invalid', '/%2f%2fevil.invalid', '/%5cevil.invalid', '/%zz']) assert.equal(admitted(request({}, path)), false);
  assert.equal(admitted({...request(),rawHeaders:['Host','localhost:4443','Host','evil.invalid']}),false);
  assert.equal(admitted(request({}, '/', 'CONNECT')), false);
  assert.throws(() => createIngress({}, 4443));
});

test('real TLS verification, sanitized upstream headers, secure cookie and denial', async () => {
  const dir = mkdtempSync(join(tmpdir(), 'cinenest-tls-test-'));
  let ingress, upstream;
  try {
    // Ephemeral security-test certificate, explicitly trusted only by this test client.
    execFileSync('openssl', ['req','-x509','-newkey','rsa:2048','-nodes','-days','1','-subj','/CN=localhost','-addext','subjectAltName=DNS:localhost','-keyout',join(dir,'key.pem'),'-out',join(dir,'cert.pem')], {stdio:'ignore'});
    const cert = readFileSync(join(dir,'cert.pem'));
    let calls = 0;
    upstream = http.createServer((req,res) => {
      calls++;
      assert.equal(req.headers.host,'localhost:4443');
      assert.equal(req.headers['x-forwarded-proto'],'https');
      assert.equal(req.headers['x-forwarded-host'],'localhost:4443');
      assert.equal(req.headers.forwarded,undefined);
      res.setHeader('Set-Cookie','CINENEST_SESSION=synthetic; Secure; HttpOnly; SameSite=Lax; Path=/');
      res.end('synthetic');
    });
    await new Promise(resolve => upstream.listen(0,'127.0.0.1',resolve));
    ingress = createIngress({key:readFileSync(join(dir,'key.pem')),cert},upstream.address().port);
    await new Promise(resolve => ingress.listen(0,'127.0.0.1',resolve));
    const request = (headers={},ca=cert) => new Promise((resolve,reject) => {
      const req=https.get({hostname:'127.0.0.1',servername:'localhost',port:ingress.address().port,path:'/login',ca,headers:{Host:'localhost:4443',...headers}},res=>{res.resume();res.on('end',()=>resolve(res));});
      req.on('error',reject);
    });
    const response=await request();
    assert.equal(response.statusCode,200);
    assert.match(response.headers['set-cookie'][0],/Secure; HttpOnly; SameSite=Lax/);
    assert.equal((await request({'x-forwarded-proto':'https'})).statusCode,400);
    assert.equal((await request({Host:'evil.invalid'})).statusCode,400);
    assert.equal((await request({Origin:'https://evil.invalid'})).statusCode,400);
    assert.equal(calls,1);
    await assert.rejects(request({},null));
  } finally {
    if(ingress) await new Promise(resolve=>ingress.close(resolve));
    if(upstream) await new Promise(resolve=>upstream.close(resolve));
    rmSync(dir,{recursive:true,force:true});
  }
});
