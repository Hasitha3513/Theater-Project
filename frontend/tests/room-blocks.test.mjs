import test from "node:test";
import assert from "node:assert/strict";
import React from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { blockPath, parseBlock, parseBlockList } from "../lib/room-blocks.ts";
import { RoomRequestError } from "../lib/rooms.ts";
import { forwardRoomBlocks } from "../lib/room-backend.ts";
import {
	RoomBlocking,
	blockDateTime,
	validateBlockInputs,
	blockLocalTime,
	blockFeedback,
	blockWriteFailure,
	prepareBlockCommand,
	readBlockContext,
	sendBlockCommand,
	SavedBlockDetail,
} from "../components/room-blocking.tsx";
const venue = "10000000-0000-4000-8000-000000000001",
	room = "10000000-0000-4000-8000-000000000002",
	id = "10000000-0000-4000-8000-000000000003";
const block = {
	blockId: id,
	venueId: venue,
	roomId: room,
	startAt: "2030-01-01T00:00:00Z",
	endAt: "2030-01-01T01:00:00Z",
	revision: 0,
	createdAt: "2026-10-30T00:00:00Z",
	synthetic: true,
};
test("Room Blocking binds exact scope and rejects unsafe references", () => {
	assert.equal(
		blockPath(venue, room),
		`/api/admin/venues/${venue}/rooms/${room}/blocks`,
	);
	for (const value of ["../payments", `${room}?actor=other`, "unknown"])
		assert.throws(() => blockPath(venue, value));
	assert.throws(() => parseBlock({ ...block, venueId: room }, venue, room));
	assert.throws(() =>
		parseBlock({ ...block, endAt: block.startAt }, venue, room),
	);
	assert.throws(() => parseBlock({ ...block, revision: 1 }, venue, room));
});
test("allowlisted DTOs strip private fields and require synthetic evidence", () => {
	assert.deepEqual(
		parseBlock(
			{ ...block, actor_id: "PRIVATE", request_hash: "PRIVATE" },
			venue,
			room,
		),
		block,
	);
	const list = {
		items: [block],
		page: 0,
		size: 24,
		configuration: "2:3:fixture",
		timezone: "Asia/Colombo",
		observedAt: block.createdAt,
		synthetic: true,
	};
	assert.equal(parseBlockList(list, venue, room).items.length, 1);
	for (const bad of [
		{ ...list, synthetic: false },
		{ ...list, timezone: "INVALID" },
		{ ...list, size: 101 },
		{ ...list, configuration: "" },
	])
		assert.throws(() => parseBlockList(bad, venue, room));
});
test("proxy rejects unsupported methods, details mutations and injected authority", async () => {
	const path = `http://127.0.0.1${blockPath(venue, room)}`;
	assert.equal(
		(await forwardRoomBlocks(new Request(path, { method: "DELETE" }))).status,
		404,
	);
	assert.equal(
		(await forwardRoomBlocks(new Request(`${path}/${id}`, { method: "POST" })))
			.status,
		404,
	);
	assert.equal(
		(
			await forwardRoomBlocks(
				new Request(path, { headers: { "x-role": "ADMIN" } }),
			)
		).status,
		400,
	);
	assert.equal(
		(await forwardRoomBlocks(new Request(`${path}?accountId=other`))).status,
		400,
	);
});
test("UI preserves disabled/default and no-reopening distinction", () => {
	const html = renderToStaticMarkup(React.createElement(RoomBlocking));
	assert.match(html, /Room Blocking/);
	assert.match(html, /cannot reopen/);
	assert.match(html, /Start date/);
	assert.match(html, /Start time \(24-hour\)/);
	assert.match(html, /End date/);
	assert.match(html, /End time \(24-hour\)/);
	assert.match(html, /No default timezone is assumed/);
	assert.match(html, /Find venues by name/);
	assert.match(html, /Load room blocks/);
	assert.doesNotMatch(html, /Unblock room|Cancel booking|Publish/);
});

const validInput = {
	venue,
	room,
	startDate: "2030-01-03",
	startTime: "10:00",
	endDate: "2030-01-03",
	endTime: "12:00",
};
for (const [zone, date, time, expected] of [
	["Asia/Colombo", "2030-01-03", "10:00", "2030-01-03T04:30:00.000Z"],
	["Asia/Kathmandu", "2030-01-03", "10:00", "2030-01-03T04:15:00.000Z"],
	["UTC", "2030-01-03", "10:00", "2030-01-03T10:00:00.000Z"],
	["America/New_York", "2030-01-03", "10:00", "2030-01-03T15:00:00.000Z"],
	["America/New_York", "2030-07-03", "10:00", "2030-07-03T14:00:00.000Z"],
])
	test(`venue timezone conversion: ${zone} ${date}`, () =>
		assert.equal(blockDateTime(date, time, zone), expected));
