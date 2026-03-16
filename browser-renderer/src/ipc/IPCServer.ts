// このファイルの責務: Java Plugin との WebSocket IPC を受け付け、各機能へルーティングする。
import { WebSocketServer, type WebSocket } from "ws";

import { BrowserPool } from "../browser/BrowserPool.js";
import type { ProcessResult } from "../renderer/FrameProcessor.js";
import { isJavaToNodeMessage, type NodeToJavaMessage } from "../types/ipc.js";
import { logger } from "../util/logger.js";

export class IPCServer {
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
			default:
				throw new Error("Unsupported type");
		}
	}

	private handleFrame(screenId: string, result: ProcessResult): void {
		if (result.type === "FRAME") {
			this.send({
				type: "FRAME",
				screenId,
				data: Buffer.from(result.data).toString("base64"),
				width: result.width,
				height: result.height,
			});
			return;
		}
		if (result.type === "DELTA_FRAME") {
			this.send({
				type: "DELTA_FRAME",
				screenId,
				data: Buffer.from(result.data).toString("base64"),
				x: result.x,
				y: result.y,
				w: result.w,
				h: result.h,
			});
		}
	}

	private send(message: NodeToJavaMessage): void {
		if (!this.socket || this.socket.readyState !== 1) {
			return;
		}
		this.socket.send(JSON.stringify(message));
	}
}
