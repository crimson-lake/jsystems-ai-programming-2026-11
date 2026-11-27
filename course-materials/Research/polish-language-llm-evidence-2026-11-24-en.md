# Is Polish the best language for LLMs? Evidence review (as of 24 Nov 2026)

**Verdict: the claim is debunked.** No study shows that prompting in Polish, or in any Slavic language, makes LLMs better than prompting in English.

The 2025 headline came from a single long-context retrieval benchmark (OneRuler). The paper never tested prompting, and its co-author says the Polish–English gap is not statistically significant. Newer controlled evidence (2025–2026) on search, RAG, reasoning and real exams shows the following:

- **English is equal or better in almost every same-task comparison.**
- On **frontier models** the Polish gap is small: 0–6 points, and sometimes Polish wins by about 1 point.
- On **small or open models** the gap is large: 10–20 points.
- Polish text costs **about 1.3–1.9× more tokens**.

| Scope | What the newest evidence says | Strength |
|---|---|---|
| Original claim (OneRuler) | Polish 88% vs English 83.9%, only at 64K–128K context, averaged over 6 models. No significance test, and a different book per language. It measures needle retrieval, not prompting. | Verified in the paper; confounded |
| Search and retrieval | BM25 is weaker in Polish than English on all 11 paired BEIR datasets (−2.4 to −12.5 nDCG@10) | Medium (machine-translated data) |
| RAG | Mixing languages between query and documents is the biggest penalty (−7 to −42 pts Hit@20). LLMs prefer English sources. A 2026 deep-research benchmark loses 16–23 pp on translated corpora, and still loses 5–13 pp even with perfect retrieval. | Medium–strong; Polish rarely tested directly |
| Reasoning | Language affects reasoning through comprehension and the language of thought. Polish on frontier reasoning models: −0.8 to −5.6 pts (mmPISA, 2026). Older open model: GSM8K 0.87 EN vs 0.69 PL. | Medium–strong |
| Real Polish exams (medical) | Human-translated LEK: Llama-3.1-8B 51.3% PL vs 64.7% EN; Llama-70B 80.9 vs 82.6; **GPT-4o 90.0 PL vs 88.7 EN** | Strong (EMNLP 2025 Findings) |
| Other Slavic languages | No evidence of native-language superiority anywhere. Native-prompt effects are small and task-dependent (Slovak, Russian, Ukrainian). | Medium; mostly secondary sources |
| Cost | Polish fertility 2.25 vs English 1.23 tokens/word (6 tokenizers) | Strong |

---

## 1. The original claim, checked against the paper

The cryps.pl article, Euronews (Nov 2025), Polskie Radio and others built on **OneRuler**: Kim, Russell, Karpinska and Iyyer, *One ruler to measure them all*, COLM 2025, arXiv 2503.01996.

The lead model checked the following directly in the arXiv v3 text:

- **What was tested:** 7 synthetic tasks (needle-in-a-haystack variants and word aggregation) at 8K–128K tokens, in 26 languages.
- **Sample size:** 50 examples per configuration, which is 5.2K prompts per task per model.
- **The headline numbers:** *"at context lengths of 64K and 128K, we unexpectedly observe that Polish is the top performer on NIAH tasks"*, with 88% averaged across all models. English is *"only the 6th best language out of the 26"*, at 83.9%. Chinese is 4th from the bottom, at 62.1%.
- **One book per language:** *"26 open-domain non-copyrighted books, one per language"*. Polish used *Noce i dnie*, English used *Little Women*. So the haystack text differs in style, era and length. This book confound is enough on its own to make a 4-point gap uninterpretable.
- **Romance languages lead overall.** The Figure 3 caption says *"Romance languages perform best across all context lengths, along with Polish and Russian."* (The earlier report attributed this to Figure 4. That was corrected here.)
- **Instruction language matters more than the 4-point gap.** Instruction language alone moves accuracy by up to about 20 points. Example: English context at 64K drops from 91% to 71% with Korean instructions.
- **No significance test** is reported for language differences.

**What the co-author said** (Science in Poland / PAP, 27 Nov 2025): the difference was not statistically significant, and the study did not research prompting. *"We certainly cannot conclude"* from it that Polish is best for prompting. Spider's Web reported the same correction.

**What has happened since (2025–2026):** no replication of a Polish-first result was found. The newer long-context benchmarks either omit Polish or find English on top:

