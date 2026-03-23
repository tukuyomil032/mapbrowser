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
	private contentAwareRequestedFps: number;
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
	private currentUrl = "about:blank";
	private consecutiveSkipFrames = 0;
	private metricsWindowStartedAt = Date.now();
	private metricsProcessed = 0;
	private metricsSkipped = 0;
	private metricsFrameEmitted = 0;
	private metricsDeltaEmitted = 0;

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
		this.contentAwareRequestedFps = this.requestedFps;
		this.adaptiveFps = this.contentAwareRequestedFps;
		this.onFrame = onFrame;
		this.onUrlChanged = onUrlChanged;
		this.onPageLoaded = onPageLoaded;
	}

	public async open(): Promise<void> {
		this.browser = await chromium.launch({
			headless: true,
			args: [
				"--disable-gpu-vsync",
				"--disable-frame-rate-limit",
				"--enable-gpu",
			],
		});
		const context = await this.browser.newContext({
			viewport: { width: this.widthMaps * 128, height: this.heightMaps * 128 },
			ignoreHTTPSErrors: true,
		});
		this.page = await context.newPage();
		this.page.on("framenavigated", () => {
			const url = this.page?.url() ?? "about:blank";
			this.currentUrl = url;
			this.applyContentFpsPolicy(url);
			this.onUrlChanged(url);
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
		this.currentUrl = target;
		this.applyContentFpsPolicy(target);
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
		this.applyContentFpsPolicy(this.currentUrl);
		this.adaptiveFps = this.contentAwareRequestedFps;
		this.nextAllowedFrameAtMs = 0;
		this.smoothedProcessMs = 0;
		this.droppedByThrottle = 0;
		this.processedSinceTune = 0;
		this.lastTuneAtMs = Date.now();
		this.consecutiveSkipFrames = 0;
		this.resetMetricsWindow();
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

	public getPage(): Page | null {
		return this.page;
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
		const everyNthFrame = Math.max(
			1,
			Math.floor(60 / Math.max(1, this.contentAwareRequestedFps)),
		);

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
				this.metricsProcessed++;
				if (processed.type !== "SKIP") {
					this.consecutiveSkipFrames = 0;
					if (processed.type === "FRAME") {
						this.metricsFrameEmitted++;
					} else {
						this.metricsDeltaEmitted++;
					}
					this.onFrame(processed);
				} else {
					this.consecutiveSkipFrames++;
					this.metricsSkipped++;
				}
				const elapsedMs = Math.max(1, Date.now() - startedAt);
				this.updateAdaptiveFps(elapsedMs);
				this.maybeLogMetrics();
				const baseIntervalMs = Math.floor(1000 / Math.max(1, this.adaptiveFps));
				const idlePenaltyMs =
					this.consecutiveSkipFrames > 20
						? Math.max(
								baseIntervalMs,
								this.isLikelyVideoUrl(this.currentUrl) ? 120 : 333,
							)
						: 0;
				this.nextAllowedFrameAtMs = Date.now() + baseIntervalMs + idlePenaltyMs;

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

		const isVideo = this.isLikelyVideoUrl(this.currentUrl);
		const minFps = isVideo ? 4 : 2;
		let nextAdaptive = this.contentAwareRequestedFps;

		if (this.smoothedProcessMs > 80) {
			nextAdaptive = 4;
		} else if (this.smoothedProcessMs > 50) {
			nextAdaptive = 6;
		}

		if (this.droppedByThrottle >= 4) {
			nextAdaptive = Math.min(
				nextAdaptive,
				Math.max(minFps, this.adaptiveFps - 1),
			);
		}

		nextAdaptive = Math.max(
			minFps,
			Math.min(this.contentAwareRequestedFps, nextAdaptive),
		);

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

	private applyContentFpsPolicy(url: string): void {
		const isVideo = this.isLikelyVideoUrl(url);
		this.contentAwareRequestedFps = isVideo
			? Math.max(this.requestedFps, 10)
			: Math.min(this.requestedFps, 3);
		if (this.adaptiveFps > this.contentAwareRequestedFps) {
			this.adaptiveFps = this.contentAwareRequestedFps;
		}
	}

	private isLikelyVideoUrl(url: string): boolean {
		const normalized = url.toLowerCase();
		return (
			normalized.includes("youtube.com") ||
			normalized.includes("youtu.be") ||
			normalized.includes("/watch?") ||
			normalized.includes("/shorts/")
		);
	}

	private maybeLogMetrics(): void {
		const now = Date.now();
		const elapsedMs = now - this.metricsWindowStartedAt;
		if (elapsedMs < 10_000) {
			return;
		}

		const elapsedSec = Math.max(1, elapsedMs / 1000);
		const processedFps = this.metricsProcessed / elapsedSec;
		const skipRatio =
			this.metricsProcessed === 0
				? 0
				: this.metricsSkipped / this.metricsProcessed;
		logger.info(
			`Perf: fps=${processedFps.toFixed(2)} adaptive=${this.adaptiveFps} frame=${this.metricsFrameEmitted} delta=${this.metricsDeltaEmitted} skipRatio=${(skipRatio * 100).toFixed(1)}% processMs=${this.smoothedProcessMs.toFixed(1)}`,
		);

		this.resetMetricsWindow();
	}

	private resetMetricsWindow(): void {
		this.metricsWindowStartedAt = Date.now();
		this.metricsProcessed = 0;
		this.metricsSkipped = 0;
		this.metricsFrameEmitted = 0;
		this.metricsDeltaEmitted = 0;
	}
}
