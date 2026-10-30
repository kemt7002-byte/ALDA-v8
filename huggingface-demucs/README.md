---
title: ALAD Free Demucs Separator
emoji: 🎧
colorFrom: purple
colorTo: blue
sdk: docker
app_port: 7860
pinned: false
---

# ALAD Free Demucs Separator

CPU-only Hugging Face Space endpoint used by ALAD-Mobile. Health check: `/health`. POST WAV multipart field `file` to `/separate`; response contains base64 WAV stems `vocals_wav` and `instrumental_wav`.

This Space uses the open-source HTDemucs model. First startup downloads model weights and can take several minutes. Free CPU Spaces may sleep, have cold starts, and are not guaranteed to provide continuous real-time service.
