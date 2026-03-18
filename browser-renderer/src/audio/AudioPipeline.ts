// このファイルの責務: Phase 4 で音声取り込み/Opus化を実装するための拡張ポイントを定義する。

import type { Page } from "playwright";

import { logger } from "../util/logger.js";

export interface AudioFrame {
	screenId: string;
	sampleRate: number;
	opusData: Uint8Array;
}

export type AudioFrameSink = (frame: AudioFrame) => void;

const parsePositiveInt = (
	value: string | undefined,
	fallback: number,
): number => {
	if (!value) {
		return fallback;
	}
	const parsed = Number.parseInt(value, 10);
	if (!Number.isFinite(parsed) || parsed <= 0) {
		return fallback;
	}
	return parsed;
};

export interface AudioPipeline {
	start(screenId: string, page: Page | null): Promise<void>;
	stop(screenId: string): Promise<void>;
	diagnostics(): string;
}

export class NoopAudioPipeline implements AudioPipeline {
	private readonly activeScreens = new Set<string>();

	public async start(_screenId: string, _page: Page | null): Promise<void> {
		this.activeScreens.add(_screenId);
	}

	public async stop(_screenId: string): Promise<void> {
		this.activeScreens.delete(_screenId);
	}

	public diagnostics(): string {
		return `audio-pipeline=noop active=${this.activeScreens.size}`;
	}
}

export class SyntheticAudioPipeline implements AudioPipeline {
	private readonly activeScreens = new Set<string>();
	private readonly timers = new Map<string, NodeJS.Timeout>();
	private readonly frameSink: AudioFrameSink;
	private readonly opusPayload: Uint8Array;
	private readonly sampleRate: number;
	private readonly intervalMs: number;
	private emittedFrames = 0;

	public constructor(frameSink: AudioFrameSink) {
		this.frameSink = frameSink;
		this.opusPayload = SyntheticAudioPipeline.loadPayload();
		this.sampleRate = parsePositiveInt(
			process.env.MAPBROWSER_AUDIO_SAMPLE_RATE,
			48000,
		);
		this.intervalMs = parsePositiveInt(
			process.env.MAPBROWSER_AUDIO_FRAME_INTERVAL_MS,
			100,
		);
	}

	public async start(screenId: string, _page: Page | null): Promise<void> {
		this.activeScreens.add(screenId);
		if (this.opusPayload.length === 0 || this.timers.has(screenId)) {
			return;
		}

		const timer = setInterval(() => {
			this.frameSink({
				screenId,
				sampleRate: this.sampleRate,
				opusData: this.opusPayload,
			});
			this.emittedFrames++;
		}, this.intervalMs);
		this.timers.set(screenId, timer);
	}

	public async stop(screenId: string): Promise<void> {
		this.activeScreens.delete(screenId);
		const timer = this.timers.get(screenId);
		if (!timer) {
			return;
		}
		clearInterval(timer);
		this.timers.delete(screenId);
	}

	public diagnostics(): string {
		return `audio-pipeline=synthetic active=${this.activeScreens.size} timers=${this.timers.size} emitted=${this.emittedFrames} intervalMs=${this.intervalMs} sampleRate=${this.sampleRate}`;
	}

	private static loadPayload(): Uint8Array {
		const raw = process.env.MAPBROWSER_AUDIO_TEST_OPUS_BASE64;
		if (!raw || raw.trim().length === 0) {
			return new Uint8Array();
		}
		try {
			const buffer = Buffer.from(raw, "base64");
			if (buffer.length === 0) {
				logger.warn("Synthetic audio payload is empty");
				return new Uint8Array();
			}
			logger.info(
				`Synthetic audio pipeline enabled (payload=${buffer.length} bytes)`,
			);
			return new Uint8Array(buffer);
		} catch (error) {
			logger.warn("Failed to decode MAPBROWSER_AUDIO_TEST_OPUS_BASE64", error);
			return new Uint8Array();
		}
	}
}

type CapturedChunkPayload = {
	data: string;
	sampleRate: number;
};

export class BrowserMediaRecorderAudioPipeline implements AudioPipeline {
	private readonly activeScreens = new Set<string>();
	private readonly pages = new Map<string, Page>();
	private readonly frameSink: AudioFrameSink;
	private readonly timesliceMs: number;
	private forwardedChunks = 0;

	public constructor(frameSink: AudioFrameSink) {
		this.frameSink = frameSink;
		this.timesliceMs = parsePositiveInt(
			process.env.MAPBROWSER_AUDIO_MEDIARECORDER_TIMESLICE_MS,
			200,
		);
	}

	public async start(screenId: string, page: Page | null): Promise<void> {
		this.activeScreens.add(screenId);
		if (!page) {
			logger.warn(`Audio capture skipped: page is null for ${screenId}`);
			return;
		}

		this.pages.set(screenId, page);
		await this.installBinding(screenId, page);
		await this.installCaptureScript(page);
	}

	public async stop(screenId: string): Promise<void> {
		this.activeScreens.delete(screenId);
		const page = this.pages.get(screenId);
		if (page) {
			await this.stopCaptureScript(page);
		}
		this.pages.delete(screenId);
	}

	public diagnostics(): string {
		return `audio-pipeline=media-recorder active=${this.activeScreens.size} pages=${this.pages.size} chunks=${this.forwardedChunks} timesliceMs=${this.timesliceMs}`;
	}

