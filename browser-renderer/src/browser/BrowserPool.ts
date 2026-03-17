// このファイルの責務: screenId と PageController の対応を管理する。
import type { ProcessResult } from "../renderer/FrameProcessor.js";
import { logger } from "../util/logger.js";
import { PageController } from "./PageController.js";

export class BrowserPool {
	private readonly pages = new Map<string, PageController>();
	private readonly onFrame: (screenId: string, result: ProcessResult) => void;
	private readonly onUrlChanged: (screenId: string, url: string) => void;
	private readonly onPageLoaded: (screenId: string) => void;

	public constructor(
		onFrame: (screenId: string, result: ProcessResult) => void,
		onUrlChanged: (screenId: string, url: string) => void,
		onPageLoaded: (screenId: string) => void,
	) {
		this.onFrame = onFrame;
		this.onUrlChanged = onUrlChanged;
		this.onPageLoaded = onPageLoaded;
	}

	public async open(
		screenId: string,
		width: number,
		height: number,
		fps: number,
	): Promise<void> {
		await this.close(screenId);
		const controller = new PageController(
			width,
			height,
			fps,
			(result) => this.onFrame(screenId, result),
			(url) => this.onUrlChanged(screenId, url),
			() => this.onPageLoaded(screenId),
		);
		await controller.open();
		this.pages.set(screenId, controller);
		logger.info(`Screen opened: ${screenId}`);
	}

	public async close(screenId: string): Promise<void> {
		const page = this.pages.get(screenId);
		if (!page) return;
		await page.close();
		this.pages.delete(screenId);
		logger.info(`Screen closed: ${screenId}`);
	}

	public async navigate(screenId: string, url: string): Promise<void> {
		await this.pages.get(screenId)?.navigate(url);
	}

	public async click(
		screenId: string,
		x: number,
		y: number,
		button: "left" | "right",
	): Promise<void> {
		await this.pages.get(screenId)?.click(x, y, button);
	}

	public async scroll(screenId: string, deltaY: number): Promise<void> {
		await this.pages.get(screenId)?.scroll(deltaY);
	}

	public async goBack(screenId: string): Promise<void> {
		await this.pages.get(screenId)?.goBack();
	}

	public async goForward(screenId: string): Promise<void> {
		await this.pages.get(screenId)?.goForward();
	}

	public async reload(screenId: string): Promise<void> {
		await this.pages.get(screenId)?.reload();
	}

	public async setFps(screenId: string, fps: number): Promise<void> {
		await this.pages.get(screenId)?.setFps(fps);
	}

	public async typeText(screenId: string, text: string): Promise<void> {
		await this.pages.get(screenId)?.typeText(text);
	}

	public async pressKey(screenId: string, key: string): Promise<void> {
		await this.pages.get(screenId)?.pressKey(key);
	}

	public async shutdown(): Promise<void> {
		for (const [screenId, page] of this.pages.entries()) {
			await page.close();
			this.pages.delete(screenId);
		}
	}
}