- MLRBench (EACL 2026): models use less than 30% of their claimed context.
- MGAL (ICML 2026): covers the six UN languages, so Russian is the only Slavic one.

---

## 2. Search, retrieval and RAG

**Classic retrieval.** BEIR vs BEIR-PL (Wojtasik et al., LREC-COLING 2024, Table 3) uses machine-translated datasets.

- BM25 nDCG@10 is lower in Polish on **all 11 paired datasets**. The gap ranges from 2.4 to 12.5 points (NQ −12.5, HotpotQA −11.0). The average is about 35.7 vs 42.4; that average is our own calculation, not the paper's.
- The authors explain the gap by Polish inflection. Lexical search suffers from it, so lemmatisation and dense or hybrid retrieval matter more in Polish.

**Polish embedders are good within Polish.**

- On PL-MTEB (ACL 2026 Findings), Qwen3-Embedding-8B has the best average (70.47).
- The best *retrieval* score is from the Polish model stella-pl-retrieval-8k: 61.59 vs 59.21.
- On PIRB (41 Polish tasks), multilingual-e5-large averages 57.29.
- Neither benchmark measures a cross-language gap.

**Multilingual RAG behaviour:**

- **Linguistic Nepotism** (Ki et al., ICML 2026): models cite English documents even when they are less relevant. For Russian, the only Slavic language tested, citation accuracy falls by 5.3–12.2 points vs English across six open models.
- **Park & Lee 2025:** retrievers prefer high-resource languages and the query's own language. For example, bge-m3 recall is 45.05 in Russian vs 56.03 in English.
- **The Cross-Lingual Cost** (arXiv 2507.07543; this is about retrieval *bias*, not money):
  - Arabic–English legal RAG loses 7–42 pts Hit@20 when the query and the documents are in different languages (M-E5: 88 same-language vs 46 cross-language).
  - Balanced retrieval recovers about 91–94% of that loss.
- **NEW: XBCP** (*Cross-Lingual BrowseComp-Plus*, Lu et al., arXiv 2606.15345, Jun 2026, preprint) tests deep-research agents on translated evidence. It covers 12 languages and no Slavic ones.
  - With Qwen3-Embedding-8B, accuracy drops by about **16–23 pp** when the corpus is translated.
  - The best system (DeepSeek-V4-Pro) scores 64.7 on the original corpus vs 42.3 cross-lingual.
  - **Even with perfect (oracle) evidence** the drop stays at 4.7–12.8 pp. The model is worse at *reading* evidence in another language, not only at finding it.
- **Polish RAG work in 2026** exists (Bartanowicz & Jassem, LREC 2026; Smits & Walkowiak, EACL 2026 SRW). We could not open the numbers, so we do not cite them. The latest PolEval retrieval task is from 2022.

**Bottom line:** nothing supports "Polish is best for search or RAG". The measurable lever is **language consistency**. Keep queries in the language of the corpus, use a multilingual or Polish embedder with a reranker, and expect generators to favour English sources in mixed indexes.

---

## 3. Can language change an LLM's reasoning level?

**Yes, through two mechanisms. First, how well the model *understands* the input. Second, which language the model *thinks* in. For high-resource languages such as Polish the effect is small on frontier models and large on small ones. No study shows a Slavic language *raising* reasoning above English.**

| Study | Setting | Result |
|---|---|---|
| **mmPISA-bench** (Sapenov & Savelka, CMU, arXiv 2606.07069, Jun 2026) **NEW** | 25 PISA items, official human translations in 43 languages, 5 repetitions | **Claude Opus 4.5 (high effort): EN 100.0 vs PL 94.4. GPT-5.1 (high): EN 96.8 vs PL 96.0. GPT-5.1 (no reasoning): PL 78.4 > EN 76.8.** Reasoning effort raises the mean over all languages from 78.9 to 95.7 (GPT). |
| Thellmann et al. 2024, EU20 | Llama-3.1-70B, DeepL-translated | GSM8K **EN 0.87 vs PL 0.69, CS 0.75** |
| PolyMath (NeurIPS 2025) | 18 languages, no Polish | Reasoning models keep their chain of thought in the input language only 34–43% of the time (non-reasoning models: 99%+). Qwen3-235B thinking: RU ≈ EN (77.6 vs 76.8 at medium level). |
| Tam et al. 2025, *Language Matters* | MATH-500, forced English vs native thinking | English prefill helps Swahili by +31.5 pp on average, Russian by +5.1, and Qwen3/QwQ on Russian by only +0.6 |
| Barua et al. (ICLR 2026) | Long chain of thought, 32B models | Forcing target-language reasoning costs 28.8% on average; AIME 65.5 English CoT vs 24.7 target CoT (Nemotron-49B) |
| Saji et al. 2025, *Reasoning Lingua Franca* | MGSM, GPQA Diamond | Native-language reasoning costs accuracy, and more so for low-resource languages (per-language numbers are only in figures) |
| Yong et al. 2025 | s1-14B | Russian reasoning 92.4 = English 92.4 |
| Liu et al. (NAACL 2025) | ChatGPT | Translating to English helps low-resource math; native prompts are better on culture-related tasks |

