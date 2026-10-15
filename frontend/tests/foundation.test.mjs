import { test } from "node:test";
import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { createServer } from "node:net";
import { once } from "node:events";

test("packaged frontend serves an honest foundation and no booking API", { timeout: 30000 }, async () => {
  const reservation = createServer();
  reservation.listen(0, "127.0.0.1");
  await once(reservation, "listening");
  const port = reservation.address().port;
  await new Promise(resolve => reservation.close(resolve));
  const child = spawn(process.execPath, ["node_modules/next/dist/bin/next", "start", "--hostname", "127.0.0.1", "--port", String(port)], { stdio: "pipe" });
  let output = "";
  child.stdout.on("data", b => { output += b; });
  child.stderr.on("data", b => { output += b; });
  try {
    let response;
    for (let i = 0; i < 100; i++) {
      if (child.exitCode !== null) throw new Error(output);
      try { response = await fetch(`http://127.0.0.1:${port}`); break; } catch { await new Promise(r => setTimeout(r, 100)); }
    }
    assert.ok(response, output);
    assert.equal(response.status, 200);
    const html = await response.text();
    assert.match(html, /<html lang="en"/);
    assert.match(html, /Development foundation/);
    assert.match(html, /Booking, account, payment and room controls are not available/);
    assert.doesNotMatch(html, /<form/);
    assert.equal(response.headers.get("x-powered-by"), null);
    assert.equal((await fetch(`http://127.0.0.1:${port}/api/bookings`)).status, 404);
  } finally {
    const stopped = once(child, "exit");
    child.kill("SIGTERM");
    await stopped;
  }
});