	private async installBinding(screenId: string, page: Page): Promise<void> {
		try {
			await page.exposeBinding(
				"__mbPushEncodedAudio",
				(_source, payload: CapturedChunkPayload) => {
					if (typeof payload?.data !== "string" || payload.data.length === 0) {
						return;
					}
					const sampleRate = Number.isFinite(payload.sampleRate)
						? Math.max(1, payload.sampleRate)
						: 48000;
					const data = Buffer.from(payload.data, "base64");
					if (data.length === 0) {
						return;
					}
					this.forwardedChunks++;
					this.frameSink({
						screenId,
						sampleRate,
						opusData: new Uint8Array(data),
					});
				},
			);
		} catch (error) {
			logger.debug("Audio binding installation skipped", error);
		}
	}

	private async installCaptureScript(page: Page): Promise<void> {
		try {
			await page.evaluate((timesliceMs) => {
				type CaptureState = {
					active: boolean;
					ctx: AudioContext;
					recorder: MediaRecorder;
					observer: MutationObserver;
				};

				type CaptureWindow = Window & {
					__mbAudioCapture?: CaptureState;
					__mbPushEncodedAudio?: (payload: {
						data: string;
						sampleRate: number;
					}) => void;
				};

				const win = window as CaptureWindow;
				if (win.__mbAudioCapture?.active) {
					return;
				}

				const ContextCtor =
					window.AudioContext ||
					((window as unknown as { webkitAudioContext?: typeof AudioContext })
						.webkitAudioContext ??
						null);
				if (!ContextCtor || typeof MediaRecorder === "undefined") {
					return;
				}

				const ctx = new ContextCtor({ sampleRate: 48000 });
				const destination = ctx.createMediaStreamDestination();
				const attached = new WeakSet<HTMLMediaElement>();

				const attachMediaElement = (element: HTMLMediaElement): void => {
					if (attached.has(element)) {
						return;
					}
					try {
						const source = ctx.createMediaElementSource(element);
						source.connect(destination);
						source.connect(ctx.destination);
						attached.add(element);
					} catch {
						// createMediaElementSource can fail for already-connected or unsupported elements.
					}
				};

				document.querySelectorAll("audio,video").forEach((node) => {
					attachMediaElement(node as HTMLMediaElement);
				});

				const observer = new MutationObserver((mutations) => {
					for (const mutation of mutations) {
						for (const node of mutation.addedNodes) {
							if (!(node instanceof HTMLElement)) {
								continue;
							}
							if (node instanceof HTMLMediaElement) {
								attachMediaElement(node);
							}
							node.querySelectorAll("audio,video").forEach((child) => {
								attachMediaElement(child as HTMLMediaElement);
							});
						}
					}
				});
				observer.observe(document.documentElement, {
					childList: true,
					subtree: true,
				});

				const preferredMimes = [
					"audio/webm;codecs=opus",
					"audio/ogg;codecs=opus",
					"audio/webm",
					"audio/ogg",
				];
				const mimeType = preferredMimes.find((mime) =>
					MediaRecorder.isTypeSupported(mime),
				);
				const recorder = mimeType
					? new MediaRecorder(destination.stream, { mimeType })
					: new MediaRecorder(destination.stream);

				recorder.ondataavailable = (event) => {
					if (
						!event.data ||
						event.data.size === 0 ||
						!win.__mbPushEncodedAudio
					) {
						return;
					}
					void event.data.arrayBuffer().then((buffer) => {
						const bytes = new Uint8Array(buffer);
						if (bytes.length === 0) {
							return;
						}
						let binary = "";
						for (const value of bytes) {
							binary += String.fromCharCode(value);
						}
						win.__mbPushEncodedAudio?.({
							data: btoa(binary),
							sampleRate: ctx.sampleRate,
						});
					});
				};

				recorder.start(Math.max(50, timesliceMs));
				win.__mbAudioCapture = {
					active: true,
					ctx,
					recorder,
					observer,
				};

				const resume = (): void => {
					if (ctx.state === "suspended") {
						void ctx.resume();
					}
				};
				document.addEventListener("click", resume, { passive: true });
				document.addEventListener("keydown", resume);
			}, this.timesliceMs);
		} catch (error) {
			logger.warn("Failed to install browser audio capture", error);
		}
	}

	private async stopCaptureScript(page: Page): Promise<void> {
		try {
			await page.evaluate(() => {
				type CaptureState = {
					active: boolean;
					ctx: AudioContext;
					recorder: MediaRecorder;
					observer: MutationObserver;
				};
				type CaptureWindow = Window & { __mbAudioCapture?: CaptureState };
				const win = window as CaptureWindow;
				const state = win.__mbAudioCapture;
				if (!state) {
					return;
				}
				state.active = false;
				state.observer.disconnect();
				if (state.recorder.state !== "inactive") {
					state.recorder.stop();
				}
				void state.ctx.close();
				delete win.__mbAudioCapture;
			});
		} catch (error) {
			logger.debug("Failed to stop browser audio capture", error);
		}
	}
}

export const createAudioPipeline = (
	frameSink: AudioFrameSink,
): AudioPipeline => {
	if (process.env.MAPBROWSER_AUDIO_CAPTURE_MODE === "media-recorder") {
		return new BrowserMediaRecorderAudioPipeline(frameSink);
	}
	if (process.env.MAPBROWSER_AUDIO_TEST_OPUS_BASE64) {
		return new SyntheticAudioPipeline(frameSink);
	}
	return new NoopAudioPipeline();
};