**Interpretability** work points the same way: Wendler et al. (ACL 2024), *Semantic Hub* (ICLR 2025) and Anthropic's *Tracing the thoughts* (2025). Models share a mostly language-independent concept space that grows with model size. This explains why the prompt language matters less on larger models.

**Vendor tables:**

- Anthropic's Global-MMLU table (fetched 24 Nov 2026) puts every listed language at **98.5–99.8% of English** for Claude Fable 5.2, and 96.3–99.3% for Opus 5.5. **Polish is not listed.**
- OpenAI's MMMLU covers 14 locales and no Polish.

**Side effect:** for Russian, the attack success rate was 16.1% with Russian prompts vs 11.5% with English ones (Tam et al., Table 3). Safety tuning can be weaker outside English.

---

## 4. Direct Polish evidence (same items, PL vs EN)

**Polish–English medical knowledge transfer** (Grzybowski et al., Adam Mickiewicz University, *Findings of EMNLP 2025*, arXiv 2412.00559) is **the best Polish-specific study found**. It uses Polish medical licensing exams with **professional human English translations** (Table 4):

| Model | LEK PL | LEK EN | LDEK PL | LDEK EN |
|---|---|---|---|---|
| Llama-3.1-8B | 51.25 | 64.69 | 41.06 | 47.71 |
| Llama-3.1-70B | 80.94 | 82.60 | 61.75 | 63.17 |
| GPT-4o | **89.96** | 88.69 | **73.89** | 72.51 |
| GPT-4o-mini | 75.88 | 75.92 | 54.94 | 56.88 |
| Qwen2.5-72B | 76.87 | 81.93 | 58.35 | 63.33 |
| Mistral-Large-2407 | 76.75 | 82.40 | 56.29 | 62.14 |
| Bielik-11B-v2.2 | **62.36** | 56.98 | **43.20** | 42.88 |

Two things stand out:

- **Llama-3.1-8B is far behind in Polish.** It passed one LEK exam in Polish but all 13 in English.
- **GPT-4o and the Polish model Bielik do better in Polish.** The authors: *"language transfer is more effective as the model's general performance improves"*.

Other Polish data points:

- **LLMzSzŁ** (arXiv 2501.02266, ~19k Polish exam questions): models of about 70B parameters perform similarly in Polish and English, while small models do best in their main training language.
- **PLCC** (600 Polish culture questions, 2025): frontier multilingual models lead. Gemini-Exp-1206 scores 83.0, Claude 3.5 Sonnet 82.7, GPT-4o 81.3, and Bielik-2.3 62.2. This measures Polish *knowledge*, not prompt language.
- **MoT-PL** (Wrocław Tech, ICCS 2026): fine-tuning Polish models on Polish reasoning traces improved Polish reasoning but *degraded* English reasoning (up to −45% relative on LightR1). This is a capacity trade-off, not evidence that Polish is better.

---

## 5. Other Slavic languages (searched in their own languages)

We ran about 44 native-language queries in Czech, Slovak, Ukrainian, Russian, Bulgarian, Serbian, Croatian, Slovene and Polish.

Serbian and Croatian got 3–4 queries each because of the budget. They returned only encoder-model benchmarks, so these two languages remain a gap.

