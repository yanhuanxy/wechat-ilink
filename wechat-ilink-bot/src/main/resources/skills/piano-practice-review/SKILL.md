---
name: piano-practice-review
description: >-
  Generate a structured, encouraging, expert review of a child's piano-practice
  video. Use this skill WHENEVER the user uploads a piano (or keyboard) practice
  video — file types like .mp4, .mov, .m4v, .avi, .mkv showing a child playing
  piano — and wants feedback, a 点评, 评价, 反馈, or "how did they do", EVEN IF
  they don't name this skill explicitly. Also trigger on phrases like "点评一下这段钢琴/弹琴视频",
  "帮我看看孩子练琴", "piano practice feedback", "review my kid's piano video".
  Do NOT use for adult concert performances, music theory questions, or non-video
  audio-only clips unless the user asks for the same review format.
compatibility: >-
  Requires a runtime with ffmpeg, ffprobe, numpy, scipy, and Pillow available
  (all standard in the Claude code-execution sandbox). No network access needed.
---

# 钢琴练习视频点评 (Piano Practice Review)

Turn an uploaded child's piano-practice video into a structured, warm, expert
review. The workflow below is fixed; the review *criteria* live in a separate
file so they can be iterated independently (see "Iterating the rubric").

## When this triggers

A user uploads a piano/keyboard practice video (a child playing) and wants
feedback. The video lands at `/mnt/user-data/uploads/<name>`. Proceed even if
they only say something short like "点评一下" or "看看弹得怎么样".

## Workflow — follow in order

### 1. Pick the rubric (do this first, every time)
Multiple review standards coexist in `references/rubrics/` — one file per level
(e.g. `beginner.md`, `intermediate.md`, `exam-prep.md`), and the user may add
more. Students grow, so the right standard changes over time. Choose **at your
discretion (酌情)**:

1. **List and read headers.** `ls references/rubrics/*.md`, then read the
   metadata comment at the top of each file. Each carries `RUBRIC_ID`,
   `RUBRIC_NAME`, `LEVEL`, `WHEN_TO_USE`, and `RUBRIC_VERSION`. Don't assume the
   set is fixed — always discover what's actually there.
2. **Select using this priority:**
   - If the user explicitly names a standard or level (e.g. "用考级标准点评",
     "按中级来", "this is exam prep"), use that one.
   - Else if the conversation / standing context tells you the child's level,
     match it to the closest rubric's `WHEN_TO_USE`.
   - Else infer from the **video evidence** — the sheet-music difficulty and
     repertoire visible in the frames, hand independence, use of pedal — and
     map it to the best-fitting `WHEN_TO_USE`.
   - If still genuinely ambiguous, default to `beginner` rather than stalling.
3. **Read the chosen file in full** and follow its structure exactly. Always
   read it fresh at runtime — rubrics are the iterable part and may have changed.
4. **State your choice transparently.** Put one line at the very top of the
   review naming the rubric and version used, and invite an override, e.g.:
   "本次按【中级 / 进阶 · v1.0】标准点评；如需换用其它标准（初级/考级冲刺），告诉我即可。"

### 2. Run the analysis script
Run the bundled script on the uploaded video:

```bash
python scripts/analyze_video.py "/mnt/user-data/uploads/<video>" --out /tmp/piano_frames
```

It extracts ~1 frame/second, ranks frames by sharpness, and (if there's an
audio track) prints approximate metrics: estimated tempo / notes-per-minute,
rhythm steadiness (`rhythm_cv`, lower = steadier), `onset_density_per_5s`
(a falling trend across windows usually means slowing / hesitation later in the
piece), `pauses_over_0_6s` with timestamps (likely stops or hesitations),
dynamics spread in dB (small = flat dynamics), and register movement across the
clip (rising then falling often = an ascending/descending scale or exercise).
A machine-readable copy sits between `BEGIN_JSON` and `END_JSON`.

### 3. Look at the frames
`view` several of the **sharpest** frames the script lists — they are best for:
- reading the **sheet music** (difficulty, single-staff exercise vs. grand-staff piece) and spotting the teacher's **red/colored markings** (zoom by cropping the sheet-music region with Pillow if needed);
- judging **hand shape** (knuckle bridge, finger curve, wrist height — crop the hands region and enlarge);
- judging **posture, focus, and engagement**.
Also glance at an early, a middle, and a late frame to track how the hands move
and whether the child stays engaged.

### 4. Write the review
Produce the review **strictly following the structure and rules in the rubric
you selected in step 1**. Ground every claim in actual evidence from the
frames and the audio metrics. Respect the rubric's honesty/boundary rules:
say so when the video is short / shaky / blurry, treat audio numbers as
approximate evidence (not precise fact), don't invent unreadable details
(measure numbers, exact piece title), and keep a friendly, age-appropriate,
encouraging tone throughout. Default to writing in the language the user used
(the rubric is written for Chinese output).

Open with the most useful first impression rather than narrating the tooling —
the parent should see the review, not a description of how frames were
extracted.

## Iterating the rubrics (multiple standards, coexisting)

The standards live as independent files in `references/rubrics/`, one per level,
so they can grow with the student without interfering with each other:

- **Edit a level's standard** → change only that one file (e.g.
  `intermediate.md`). Bump its `RUBRIC_VERSION` line so versions are traceable.
- **Add a whole new level** (e.g. a 高级/演奏级 standard, or a school-specific
  one) → copy an existing rubric file, rename it, and edit its metadata header
  (`RUBRIC_ID`, `RUBRIC_NAME`, `LEVEL`, `WHEN_TO_USE`, `RUBRIC_VERSION`). Step 1
  discovers files dynamically, so the new standard becomes selectable
  immediately — no other change needed.
- **Retire a standard** → delete or rename its file.

Because step 1 lists the folder and reads headers fresh every run, all of the
above take effect on the very next video. **Do not edit `SKILL.md` or
`scripts/analyze_video.py` for ordinary rubric changes** — touch those only when
the *workflow itself* changes (a new analysis metric, a different trigger
condition, or a change to how rubrics are selected).

Keep every rubric's metadata header intact and accurate — the `WHEN_TO_USE`
line is what lets the skill pick the right standard at its discretion.

## Notes & edge cases
- **No audio track**: the script says so; base the review on visuals only and note the limitation.
- **Very long video**: 1 fps is fine for a few minutes; for long clips the frame count just grows — still readable.
- **Not actually piano** (e.g. another instrument): tell the user this skill is tuned for piano and ask whether to proceed anyway.
- **Multiple videos**: review them one at a time, each with its own run.
