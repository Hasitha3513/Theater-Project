import { test } from "node:test";
import { createServer } from "node:http";
import { once } from "node:events";
import assert from "node:assert/strict";
import { createElement as h } from "react";
import { renderToStaticMarkup as render } from "react-dom/server";
import { RoomSummary, RoomPhoto, clock } from "../components/rooms.tsx";
import { roomRoute, forwardRooms } from "../lib/room-backend.ts";
import {
	roomRequest,
	RoomRequestError,
	stateFor,
	queryRooms,
} from "../lib/rooms.ts";
const id = "00000000-0000-4000-8000-000000000001";
const room = {
	roomId: id,
	venueId: id,
	code: "SYNTHETIC",
	name: "Synthetic room",
	description: "HDMI input",
	capacity: 4,
	version: 0,
	enabled: true,
	configured: true,
	facilities: [],
	images: [],
	permittedActions: ["room.read"],
};
test("room cards distinguish catalog configuration from readiness and hide unauthorized editing", () => {
	const html = render(h(RoomSummary, { room, admin: true }));
	assert.match(html, /Catalog enabled/);
	assert.match(html, /photograph unavailable/);
	assert.doesNotMatch(html, /Edit room|Available now|Book now/);
	assert.match(
		render(
			h(RoomSummary, {
				room: { ...room, permittedActions: ["room.write"] },
				admin: true,
			}),
		),
		/Edit room/,
	);
});
test("public cards expose neutral discovery only and escape descriptions", () => {
	const html = render(
		h(RoomSummary, {
			room: { ...room, name: "<script>unsafe</script>" },
			admin: false,
		}),
	);
	assert.match(html, /&lt;script&gt;/);
	assert.match(html, /Room details/);
	assert.doesNotMatch(html, /Edit room|permittedActions/);
});
test("photo fallback is explicit and midnight rendering is not a policy default", () => {
	assert.match(render(h(RoomPhoto, { room })), /photograph unavailable/);
	assert.equal(clock(1440), "24:00");
	assert.equal(clock(610), "10:10");
});
test("card and table filters share a canonical backend query without view parameter", () => {
	const q = queryRooms(id, "%_", "5", "HDMI", 2, "false");
	const params = new URLSearchParams(q);
	assert.equal(params.get("q"), "%_");
	assert.equal(params.get("venueId"), id);
	assert.equal(params.get("enabled"), "false");
	assert.equal(params.has("view"), false);
});
test("adapter accepts only approved eleven endpoint methods", () => {
	for (const path of [
		"/api/catalog/venues",
		"/api/catalog/rooms",
		`/api/catalog/rooms/${id}`,
		"/api/admin/venues",
		`/api/admin/venues/${id}`,
		`/api/admin/venues/${id}/rooms`,
		`/api/admin/venues/${id}/rooms/${id}`,
	])
		assert.equal(roomRoute(path, "GET"), true);
	assert.equal(roomRoute("/api/admin/venues", "POST"), true);
	assert.equal(roomRoute(`/api/admin/venues/${id}`, "PUT"), true);
	for (const [path, method] of [
		["/api/catalog/rooms", "POST"],
		[`/api/admin/venues/${id}`, "DELETE"],
		["/api/bookings", "GET"],
		["/api/admin/venues/not-a-uuid", "GET"],
	])
		assert.equal(roomRoute(path, method), false);
});
test("adapter rejects unconfigured service, cross-origin writes, missing CSRF and arbitrary proxy paths", async () => {
	const previous = process.env.CINENEST_AUTH_ORIGIN;
	try {
		delete process.env.CINENEST_AUTH_ORIGIN;
		assert.equal(
			(await forwardRooms(new Request("http://localhost/api/catalog/venues")))
				.status,
			503,
		);
		process.env.CINENEST_AUTH_ORIGIN = "http://127.0.0.1:9999";
		assert.equal(
			(
				await forwardRooms(
					new Request("http://localhost/api/admin/venues", {
						method: "POST",
						headers: { origin: "https://foreign.invalid" },
					}),
				)
			).status,
			403,
		);
		assert.equal(
			(
				await forwardRooms(
					new Request("http://localhost/api/admin/venues", {
						method: "POST",
						headers: { origin: "http://localhost" },
					}),
				)
			).status,
			403,
		);
		assert.equal(
			(await forwardRooms(new Request("http://localhost/api/payments"))).status,
			404,
		);
	} finally {
		if (previous === undefined) delete process.env.CINENEST_AUTH_ORIGIN;
		else process.env.CINENEST_AUTH_ORIGIN = previous;
	}
});
test("client writes use existing session CSRF and preserve explicit version", async () => {
	const original = global.fetch;
	const calls = [];
	try {
		global.fetch = async (path, init) => {
			calls.push([path, init]);
			return Response.json(
				path === "/api/session/csrf"
					? { token: "synthetic-token" }
					: { version: 2 },
			);
		};
		await roomRequest(`/api/admin/venues/${id}`, {
			method: "PUT",
			body: JSON.stringify({ expectedVersion: 1 }),
		});
		assert.equal(calls[0][0], "/api/session/csrf");
		assert.equal(calls[1][1].headers.get("x-csrf-token"), "synthetic-token");
		assert.match(calls[1][1].body, /expectedVersion/);
	} finally {
		global.fetch = original;
	}
});
test("denied, expired, failed and conflicting requests remain failures rather than mock success", async () => {
	const original = global.fetch;
	try {
		for (const status of [400, 401, 403, 409, 503]) {
			global.fetch = async () =>
				Response.json({ message: "Synthetic failure" }, { status });
			await assert.rejects(
				roomRequest("/api/catalog/rooms"),
				(e) => e instanceof RoomRequestError && e.status === status,
			);
		}
		assert.equal(stateFor(new RoomRequestError(403, "no")), "denied");
		assert.equal(stateFor(new RoomRequestError(401, "no")), "unauthenticated");
	} finally {
		global.fetch = original;
	}
});

test("room adapter uses real HTTP and strips unrelated cookie, bearer and query authority",async()=>{
 const previous=process.env.CINENEST_AUTH_ORIGIN;const received=[];
 const server=createServer((request,response)=>{received.push({url:request.url,headers:request.headers,method:request.method});response.writeHead(200,{"Content-Type":"application/json"});response.end(JSON.stringify({items:[],total:0,page:0,size:24}));});
 server.listen(0,"127.0.0.1");await once(server,"listening");process.env.CINENEST_AUTH_ORIGIN=`http://127.0.0.1:${server.address().port}`;
 try{const response=await forwardRooms(new Request("http://localhost/api/catalog/rooms?q=synthetic&role=SUPERADMIN",{headers:{cookie:"secret=excluded; CINENEST_SESSION=synthetic-cookie",authorization:"Bearer excluded"}}));assert.equal(response.status,200);assert.equal(received[0].headers.cookie,"CINENEST_SESSION=synthetic-cookie");assert.equal(received[0].headers.authorization,undefined);assert.equal(received[0].url.includes("role="),false);assert.equal(response.headers.get("cache-control"),"no-store");}
 finally{if(previous===undefined)delete process.env.CINENEST_AUTH_ORIGIN;else process.env.CINENEST_AUTH_ORIGIN=previous;await new Promise(r=>server.close(r));}
});
