export interface SpeechRecognitionOptions {
  lang?: string;
  continuous?: boolean;
  interimResults?: boolean;
  maxAlternatives?: number;
}

export function createSpeechRecognition(
  options: SpeechRecognitionOptions = {},
): SpeechRecognition | null {
  const SpeechRecognitionCtor = window.SpeechRecognition || window.webkitSpeechRecognition;

  if (!SpeechRecognitionCtor) {
    return null;
  }

  const recognition = new SpeechRecognitionCtor();
  recognition.lang = options.lang ?? 'zh-CN';
  recognition.continuous = options.continuous ?? false;
  recognition.interimResults = options.interimResults ?? false;
  recognition.maxAlternatives = options.maxAlternatives ?? 1;
  return recognition;
}

export function startRecognition(recognition: SpeechRecognition | null): void {
  recognition?.start();
}

export function stopRecognition(recognition: SpeechRecognition | null): void {
  recognition?.stop();
}

export function speak(text: string, lang = 'zh-CN'): Promise<void> {
  return new Promise((resolve) => {
    if (!text || !('speechSynthesis' in window)) {
      resolve();
      return;
    }

    window.speechSynthesis.cancel();

    const utterance = new SpeechSynthesisUtterance(text);
    utterance.lang = lang;

    const voices = window.speechSynthesis.getVoices();
    const matchedVoice = voices.find((voice) => voice.lang.toLowerCase().includes('zh'));
    if (matchedVoice) {
      utterance.voice = matchedVoice;
    }

    utterance.onend = () => resolve();
    utterance.onerror = () => resolve();

    window.speechSynthesis.speak(utterance);
  });
}