| Language | Source | Finding | Confidence |
|---|---|---|---|
| Ukrainian | Galeshchuk et al., UNLP 2026 (347 expert questions) | Large LLMs reach 59.6% at most and small ones 42.1%, with prompts in Ukrainian and in English | Secondary |
| Ukrainian | UNLP 2026 grammar-correction paper | Ukrainian prompts were best for grammar correction; in zero-shot only Claude gained from Ukrainian instructions | Secondary (summary) |
| Russian | MERA Code (arXiv 2507.12284) | Russian vs English prompts: near-zero median difference | Secondary |
| Russian | Russian press (kod.ru, 3dnews) | Repeated the OneRuler ranking with **conflicting numbers** | Press only |
| Slovak | Harvan / Kopčan et al., LoResNLP 2025 | Native Slovak prompts give a small, task-dependent gain; instructions matter more than language | Secondary |
| Bulgarian | BgGPT 1.0 (INSAIT) | The authors state reduced capability when prompted in Bulgarian; BgGPT 3.0 (Mar 2026) claims gains, but these are vendor claims | Abstract-level |
| Bulgarian | MMLU-BG (LREC 2026) | 7 LLMs on English MMLU vs Bulgarian MMLU-BG | Numbers not obtained |
| Czech | BenCzechMark (TACL 2025), 50 tasks | Multilingual models beat Czech-specific ones; no direct English-vs-Czech comparison | Primary metadata |
| Slovene | GaMS3-12B (Mar 2026) | Self-reported win rate above 60% vs GPT-4o in a Slovene arena | Self-reported |
| South Slavic | Kuzman Pungeršek et al. 2025 | Zero-shot classification in Croatian, Serbian, Bosnian and Slovene is comparable to English | Secondary |

**Bottom line:** across the Slavic family, native-language performance is at best on par with English on large models and lower on small ones. Prompt language has a modest, task-dependent effect when the content stays native.

---

## 6. Token cost

| Source | Polish vs English |
|---|---|
| Ovcharov 2026, *Tokenizer Tax* (arXiv 2605.24718, Table 4; 6 tokenizers incl. GPT-4o and Llama 4) | Fertility **2.25 vs 1.23 tokens/word** (about 1.8×). Czech 2.28, Ukrainian 2.66 |
| Bielik v3 tokenizer paper (arXiv 2604.10799, Table 1) | Polish needs about **1.33–1.83×** the tokens of English on Gemma 3, Qwen3, Llama 3.2 and Mistral. A Polish-optimised tokenizer reverses this. |
| Lundin et al. 2025, *Token Tax* (arXiv 2509.05486) | Each extra token per word lowers accuracy by about 0.08–0.18 (tested on African languages, not Slavic ones) |

More tokens mean higher API bills, less usable context and more latency, so the same Polish prompt typically costs **30–90% more**. No primary source measures OpenAI o200k, Claude and Gemini tokenizers on the same Polish corpus. Run your own measurement with the vendors' count-tokens endpoints.

---

## 7. Practical recommendations

1. **Match the query language to the corpus language.** Cross-language RAG is the largest measured penalty.
2. **For instructions and system prompts, English is the safe default** when cost or small-model accuracy matters. Add "Respond in Polish". On frontier models, Polish prompts are fine: the gap is 0–6 points and sometimes in Polish's favour.
3. **For Polish law, medicine, culture and style, keep the content in Polish** and use a strong frontier or Polish model. The best models gain little or nothing from translation.
4. **On small or open models, let them think in English** (or translate hard inputs), then answer in Polish.
5. **Use dense or hybrid retrieval with a reranker** for Polish (Qwen3-Embedding, BGE-M3, multilingual-e5, stella-pl). Plain BM25 loses on inflection.
6. **Evaluate on your own data.** Build 50–200 real prompts with expected answers and run them in both languages. Public benchmarks rarely include Polish.

---

## 8. Method and quality control

- **Research:** 5 parallel Claude Haiku agents covered long context, search/RAG, reasoning, native-language Slavic sources and tokens/Polish benchmarks. Together they made about 165 tool calls. They searched in English, Polish, Czech, Slovak, Ukrainian, Russian, Bulgarian, Serbian, Croatian and Slovene.
- **Verification:** the lead model (Claude Opus 5.5) rechecked the key claims in three extra fetch passes against primary text:
  - OneRuler v3 HTML: sample size, the 64K/128K scope, one book per language, the Figure 3 caption, and the 11% → 34% gap
  - the EMNLP 2025 medical paper, Table 4
  - mmPISA-bench, Table 1
  - XBCP, Tables 1, 4 and 5
  - MoT-PL, Table 4
  - Anthropic's multilingual table
- **Corrections to the earlier version of this report:**
  - The "Romance languages best" quote is from Figure 3, not Figure 4.
  - arXiv 2507.07543 is about retrieval bias, not cost.
  - The Linguistic Nepotism range for Russian is 5.3–12.2 pts.
  - The Galeshchuk et al. paper is a regular UNLP paper, not a shared task.
  - The BgGPT 1.0 table values (75.08/60.51) could not be re-verified, so they were dropped.
