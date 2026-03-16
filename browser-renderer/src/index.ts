// このファイルの責務: browser-renderer の起動エントリーポイントを提供する。
import { IPCServer } from "./ipc/IPCServer.js";
import { logger } from "./util/logger.js";

const port = Number.parseInt(process.env.MAPBROWSER_IPC_PORT ?? "25600", 10);
const server = new IPCServer(port);
server.start();

const shutdown = async (): Promise<void> => {
	logger.info("Shutting down browser-renderer...");
	await server.shutdown();
	process.exit(0);
};

process.on("SIGINT", () => {
	void shutdown();
});
process.on("SIGTERM", () => {
	void shutdown();
});

logger.info(`browser-renderer started on port ${port}`);
