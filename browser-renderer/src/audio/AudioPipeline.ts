// このファイルの責務: Phase 4 で音声取り込み/Opus化を実装するための拡張ポイントを定義する。

export interface AudioFrame {
	screenId: string;
	sampleRate: number;
	opusData: Uint8Array;
}

export interface AudioPipeline {
	start(screenId: string): Promise<void>;
	stop(screenId: string): Promise<void>;
}

export class NoopAudioPipeline implements AudioPipeline {
	public async start(_screenId: string): Promise<void> {
		// Phase 4 implementation pending.
	}

	public async stop(_screenId: string): Promise<void> {
		// Phase 4 implementation pending.
	}
}
