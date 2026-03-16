// このファイルの責務: 1スクリーン分の Playwright ページ制御とフレーム取得を担う。
import { chromium, type Browser, type CDPSession, type Page } from "playwright";

import {
	FrameProcessor,
	type ProcessResult,
} from "../renderer/FrameProcessor.js";
import { resolveYouTubeUrl } from "../youtube/YtDlpBridge.js";
import { logger } from "../util/logger.js";

export class PageController {
	private browser: Browser | null = null;
	private page: Page | null = null;
	private cdp: CDPSession | null = null;
	private readonly frameProcessor = new FrameProcessor();
	private readonly widthMaps: number;
	private readonly heightMaps: number;
	private fps: number;
	private readonly onFrame: (result: ProcessResult) => void;
	private readonly onUrlChanged: (url: string) => void;
	private readonly onPageLoaded: () => void;

	public constructor(
		widthMaps: number,
		heightMaps: number,
		fps: number,
		onFrame: (result: ProcessResult) => void,
		onUrlChanged: (url: string) => void,
		onPageLoaded: () => void,
	) {
		this.widthMaps = widthMaps;
		this.heightMaps = heightMaps;
		this.fps = fps;
		this.onFrame = onFrame;
		this.onUrlChanged = onUrlChanged;
		this.onPageLoaded = onPageLoaded;
	}

	public async open(): Promise<void> {
		this.browser = await chromium.launch({ headless: true });
		const context = await this.browser.newContext({
			viewport: { width: this.widthMaps * 128, height: this.heightMaps * 128 },
			ignoreHTTPSErrors: true,
		});
		this.page = await context.newPage();
		this.page.on("framenavigated", () => {
			this.onUrlChanged(this.page?.url() ?? "about:blank");
		});
		this.page.on("load", () => {
			this.onPageLoaded();
		});
		await this.startCapture();
	}

	public async close(): Promise<void> {
		if (this.page) {
			await this.page.close({ runBeforeUnload: false });
		}
		if (this.browser) {
			await this.browser.close();
		}
		this.page = null;
		this.browser = null;
		this.cdp = null;
	}

	public async navigate(url: string): Promise<void> {
		if (!this.page) return;
		let target = url;
		if (/^https?:\/\/(www\.)?(youtube\.com|youtu\.be)\//i.test(url)) {
			try {
				target = await resolveYouTubeUrl(url);
			} catch (error) {
				logger.warn("yt-dlp resolve failed, fallback to raw URL", error);
			}
		}
		await this.page.goto(target, { waitUntil: "domcontentloaded" });
	}

	public async click(
		x: number,
		y: number,
		button: "left" | "right",
	): Promise<void> {
		if (!this.page) return;
		await this.page.mouse.click(x, y, { button });
	}

	public async scroll(deltaY: number): Promise<void> {
		if (!this.page) return;
		await this.page.mouse.wheel(0, deltaY);
	}

	public async goBack(): Promise<void> {
		if (!this.page) return;
		await this.page.goBack({ waitUntil: "domcontentloaded" });
	}

	public async goForward(): Promise<void> {
		if (!this.page) return;
		await this.page.goForward({ waitUntil: "domcontentloaded" });
	}

	public async reload(): Promise<void> {
		if (!this.page) return;
		await this.page.reload({ waitUntil: "domcontentloaded" });
	}

	public async setFps(fps: number): Promise<void> {
		this.fps = Math.max(1, fps);
		await this.startCapture();
	}

	private async startCapture(): Promise<void> {
		if (!this.page) return;
		this.cdp = await this.page.context().newCDPSession(this.page);
		const everyNthFrame = Math.max(1, Math.floor(60 / this.fps));

		this.cdp.on(
			"Page.screencastFrame",
			async (event: { data: string; sessionId: number }) => {
				try {
					const pngBuffer = Buffer.from(event.data, "base64");
					const processed = await this.frameProcessor.process(
						pngBuffer,
						this.widthMaps,
						this.heightMaps,
					);
					if (processed.type !== "SKIP") {
						this.onFrame(processed);
					}
				} catch (error) {
					logger.error("Failed to process screencast frame", error);
				} finally {
					await this.cdp?.send("Page.screencastFrameAck", {
						sessionId: event.sessionId,
					});
				}
			},
		);

		await this.cdp.send("Page.startScreencast", {
			format: "png",
			everyNthFrame,
			maxWidth: this.widthMaps * 128,
			maxHeight: this.heightMaps * 128,
		});
	}
}
