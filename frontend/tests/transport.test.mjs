import { test } from "node:test";
import assert from "node:assert/strict";
import { createServer } from "node:http";
import { spawn } from "node:child_process";
import { once } from "node:events";

test("packaged session adapter forwards only existing routes/cookie and preserves CSRF/logout denial", { timeout: 30000 }, async () => {
 const id="00000000-0000-4000-8000-000000000001";const calls=[];
 const backend=createServer((req,res)=>{
  calls.push({url:req.url,cookie:req.headers.cookie,authorization:req.headers.authorization,csrf:req.headers["x-csrf-token"]});
  if(req.url==="/api/bookings"){res.writeHead(401);res.end();return;}
  if(req.url==="/api/session/logout"){res.writeHead(req.headers["x-csrf-token"]==="synthetic-csrf"?204:403);res.end();return;}
  res.setHeader("Content-Type","application/json");
  if(req.url==="/api/session/csrf")res.end(JSON.stringify({token:"synthetic-csrf",headerName:"X-CSRF-TOKEN"}));
  else res.end(JSON.stringify({accountId:id,eligibility:"ELIGIBLE",privateField:"must-not-forward"}));
 });backend.listen(0,"127.0.0.1");await once(backend,"listening");
 const reserve=createServer();reserve.listen(0,"127.0.0.1");await once(reserve,"listening");const port=reserve.address().port;await new Promise(r=>reserve.close(r));
 const base=`http://127.0.0.1:${port}`;
 const child=spawn(process.execPath,["node_modules/next/dist/bin/next","start","--hostname","127.0.0.1","--port",String(port)],{stdio:"pipe",env:{...process.env,CINENEST_AUTH_ORIGIN:`http://127.0.0.1:${backend.address().port}`}});
 let output="";child.stdout.on("data",b=>output+=b);child.stderr.on("data",b=>output+=b);
 try{
  let ready=false;for(let i=0;i<100;i++){if(child.exitCode!==null)throw Error(output);try{await fetch(base);ready=true;break;}catch{await new Promise(r=>setTimeout(r,100));}}assert.ok(ready);
  const response=await fetch(`${base}/api/accounts/me?owner=other`,{headers:{Cookie:"other=private; CINENEST_SESSION=synthetic-session",Authorization:"Bearer ignored"}});
  assert.equal(response.status,200);assert.match(response.headers.get("cache-control"),/no-store/);assert.deepEqual(await response.json(),{accountId:id,eligibility:"ELIGIBLE"});
  assert.deepEqual(calls[0],{url:"/api/accounts/me",cookie:"CINENEST_SESSION=synthetic-session",authorization:undefined,csrf:undefined});
  const count=calls.length;
  assert.equal((await fetch(`${base}/api/session/logout`,{method:"POST",headers:{Origin:"https://other.invalid","X-CSRF-TOKEN":"synthetic-csrf"}})).status,403);assert.equal(calls.length,count);
  const csrf=await(await fetch(`${base}/api/session/csrf`)).json();
  assert.equal((await fetch(`${base}/api/session/logout`,{method:"POST",headers:{Origin:base,[csrf.headerName]:csrf.token}})).status,204);
  assert.equal(calls.at(-1).csrf,"synthetic-csrf");
  assert.equal((await fetch(`${base}/api/bookings`)).status,401);
 }finally{const stopped=once(child,"exit");child.kill("SIGTERM");await stopped;await new Promise(r=>backend.close(r));}
});