- **Quality issues found in the agents' work:**
  - The Slavic agent relied mostly on search snippets.
  - Two agents' fetch summaries contained errors, which the verbatim text later caught. For example, one summary claimed Llama-8B was not in the medical paper, and another claimed mmPISA had no Table 1.
  - Numbers marked *secondary* above were not read in the original tables.
- **Main limitation:** Polish is missing from most large multilingual reasoning and RAG benchmarks, and there is no 2026 Polish-vs-English *prompt* study on current frontier models.

## Sources
- OneRuler (COLM 2025): https://arxiv.org/abs/2503.01996 · HTML v3 https://arxiv.org/html/2503.01996v3
- Karpińska interview (Science in Poland): https://scienceinpoland.pl/en/news/news,110407,polish-language-not-superior-ai-prompting-researchers-say.html
- Spider's Web follow-up: https://spidersweb.pl/2025/11/polski-jezyk-ai-microsoft-dementuje-oneruler.html
- Original article: https://cryps.pl/sztuczna-inteligencja-mowi-po-polsku-nasz-jezyk-zdeklasowal-angielski-w-najnowszym-rankingu/
- MLRBench (EACL 2026): https://aclanthology.org/2026.eacl-long.290/ · MGAL (ICML 2026): https://proceedings.mlr.press/v306/li26ib.html
- BEIR-PL: https://arxiv.org/abs/2305.19840 · PIRB: https://arxiv.org/abs/2402.13350 · PL-MTEB: https://arxiv.org/abs/2405.10138
- Linguistic Nepotism (ICML 2026): https://proceedings.mlr.press/v306/ki26a.html
- Park & Lee 2025: https://arxiv.org/abs/2502.11175 · Cross-Lingual Cost: https://arxiv.org/abs/2507.07543
- XBCP (2026): https://arxiv.org/abs/2606.15345
- Polish RAG 2026: https://aclanthology.org/2026.eacl-srw.55
- mmPISA-bench (2026): https://arxiv.org/abs/2606.07069
- Thellmann et al. EU20: https://arxiv.org/abs/2410.08928
- PolyMath: https://arxiv.org/abs/2504.18428 · Language Matters: https://arxiv.org/abs/2505.17407 · Barua et al.: https://arxiv.org/abs/2508.14828 · Reasoning Lingua Franca: https://arxiv.org/abs/2510.20647 · Yong et al.: https://arxiv.org/abs/2505.05408 · Liu et al. (NAACL 2025): https://aclanthology.org/2025.naacl-long.485
- Do Multilingual LLMs Think in English?: https://arxiv.org/abs/2502.15603 · Anthropic, Tracing the thoughts: https://www.anthropic.com/research/tracing-thoughts-language-model
- Anthropic multilingual support: https://platform.claude.com/docs/en/build-with-claude/multilingual-support · OpenAI MMMLU: https://huggingface.co/datasets/openai/MMMLU
- Polish–English medical knowledge transfer (EMNLP 2025 Findings): https://arxiv.org/abs/2412.00559
- LLMzSzŁ: https://arxiv.org/abs/2501.02266 · PLCC: https://arxiv.org/abs/2503.00995 · MoT-PL: https://arxiv.org/abs/2605.13290 · PLLuM: https://arxiv.org/abs/2511.03823 · Bielik 11B v3: https://arxiv.org/abs/2601.11579
- UNLP 2026 (Ukrainian): https://aclanthology.org/2026.unlp-1.12/ · MERA Code: https://arxiv.org/abs/2507.12284 · Slovak prompts: https://aclanthology.org/2025.lowresnlp-1.10 · BgGPT 1.0: https://arxiv.org/abs/2412.10893 · MMLU-BG: https://aclanthology.org/2026.lrec-1.366/ · BenCzechMark: https://aclanthology.org/2025.tacl-1.50 · GaMS3: https://arxiv.org/abs/2603.01691 · Serbian SuperGLUE: https://aclanthology.org/2026.loreslm-1.30
- Tokenizer Tax (Ovcharov 2026): https://arxiv.org/abs/2605.24718 · Bielik v3 tokenizer: https://arxiv.org/abs/2604.10799 · Token Tax (Lundin et al.): https://arxiv.org/abs/2509.05486 · Petrov et al. (NeurIPS 2023): https://arxiv.org/abs/2305.15425
