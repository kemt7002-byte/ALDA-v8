import io, base64, os
from fastapi import FastAPI, UploadFile, File, HTTPException
from fastapi.middleware.cors import CORSMiddleware
import numpy as np
import soundfile as sf
import torch, torchaudio
from demucs.pretrained import get_model
from demucs.apply import apply_model

app = FastAPI(title="ALAD Demucs Free Colab")
app.add_middleware(CORSMiddleware, allow_origins=["*"], allow_methods=["GET", "POST"], allow_headers=["*"])
MODEL = None

@app.on_event("startup")
def load_model():
    global MODEL
    torch.set_num_threads(max(1, min(4, os.cpu_count() or 2)))
    MODEL = get_model(name="htdemucs")
    MODEL.cpu().eval()

@app.get("/health")
def health():
    return {"status": "ok", "model": "htdemucs", "device": "cpu"}

def wav_b64(audio, sr):
    buf = io.BytesIO(); sf.write(buf, audio, sr, format="WAV", subtype="PCM_16")
    return base64.b64encode(buf.getvalue()).decode("ascii")

@app.post("/separate")
async def separate(file: UploadFile = File(...)):
    if MODEL is None: raise HTTPException(503, "Model is loading; retry")
    raw = await file.read()
    if not raw or len(raw) > 8 * 1024 * 1024: raise HTTPException(413, "Audio must be 1 byte to 8 MB")
    try:
        audio, sr = sf.read(io.BytesIO(raw), dtype="float32", always_2d=True)
        x = torch.from_numpy(audio.T.copy())
        if sr != MODEL.samplerate: x = torchaudio.functional.resample(x, sr, MODEL.samplerate)
        if x.shape[0] == 1: x = x.repeat(2, 1)
        elif x.shape[0] > 2: x = x[:2]
        mean = x.mean(); std = x.std().clamp(min=1e-8); x = (x - mean) / std
        with torch.inference_mode():
            stems = apply_model(MODEL, x.unsqueeze(0), device="cpu", shifts=0, split=True, overlap=0.25, progress=False)[0]
        stems = stems * std + mean
        names = MODEL.sources
        vocals = stems[names.index("vocals")].mean(0)
        instrumental = sum(stems[i] for i,n in enumerate(names) if n != "vocals").mean(0)
        vocals = torchaudio.functional.resample(vocals.unsqueeze(0), MODEL.samplerate, 16000).squeeze(0).numpy()
        instrumental = torchaudio.functional.resample(instrumental.unsqueeze(0), MODEL.samplerate, 16000).squeeze(0).numpy()
        return {"vocals_wav": wav_b64(vocals,16000), "instrumental_wav": wav_b64(instrumental,16000), "sample_rate":16000}
    except Exception as e:
        raise HTTPException(500, f"Separation failed: {type(e).__name__}: {e}")