test("conversion never uses the operator computer timezone", () => {
	const old = process.env.TZ;
	try {
		process.env.TZ = "Pacific/Honolulu";
		assert.equal(
			blockDateTime("2030-01-03", "10:00", "Asia/Colombo"),
			"2030-01-03T04:30:00.000Z",
		);
	} finally {
		if (old === undefined) delete process.env.TZ;
		else process.env.TZ = old;
	}
});
for (const [date, time] of [
	["2030-02-30", "10:00"],
	["2030-13-01", "10:00"],
	["03/01/2030", "10:00"],
	["", "10:00"],
	["2030-01-03", "24:00"],
	["2030-01-03", "10:60"],
	["2030-01-03", "10:00Z"],
	["2030-01-03", ""],
])
	test(`reject unsupported date/time ${date} ${time}`, () =>
		assert.throws(() => blockDateTime(date, time, "Asia/Colombo")));
test("DST gaps and ambiguous folds fail closed instead of guessing an offset", () => {
	assert.throws(
		() => blockDateTime("2030-03-10", "02:30", "America/New_York"),
		/does not exist/,
	);
	assert.throws(
		() => blockDateTime("2030-11-03", "01:30", "America/New_York"),
		/occurs twice/,
	);
	assert.throws(
		() => blockDateTime("2011-12-30", "10:00", "Pacific/Apia"),
		/does not exist/,
	);
	assert.throws(() => blockDateTime("2030-01-03", "10:00", "UNSUPPORTED"));
});
test("missing references, dates, times and loaded timezone have specific messages", () => {
	const result = validateBlockInputs({
		venue: "",
		room: "",
		startDate: "",
		startTime: "",
		endDate: "",
		endTime: "",
	});
	for (const name of [
		"venue",
		"room",
		"startDate",
		"startTime",
		"endDate",
		"endTime",
		"context",
	])
		assert.ok(result.errors[name], name);
	assert.equal(result.startAt, undefined);
	assert.equal(result.endAt, undefined);
});
test("valid intervals preserve exact venue-local instants and reject reversed/equal bounds", () => {
	const result = validateBlockInputs(validInput, "Asia/Colombo");
	assert.deepEqual(result.errors, {});
	assert.equal(result.startAt, "2030-01-03T04:30:00.000Z");
	assert.equal(result.endAt, "2030-01-03T06:30:00.000Z");
	for (const endTime of ["09:00", "10:00"])
		assert.match(
			validateBlockInputs({ ...validInput, endTime }, "Asia/Colombo").errors
				.endTime,
			/after/,
		);
	assert.deepEqual(
		validateBlockInputs(
			{
				...validInput,
				startTime: "23:00",
				endDate: "2030-01-04",
				endTime: "01:00",
			},
			"Asia/Colombo",
		).errors,
		{},
	);
	assert.ok(
		validateBlockInputs(
			{ ...validInput, venue: "../other", room: "unknown" },
			"Asia/Colombo",
		).errors.venue,
	);
});
test("saved detail renders local time plus untouched timestamps, identifier and revision", () => {
	const html = renderToStaticMarkup(
		React.createElement(SavedBlockDetail, { block, timezone: "Asia/Colombo" }),
	);
	assert.match(html, /1 January 2030/);
	assert.match(html, /05:30:00/);
	assert.match(html, /GMT\+05:30/);
	for (const value of [
		block.blockId,
		block.startAt,
		block.endAt,
		block.createdAt,
	])
		assert.ok(html.includes(value));
	assert.match(html, /Revision 0/);
	assert.match(html, /synthetic inventory evidence only/);
	assert.match(html, /Creator identity is not supplied/);
	assert.match(blockLocalTime(block.startAt, "Asia/Colombo"), /05:30/);
});
test("actual response classifications distinguish authentication, permission, stale context and inventory conflicts", () => {
	assert.match(
		blockFeedback(new RoomRequestError(401, "ignored")),
		/session expired/,
	);
	assert.match(
		blockFeedback(new RoomRequestError(403, "PRIVATE authority")),
		/Access denied/,
	);
	assert.doesNotMatch(
		blockFeedback(new RoomRequestError(403, "PRIVATE authority")),
		/PRIVATE/,
	);
	assert.match(
		blockFeedback(
			new RoomRequestError(
				409,
				"Room configuration changed; reload before creating a block",
			),
		),
		/Room configuration changed/,
	);
	assert.match(
		blockFeedback(
			new RoomRequestError(409, "Inventory conflict; no booking was changed"),
		),
		/overlaps existing inventory/,
	);
	assert.match(
		blockFeedback(new RoomRequestError(409, "unknown")),
		/conflicts or its configuration changed/,
	);
	assert.match(
		blockFeedback(new RoomRequestError(503, "ignored")),
		/could not complete/,
	);
});
test("uncertain outcomes retain the original command; definite rejections do not silently retry", () => {
	const command = prepareBlockCommand(
		venue,
		room,
		block.startAt,
		block.endAt,
		"fixture",
		id,
	);
	for (const error of [
		new TypeError("network"),
		new RoomRequestError(500, "unknown"),
		new RoomRequestError(503, "unavailable"),
	]) {
		const result = blockWriteFailure(error, command);
		assert.equal(result.pending, command);
		assert.match(result.message, /no automatic retry/);
	}
	for (const status of [400, 401, 403, 404, 409, 413])
		assert.equal(
			blockWriteFailure(new RoomRequestError(status, "ignored"), command)
				.pending,
			undefined,
		);
	assert.throws(() =>
		prepareBlockCommand(venue, room, "not-a-date", block.endAt, "fixture", id),
	);
	assert.throws(() =>
		prepareBlockCommand(venue, room, block.startAt, block.endAt, "", id),
	);
});
test("create and explicit reload use the existing CSRF/scoped API contract", async () => {
	const old = globalThis.fetch,
		calls = [];
	const list = {
		items: [block],
		page: 0,
		size: 24,
		configuration: "2:3:fixture",
		timezone: "Asia/Colombo",
		observedAt: block.createdAt,
		synthetic: true,
	};
	try {
		globalThis.fetch = async (path, init = {}) => {
			calls.push({ path, init });
			return Response.json(
				path === "/api/session/csrf"
					? { token: "synthetic-csrf" }
					: init.method === "POST"
						? block
						: list,
			);
		};
		const command = prepareBlockCommand(
			venue,
			room,
			block.startAt,
			block.endAt,
			list.configuration,
			id,
		);
		assert.deepEqual(JSON.parse(command.body), {
			idempotencyKey: id,
			startAt: block.startAt,
			endAt: block.endAt,
			expectedConfiguration: list.configuration,
		});
		assert.equal((await sendBlockCommand(command, venue, room)).blockId, id);
		assert.equal((await readBlockContext(venue, room)).items[0].blockId, id);
		assert.deepEqual(
			calls.map((c) => c.path),
			[
				"/api/session/csrf",
				blockPath(venue, room),
				`${blockPath(venue, room)}?page=0&size=24`,
			],
		);
		assert.equal(
			new Headers(calls[1].init.headers).get("X-CSRF-TOKEN"),
			"synthetic-csrf",
		);
		assert.equal(calls[1].init.body, command.body);
	} finally {
		globalThis.fetch = old;
	}
});
test("network interruption sends no automatic second POST and explicit retry keeps identical payload", async () => {
	const old = globalThis.fetch,
		bodies = [];
	let fail = true;
	try {
		globalThis.fetch = async (path, init = {}) => {
			if (path === "/api/session/csrf")
				return Response.json({ token: "synthetic-csrf" });
			bodies.push(init.body);
			if (fail) throw new TypeError("response interrupted");
			return Response.json(block);
		};
		const command = prepareBlockCommand(
			venue,
			room,
			block.startAt,
			block.endAt,
			"fixture",
			id,
		);
		await assert.rejects(sendBlockCommand(command, venue, room));
		assert.equal(bodies.length, 1);
		const retry = blockWriteFailure(
			new TypeError("response interrupted"),
			command,
		).pending;
		fail = false;
		await sendBlockCommand(retry, venue, room);
		assert.deepEqual(bodies, [command.body, command.body]);
	} finally {
		globalThis.fetch = old;
	}
});
test("a server conflict reaches the real classification path without claiming success", async () => {
	const old = globalThis.fetch;
	try {
		globalThis.fetch = async (path) =>
			path === "/api/session/csrf"
				? Response.json({ token: "synthetic-csrf" })
				: Response.json(
						{ message: "Inventory conflict; no booking was changed" },
						{ status: 409 },
					);
		const command = prepareBlockCommand(
			venue,
			room,
			block.startAt,
			block.endAt,
			"fixture",
			id,
		);
		await assert.rejects(
			sendBlockCommand(command, venue, room),
			(error) =>
				error instanceof RoomRequestError &&
				error.status === 409 &&
				blockFeedback(error).includes("overlaps existing inventory"),
		);
	} finally {
		globalThis.fetch = old;
	}
});
test("invalid values attach errors to the correct date or time field", () => {
	const badTime = validateBlockInputs(
		{ ...validInput, startTime: "10:60" },
		"Asia/Colombo",
	);
	assert.match(badTime.errors.startTime, /24-hour/);
	assert.equal(badTime.errors.startDate, undefined);
	const badDate = validateBlockInputs(
		{ ...validInput, startDate: "2030-02-30" },
		"Asia/Colombo",
	);
	assert.match(badDate.errors.startDate, /real calendar date/);
	const badFormat = validateBlockInputs(
		{ ...validInput, startDate: "03/01/2030" },
		"Asia/Colombo",
	);
	assert.match(badFormat.errors.startDate, /YYYY-MM-DD/);
	const fold = validateBlockInputs(
		{
			...validInput,
			startDate: "2030-11-03",
			startTime: "01:30",
			endDate: "2030-11-03",
			endTime: "03:00",
		},
		"America/New_York",
	);
	assert.match(fold.errors.startTime, /occurs twice/);
});
test("uncertain command retains its original timezone along with immutable payload", () => {
	const command = {
		...prepareBlockCommand(
			venue,
			room,
			block.startAt,
			block.endAt,
			"fixture",
			id,
		),
		timezone: "Asia/Colombo",
	};
	const result = blockWriteFailure(new TypeError("interrupted"), command);
	assert.equal(result.pending, command);
	assert.equal(result.pending.timezone, "Asia/Colombo");
	assert.equal(result.pending.body, command.body);
});
