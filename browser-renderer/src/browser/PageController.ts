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
	private requestedFps: number;
	private adaptiveFps: number;
	private readonly onFrame: (result: ProcessResult) => void;
	private readonly onUrlChanged: (url: string) => void;
	private readonly onPageLoaded: () => void;
	private processingFrame = false;
	private pendingFrameData: string | null = null;
	private nextAllowedFrameAtMs = 0;
	private smoothedProcessMs = 0;
	private droppedByThrottle = 0;
	private processedSinceTune = 0;
	private lastTuneAtMs = Date.now();

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
		this.requestedFps = Math.max(1, fps);
		this.adaptiveFps = this.requestedFps;
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
		this.requestedFps = Math.max(1, fps);
		this.adaptiveFps = this.requestedFps;
		this.nextAllowedFrameAtMs = 0;
		this.smoothedProcessMs = 0;
		this.droppedByThrottle = 0;
		this.processedSinceTune = 0;
		this.lastTuneAtMs = Date.now();
		await this.startCapture();
	}

	public async typeText(text: string): Promise<void> {
		if (!this.page) return;
		const inserted = await this.page.evaluate((value) => {
			const isEditable = (element: Element | null): element is HTMLElement => {
				if (!(element instanceof HTMLElement)) {
					return false;
				}
				if (element instanceof HTMLInputElement) {
					return (
						element.type !== "hidden" && !element.disabled && !element.readOnly
					);
				}
				if (element instanceof HTMLTextAreaElement) {
					return !element.disabled && !element.readOnly;
				}
				return element.isContentEditable;
			};

			const fallback = document.querySelector<HTMLElement>(
				'input:not([type="hidden"]):not([disabled]):not([readonly]), textarea:not([disabled]):not([readonly]), [contenteditable=""], [contenteditable="true"]',
			);
			const target = isEditable(document.activeElement)
				? document.activeElement
				: fallback;

			if (!isEditable(target)) {
				return false;
			}

			target.focus();

			if (
				target instanceof HTMLInputElement ||
				target instanceof HTMLTextAreaElement
			) {
				const start = target.selectionStart ?? target.value.length;
				const end = target.selectionEnd ?? start;
				target.setRangeText(value, start, end, "end");
				target.dispatchEvent(new Event("input", { bubbles: true }));
				target.dispatchEvent(new Event("change", { bubbles: true }));
				return true;
			}

			const selection = window.getSelection();
			if (selection && selection.rangeCount > 0) {
				const range = selection.getRangeAt(0);
				range.deleteContents();
				range.insertNode(document.createTextNode(value));
				range.collapse(false);
				selection.removeAllRanges();
				selection.addRange(range);
			} else {
				target.append(document.createTextNode(value));
			}
			target.dispatchEvent(
				new InputEvent("input", {
					bubbles: true,
					data: value,
					inputType: "insertText",
				}),
			);
			return true;
		}, text);

		if (!inserted) {
			await this.page.keyboard.type(text);
		}
	}

	public async pressKey(key: string): Promise<void> {
		if (!this.page) return;
		await this.page.keyboard.press(key);
	}

	private async startCapture(): Promise<void> {
		if (!this.page) return;
		if (this.cdp) {
			try {
				await this.cdp.send("Page.stopScreencast");
			} catch (error) {
				logger.debug("stopScreencast skipped", error);
			}
			await this.cdp.detach().catch(() => undefined);
			this.cdp = null;
		}
		this.cdp = await this.page.context().newCDPSession(this.page);
		const everyNthFrame = Math.max(1, Math.floor(60 / this.requestedFps));

		this.cdp.on(
			"Page.screencastFrame",
			async (event: { data: string; sessionId: number }) => {
				await this.cdp?.send("Page.screencastFrameAck", {
					sessionId: event.sessionId,
				});

				try {
					const now = Date.now();
					if (this.processingFrame || now < this.nextAllowedFrameAtMs) {
						// Keep only the newest frame while overloaded.
						this.pendingFrameData = event.data;
						this.droppedByThrottle++;
						return;
					}
					await this.processFrame(event.data);
				} catch (error) {
					logger.error("Failed to process screencast frame", error);
				}
			},
		);

		await this.cdp.send("Page.startScreencast", {
			format: "jpeg",
			quality: 65,
			everyNthFrame,
			maxWidth: this.widthMaps * 128,
			maxHeight: this.heightMaps * 128,
		});
	}

	private async processFrame(initialFrameData: string): Promise<void> {
		this.processingFrame = true;
		let currentFrameData: string | null = initialFrameData;
		try {
			while (currentFrameData) {
				const startedAt = Date.now();
				const currentBuffer = Buffer.from(currentFrameData, "base64");
				const processed = await this.frameProcessor.process(
					currentBuffer,
					this.widthMaps,
					this.heightMaps,
				);
				if (processed.type !== "SKIP") {
					this.onFrame(processed);
				}
				const elapsedMs = Math.max(1, Date.now() - startedAt);
				this.updateAdaptiveFps(elapsedMs);
				this.nextAllowedFrameAtMs =
					Date.now() + Math.floor(1000 / this.adaptiveFps);

				const next = this.pendingFrameData;
				if (!next) {
					break;
				}
				this.pendingFrameData = null;
				currentFrameData = next;
			}
		} finally {
			this.processingFrame = false;
		}
	}

	private updateAdaptiveFps(processMs: number): void {
		this.smoothedProcessMs =
			this.smoothedProcessMs === 0
				? processMs
				: this.smoothedProcessMs * 0.8 + processMs * 0.2;
		this.processedSinceTune++;

		const now = Date.now();
		if (now - this.lastTuneAtMs < 600 && this.processedSinceTune < 8) {
			return;
		}

		const minFps = 5;
		const currentBudgetMs = 1000 / Math.max(1, this.adaptiveFps);
		let nextAdaptive = this.adaptiveFps;

		if (
			this.smoothedProcessMs > currentBudgetMs * 1.15 ||
			this.droppedByThrottle >= 4
		) {
			nextAdaptive = Math.max(minFps, Math.floor(this.adaptiveFps * 0.85));
		} else if (
			this.smoothedProcessMs < currentBudgetMs * 0.7 &&
			this.droppedByThrottle === 0 &&
			this.adaptiveFps < this.requestedFps
		) {
			nextAdaptive = Math.min(this.requestedFps, this.adaptiveFps + 1);
		}

		if (nextAdaptive !== this.adaptiveFps) {
			logger.debug(
				`Adaptive FPS tuned: ${this.adaptiveFps} -> ${nextAdaptive} (process=${this.smoothedProcessMs.toFixed(1)}ms dropped=${this.droppedByThrottle})`,
			);
			this.adaptiveFps = nextAdaptive;
		}

		this.droppedByThrottle = 0;
		this.processedSinceTune = 0;
		this.lastTuneAtMs = now;
	}
}
