// このファイルの責務: Java Plugin との WebSocket IPC を受け付け、各機能へルーティングする。
import { WebSocketServer, type WebSocket } from "ws";

import { BrowserPool } from "../browser/BrowserPool.js";
import type { ProcessResult } from "../renderer/FrameProcessor.js";
import { isJavaToNodeMessage, type NodeToJavaMessage } from "../types/ipc.js";
import { logger } from "../util/logger.js";

export class IPCServer {
	private static readonly FRAME_MAGIC = 0x4d424652; // MBFR
	private static readonly FRAME_VERSION = 1;
	private static readonly TYPE_FRAME = 1;
	private static readonly TYPE_DELTA = 2;

	private readonly wss: WebSocketServer;
	private readonly pool: BrowserPool;
	private socket: WebSocket | null = null;

	public constructor(port: number) {
		this.pool = new BrowserPool(
			(screenId, result) => this.handleFrame(screenId, result),
			(screenId, url) => this.send({ type: "URL_CHANGED", screenId, url }),
			(screenId) => this.send({ type: "PAGE_LOADED", screenId }),
		);
		this.wss = new WebSocketServer({ port, host: "127.0.0.1" });
	}

	public start(): void {
		this.wss.on("connection", (ws) => {
			logger.info("IPC client connected");
			this.socket = ws;
			this.send({ type: "READY" });

			ws.on("message", async (raw) => {
				try {
					const parsed = JSON.parse(raw.toString("utf8")) as unknown;
					if (!isJavaToNodeMessage(parsed)) {
						throw new Error("Invalid message shape");
					}
					await this.route(parsed);
				} catch (error) {
					logger.error("Failed to process IPC message", error);
				}
			});

			ws.on("close", () => {
				logger.warn("IPC client disconnected");
				this.socket = null;
			});
		});

		logger.info(
			"IPC server started on ws://127.0.0.1:" + this.wss.options.port,
		);
	}

	public async shutdown(): Promise<void> {
		await this.pool.shutdown();
		await new Promise<void>((resolve) => this.wss.close(() => resolve()));
	}

	private async route(msg: ReturnType<typeof JSON.parse>): Promise<void> {
		switch (msg.type) {
			case "OPEN":
				await this.pool.open(msg.screenId, msg.width, msg.height, msg.fps);
				return;
			case "NAVIGATE":
				await this.pool.navigate(msg.screenId, msg.url);
				return;
			case "MOUSE_CLICK":
				await this.pool.click(msg.screenId, msg.x, msg.y, msg.button);
				return;
			case "SCROLL":
				await this.pool.scroll(msg.screenId, msg.deltaY);
				return;
			case "GO_BACK":
				await this.pool.goBack(msg.screenId);
				return;
			case "GO_FORWARD":
				await this.pool.goForward(msg.screenId);
				return;
			case "RELOAD":
				await this.pool.reload(msg.screenId);
				return;
			case "CLOSE":
				await this.pool.close(msg.screenId);
				return;
			case "SET_FPS":
				await this.pool.setFps(msg.screenId, msg.fps);
				return;
			case "TEXT_INPUT":
				await this.pool.typeText(msg.screenId, msg.text);
				return;
			case "KEY_PRESS":
				await this.pool.pressKey(msg.screenId, msg.key);
				return;
			default:
				throw new Error("Unsupported type");
		}
	}

	private handleFrame(screenId: string, result: ProcessResult): void {
		if (result.type === "FRAME") {
			this.sendFrameBinary(
				screenId,
				IPCServer.TYPE_FRAME,
				result.data,
				(result.width & 0xffff) | ((result.height & 0xffff) << 16),
				0,
				0,
				0,
			);
			return;
		}
		if (result.type === "DELTA_FRAME") {
			this.sendFrameBinary(
				screenId,
				IPCServer.TYPE_DELTA,
				result.data,
				result.x,
				result.y,
				result.w,
				result.h,
			);
		}
	}

	private sendFrameBinary(
		screenId: string,
		type: number,
		payload: Uint8Array,
		a: number,
		b: number,
		c: number,
		d: number,
	): void {
		if (!this.socket || this.socket.readyState !== 1) {
			return;
		}
		const uuid = this.uuidToBytes(screenId);
		if (!uuid) {
			logger.warn(`Invalid screenId for binary frame: ${screenId}`);
			return;
		}

		const headerSize = 4 + 1 + 1 + 2 + 16 + 16;
		const packet = Buffer.allocUnsafe(headerSize + payload.length);
		let offset = 0;

		packet.writeUInt32BE(IPCServer.FRAME_MAGIC, offset);
		offset += 4;
		packet.writeUInt8(IPCServer.FRAME_VERSION, offset);
		offset += 1;
		packet.writeUInt8(type, offset);
		offset += 1;
		packet.writeUInt16BE(0, offset);
		offset += 2;

		uuid.copy(packet, offset);
		offset += 16;

		packet.writeInt32BE(a, offset);
		offset += 4;
		packet.writeInt32BE(b, offset);
		offset += 4;
		packet.writeInt32BE(c, offset);
		offset += 4;
		packet.writeInt32BE(d, offset);
		offset += 4;

		Buffer.from(payload).copy(packet, offset);
		this.socket.send(packet);
	}

	private uuidToBytes(value: string): Buffer | null {
		const hex = value.replace(/-/g, "").toLowerCase();
		if (!/^[0-9a-f]{32}$/.test(hex)) {
			return null;
		}
		const out = Buffer.allocUnsafe(16);
		for (let i = 0; i < 16; i++) {
			out[i] = Number.parseInt(hex.slice(i * 2, i * 2 + 2), 16);
		}
		return out;
	}

	private send(message: NodeToJavaMessage): void {
		if (!this.socket || this.socket.readyState !== 1) {
			return;
		}
		this.socket.send(JSON.stringify(message));
	}
}
