# PoC demo video

`poc-demo.mp4` is a walkthrough of the working Hardware Service Decision Copilot PoC.

- **Recorded:** 2026-11-26 (Europe/Warsaw), with `playwright-cli` 0.1.25 (`video-start`, `video-chapter`, `video-show-actions`, `video-stop`).
- **App commit:** `0fab921` (final packaged JAR, not rebuilt).
- **Models:** vision and decision `openai/gpt-6-luna`; triage `typesafe/jev-1.14` (via OpenRouter).
- **Real, no mocks:** real JAR, real OpenRouter calls, the real photos from `assets/example-images/` (`phone-1.jpg`, `phone-2.jpeg`, `phone-3.jpeg`, `laptop-1.png`, `laptop-2.webp`). The error chapter uses a second JAR instance on `:8081` started with an invalid key.
- **File:** 1280x720, H.264 (High), yuv420p, 25 fps, no audio, 3:50 (230.5 s), about 5.7 MB.

## Chapters

| Time | Chapter | What happens |
|---|---|---|
| 00:03 | Zwrot: formularz i walidacja | Empty submit shows validation errors; a RETURN with `phone-1.jpg` is filled in |
| 00:25 | Zwrot: decyzja i czat | Submit, streamed decision, question "Jaką datę zakupu podałem?" |
| 00:43 | Reklamacja ze zdjęciem | Nowa sprawa, then a COMPLAINT with `laptop-1.png` |
| 01:14 | Czat: pytanie i nowe zdjęcie | `laptop-2.webp` attached to a chat question |
| 01:29 | Przekroczony termin | RETURN with a purchase 45 days ago |
| 01:58 | Bezpieczeństwo | Reason "Bateria spuchła i czuć swąd." |
| 02:29 | Błąd usługi i ponowienie | Invalid-key instance on `:8081`: error banner and "Spróbuj ponownie" |
| 02:52 | Nowa sprawa | Decision on a RETURN, then the confirmation dialog and the empty form |
| 03:20 | Widok mobilny | 390 px viewport: form, submit, decision and a chat question |

Chapter times are approximate (about 1-2 s).

## How it was made

The session was recorded as three raw clips (`test-results/demo-raw.webm`, `demo-raw2.webm`, `demo-raw3.webm`, not committed): the first covers chapters 1-7. In the first take the "Nowa sprawa" and mobile chapters did not perform the new-case step, so those two chapters were re-recorded as separate clips on the same JAR (the mobile one at a 390x844 viewport). Raw videos are 800x450 (the browser default recording size) and 390x844 and were scaled/padded to 1280x720.

Idle time (waiting for the model, frames unchanged for more than 1.5 s) was shortened to 1.6 s per pause with ffmpeg `freezedetect` plus a `select` filter; every action and every response is kept, nothing was edited otherwise. Raw total was about 12 min 40 s.

Encoding of each clip (the `select` filter list is generated from `freezedetect` output; `-t 402` was applied to the first clip), then concatenated with `-c copy`:

```
ffmpeg -i demo-raw.webm -t 402 -filter_script:v vf0.txt -c:v libx264 -preset slow -crf 22 -pix_fmt yuv420p -an part0.mp4
# vf0.txt: select='not(between(t,a,b)+...)',setpts=N/FRAME_RATE/TB,scale=1280:720:force_original_aspect_ratio=decrease,pad=1280:720:(ow-iw)/2:(oh-ih)/2:color=0x202020,setsar=1,fps=25
ffmpeg -f concat -safe 0 -i list.txt -c copy -movflags +faststart docs/demo/poc-demo.mp4
```

The plan's single-pass command (`-crf 28`, `scale='min(1280,iw)':-2`) was not used because the output was only about 6 MB and `-crf 22` keeps small text legible.

## Orchestrator review (2026-11-27)

The whole video was reviewed as contact sheets with one frame every 10 s (23 frames over 3:50). Every chapter shows the real flow: validation errors on an empty form; a RETURN decision ("Odrzucone") and the reply "Podałeś datę zakupu: 18 listopada 2026 r."; a COMPLAINT with `laptop-1.png` ("Przekazane do specjalisty"); a chat photo turn that keeps the decision; the 45-day return ("45 dni … 30 dni", "Odrzucone"); the safety case with the red safety block first; the error banner with "Spróbuj ponownie"; "Nowa sprawa"; and the 390 px mobile form and chat. The human lead still watches it in full as the final acceptance (plan T45).

## Human acceptance

Accepted by the human lead on 2026-11-27 ("wideo ok"), closing plan task T45.
