// このファイルの責務: Java 側と共有する IPC メッセージ型を定義する。

export type JavaToNodeMessage =
	| {
			type: "OPEN";
			screenId: string;
			width: number;
			height: number;
			fps: number;
	  }
	| {
			type: "NAVIGATE";
			screenId: string;
			url: string;
	  }
	| {
			type: "MOUSE_CLICK";
			screenId: string;
			x: number;
			y: number;
			button: "left" | "right";
	  }
	| {
			type: "SCROLL";
			screenId: string;
			deltaY: number;
	  }
	| {
			type: "GO_BACK" | "GO_FORWARD" | "RELOAD" | "CLOSE";
			screenId: string;
	  }
	| {
			type: "SET_FPS";
			screenId: string;
			fps: number;
	  }
	| {
			type: "TEXT_INPUT";
			screenId: string;
			text: string;
	  }
	| {
			type: "KEY_PRESS";
			screenId: string;
			key: string;
	  };

export type NodeToJavaMessage =
	| { type: "READY" }
	| {
			type: "FRAME";
			screenId: string;
			data: string;
			width: number;
			height: number;
	  }
	| {
			type: "DELTA_FRAME";
			screenId: string;
			data: string;
			x: number;
			y: number;
			w: number;
			h: number;
	  }
	| {
			type: "URL_CHANGED";
			screenId: string;
			url: string;
	  }
	| {
			type: "PAGE_LOADED";
			screenId: string;
	  }
	| {
			type: "AUDIO_FRAME";
			screenId: string;
			data: string;
			sampleRate: number;
	  }
	| {
			type: "ERROR";
			screenId: string;
			message: string;
	  };

export const isJavaToNodeMessage = (
	value: unknown,
): value is JavaToNodeMessage => {
	if (typeof value !== "object" || value === null) {
		return false;
	}
	const m = value as Record<string, unknown>;
	return typeof m.type === "string";
};
