# Czy polski to najlepszy język dla LLM? Przegląd dowodów (stan na 24.11.2026)

**Werdykt: teza jest obalona.** Żadne badanie nie pokazuje, że promptowanie po polsku albo w innym języku słowiańskim daje lepsze wyniki niż po angielsku.

Nagłówki z 2025 roku wzięły się z jednego benchmarku wyszukiwania w długim kontekście (OneRuler). Ta praca w ogóle nie badała promptowania, a współautorka mówi, że różnica między polskim a angielskim nie jest istotna statystycznie. Nowsze kontrolowane badania (2025–2026) dotyczące wyszukiwania, RAG, rozumowania i prawdziwych egzaminów pokazują, że:

- **angielski jest równy albo lepszy niemal w każdym porównaniu na tym samym zadaniu**,
- na **modelach frontier** strata polskiego jest mała: 0–6 pkt, a czasem polski wygrywa o ok. 1 pkt,
- na **małych i otwartych modelach** strata jest duża: 10–20 pkt,
- polski tekst kosztuje **ok. 1,3–1,9× więcej tokenów**.

| Obszar | Co mówią najnowsze dowody | Siła dowodów |
|---|---|---|
| Pierwotna teza (OneRuler) | Polski 88% vs angielski 83,9%, tylko przy kontekście 64K–128K, średnio dla 6 modeli. Brak testu istotności, inna książka dla każdego języka. Mierzy wyszukiwanie „igły”, nie promptowanie. | Sprawdzone w publikacji; wyniki zaburzone |
| Wyszukiwanie | BM25 słabszy po polsku niż po angielsku na wszystkich 11 sparowanych zbiorach BEIR (od −2,4 do −12,5 nDCG@10) | Średnia (dane tłumaczone maszynowo) |
| RAG | Największa kara to różne języki zapytania i dokumentów (od −7 do −42 pkt Hit@20). LLM-y wolą źródła angielskie. Benchmark deep research z 2026: −16 do −23 pp na przetłumaczonym korpusie, a nawet przy idealnym wyszukiwaniu −5 do −13 pp. | Średnia–mocna; polski rzadko testowany wprost |
| Rozumowanie | Język wpływa przez rozumienie wejścia i język, w którym model myśli. Polski na modelach frontier z rozumowaniem: od −0,8 do −5,6 pkt (mmPISA, 2026). Starszy model otwarty: GSM8K 0,87 EN vs 0,69 PL. | Średnia–mocna |
| Prawdziwe polskie egzaminy (medyczne) | LEK tłumaczony przez ludzi: Llama-3.1-8B 51,3% PL vs 64,7% EN; Llama-70B 80,9 vs 82,6; **GPT-4o 90,0 PL vs 88,7 EN** | Mocna (EMNLP 2025 Findings) |
| Inne języki słowiańskie | Nigdzie brak przewagi języka natywnego; efekt natywnego promptu mały i zależny od zadania (słowacki, rosyjski, ukraiński) | Średnia; głównie źródła wtórne |
| Koszt | Polski: 2,25 tokena/słowo vs angielski 1,23 (6 tokenizerów) | Mocna |

---

## 1. Pierwotna teza sprawdzona w publikacji

Artykuł cryps.pl, Euronews (XI 2025), Polskie Radio i inne media oparły się na **OneRuler**: Kim, Russell, Karpinska, Iyyer, *One ruler to measure them all*, COLM 2025, arXiv 2503.01996.

Model prowadzący sprawdził bezpośrednio w tekście arXiv v3:

- **Co badano:** 7 syntetycznych zadań (warianty needle-in-a-haystack i agregacja słów) przy 8K–128K tokenów, w 26 językach.
- **Wielkość próby:** 50 przykładów na konfigurację, czyli 5,2 tys. promptów na zadanie i model.
- **Liczby z nagłówków:** *„at context lengths of 64K and 128K, we unexpectedly observe that Polish is the top performer on NIAH tasks”* (88% średnio dla wszystkich modeli). Angielski to *„only the 6th best language out of the 26”*, z wynikiem 83,9%. Chiński jest 4. od końca (62,1%).
- **Jedna książka na język:** *„26 open-domain non-copyrighted books, one per language”*. Dla polskiego były to *Noce i dnie*, dla angielskiego *Małe kobietki*, więc tekst tła różni się stylem, epoką i długością. Już samo to wystarcza, żeby różnicy 4 pkt nie dało się zinterpretować.
- **Ogólnie prowadzą języki romańskie.** Podpis rys. 3: *„Romance languages perform best across all context lengths, along with Polish and Russian.”* (Poprzednia wersja raportu przypisywała ten cytat rys. 4; poprawione.)
- **Język instrukcji waży więcej niż różnica 4 pkt.** Sam język instrukcji zmienia trafność nawet o ok. 20 pkt. Przykład: angielski kontekst przy 64K spada z 91% do 71% z instrukcją po koreańsku.
- **Brak testu istotności** dla różnic między językami.

**Co mówi współautorka** (Nauka w Polsce / PAP, 27.11.2025): różnica nie była istotna statystycznie, a badanie nie dotyczyło promptowania; *„na pewno nie możemy wnioskować”* na jego podstawie, że polski jest najlepszy do promptowania. To samo sprostowanie podał Spider's Web.

**Co od tamtej pory (2025–2026):** nie znaleziono żadnej replikacji „polskiego zwycięstwa”. Nowsze benchmarki długiego kontekstu albo pomijają polski, albo stawiają angielski na czele:

- MLRBench (EACL 2026): modele wykorzystują poniżej 30% deklarowanego kontekstu.
- MGAL (ICML 2026): obejmuje 6 języków ONZ, więc jedynym słowiańskim jest rosyjski.

---

## 2. Wyszukiwanie, retrieval i RAG

**Klasyczny retrieval.** BEIR vs BEIR-PL (Wojtasik i in., LREC-COLING 2024, tab. 3) to porównanie na zbiorach tłumaczonych maszynowo.

- BM25 nDCG@10 jest niższy po polsku na **wszystkich 11 sparowanych zbiorach**. Różnica wynosi od 2,4 do 12,5 pkt (NQ −12,5, HotpotQA −11,0). Średnio ok. 35,7 vs 42,4; tę średnią wyliczyliśmy sami, nie podaje jej publikacja.
- Autorzy tłumaczą to fleksją polszczyzny, na której traci wyszukiwanie leksykalne. Dlatego po polsku bardziej liczą się lematyzacja i wyszukiwanie dense lub hybrydowe.

**Polskie embeddery są dobre w obrębie polskiego.**

- Na PL-MTEB (ACL 2026 Findings) najlepszą średnią ma Qwen3-Embedding-8B (70,47).
- Najlepszy wynik w samym retrievalu ma polski stella-pl-retrieval-8k: 61,59 vs 59,21.
- Na PIRB (41 polskich zadań) multilingual-e5-large osiąga średnio 57,29.
- Żaden z tych benchmarków nie mierzy różnicy między językami.

**Zachowanie wielojęzycznego RAG:**

- **Linguistic Nepotism** (Ki i in., ICML 2026): modele cytują dokumenty angielskie, nawet gdy są mniej trafne. Dla rosyjskiego, jedynego badanego języka słowiańskiego, trafność cytowań spada o 5,3–12,2 pkt względem angielskiego w sześciu otwartych modelach.
- **Park & Lee 2025:** retrievery wolą języki wysokozasobowe i język zapytania. Przykład: recall bge-m3 po rosyjsku 45,05 vs po angielsku 56,03.
- **The Cross-Lingual Cost** (arXiv 2507.07543; chodzi o *bias* retrievalu, nie o pieniądze):
  - w prawniczym RAG arabsko-angielskim różne języki zapytania i dokumentów kosztują 7–42 pkt Hit@20 (M-E5: 88 w tym samym języku vs 46 między językami),
  - zbalansowany retrieval odzyskuje ok. 91–94% tej straty.
- **NOWE: XBCP** (*Cross-Lingual BrowseComp-Plus*, Lu i in., arXiv 2606.15345, VI 2026, preprint) testuje agentów deep research na przetłumaczonych dokumentach. Obejmuje 12 języków, żadnego słowiańskiego.
  - Z Qwen3-Embedding-8B trafność spada o ok. **16–23 pp**, gdy korpus jest przetłumaczony.
  - Najlepszy system (DeepSeek-V4-Pro): 64,7 na oryginalnym korpusie vs 42,3 między językami.
  - **Nawet z idealnymi (oracle) dowodami** spadek wynosi 4,7–12,8 pp. Model gorzej *czyta* dokumenty w innym języku, a nie tylko gorzej je znajduje.
- **Polski RAG w 2026** jest badany (Bartanowicz i Jassem, LREC 2026; Smits i Walkowiak, EACL 2026 SRW), ale nie udało się otworzyć liczb, więc ich nie cytujemy. Ostatnie zadanie retrievalowe PolEval jest z 2022 roku.

**Wniosek:** nic nie wspiera tezy „polski jest najlepszy do wyszukiwania i RAG”. Mierzalnym czynnikiem jest **spójność językowa**. Zadawaj pytania w języku korpusu, używaj wielojęzycznego lub polskiego embeddera z rerankerem i licz się z tym, że w mieszanym indeksie generator będzie wolał źródła angielskie.

---

## 3. Czy język może zmienić poziom rozumowania LLM?

**Tak, przez dwa mechanizmy: to, jak dobrze model *rozumie* wejście, oraz to, w jakim języku model *myśli*. Dla języków wysokozasobowych, takich jak polski, efekt jest mały na modelach frontier i duży na małych. Żadne badanie nie pokazuje, by język słowiański *podnosił* rozumowanie powyżej angielskiego.**

| Badanie | Ustawienie | Wynik |
|---|---|---|
| **mmPISA-bench** (Sapenov i Savelka, CMU, arXiv 2606.07069, VI 2026) **NOWE** | 25 zadań PISA, oficjalne tłumaczenia ludzkie na 43 języki, 5 powtórzeń | **Claude Opus 4.5 (wysoki effort): EN 100,0 vs PL 94,4. GPT-5.1 (wysoki): EN 96,8 vs PL 96,0. GPT-5.1 (bez rozumowania): PL 78,4 > EN 76,8.** Wysiłek rozumowania podnosi średnią ze wszystkich języków z 78,9 do 95,7 (GPT). |
| Thellmann i in. 2024, EU20 | Llama-3.1-70B, tłumaczenie DeepL | GSM8K **EN 0,87 vs PL 0,69, CS 0,75** |
| PolyMath (NeurIPS 2025) | 18 języków, bez polskiego | Modele z rozumowaniem prowadzą łańcuch myśli w języku wejścia tylko w 34–43% przypadków (modele bez rozumowania: 99%+). Qwen3-235B z myśleniem: RU ≈ EN (77,6 vs 76,8 na poziomie średnim). |
| Tam i in. 2025, *Language Matters* | MATH-500, wymuszone myślenie po angielsku vs w języku natywnym | Angielski prefill pomaga w suahili średnio o +31,5 pp, w rosyjskim o +5,1, a dla Qwen3/QwQ po rosyjsku tylko o +0,6 |
| Barua i in. (ICLR 2026) | Długie łańcuchy myśli, modele 32B | Wymuszenie rozumowania w języku docelowym kosztuje średnio 28,8%; AIME 65,5 (CoT po angielsku) vs 24,7 (CoT w języku docelowym), Nemotron-49B |
| Saji i in. 2025, *Reasoning Lingua Franca* | MGSM, GPQA Diamond | Rozumowanie w języku natywnym obniża trafność, mocniej dla języków niskozasobowych (liczby per język tylko na wykresach) |
| Yong i in. 2025 | s1-14B | Rozumowanie po rosyjsku 92,4 = po angielsku 92,4 |
| Liu i in. (NAACL 2025) | ChatGPT | Tłumaczenie na angielski pomaga w matematyce dla języków niskozasobowych; prompty natywne lepsze w zadaniach kulturowych |

**Interpretowalność** wskazuje to samo: Wendler i in. (ACL 2024), *Semantic Hub* (ICLR 2025) i *Tracing the thoughts* Anthropic (2025). Modele mają wspólną, w dużej mierze niezależną od języka przestrzeń pojęć, która rośnie wraz z rozmiarem modelu. Dlatego na większych modelach język promptu znaczy mniej.

**Tabele dostawców:**

- Tabela Global-MMLU Anthropic (pobrana 24.11.2026) daje każdemu wymienionemu językowi **98,5–99,8% wyniku angielskiego** dla Claude Fable 5.2 i 96,3–99,3% dla Opus 5.5. **Polskiego nie ma w tabeli.**
- OpenAI MMMLU obejmuje 14 lokalizacji, bez polskiego.

**Efekt uboczny:** dla rosyjskiego skuteczność ataków wynosiła 16,1% przy promptach rosyjskich vs 11,5% przy angielskich (Tam i in., tab. 3). Zabezpieczenia mogą działać słabiej poza angielskim.

---

## 4. Bezpośrednie dane dla polskiego (te same pytania PL vs EN)

**Polish–English medical knowledge transfer** (Grzybowski i in., UAM, *Findings of EMNLP 2025*, arXiv 2412.00559) to **najlepsze znalezione badanie dla polskiego**. Wykorzystuje polskie egzaminy lekarskie z **profesjonalnymi tłumaczeniami na angielski wykonanymi przez ludzi** (tab. 4):

| Model | LEK PL | LEK EN | LDEK PL | LDEK EN |
|---|---|---|---|---|
| Llama-3.1-8B | 51,25 | 64,69 | 41,06 | 47,71 |
| Llama-3.1-70B | 80,94 | 82,60 | 61,75 | 63,17 |
| GPT-4o | **89,96** | 88,69 | **73,89** | 72,51 |
| GPT-4o-mini | 75,88 | 75,92 | 54,94 | 56,88 |
| Qwen2.5-72B | 76,87 | 81,93 | 58,35 | 63,33 |
| Mistral-Large-2407 | 76,75 | 82,40 | 56,29 | 62,14 |
| Bielik-11B-v2.2 | **62,36** | 56,98 | **43,20** | 42,88 |

Dwie rzeczy rzucają się w oczy:

- **Llama-3.1-8B wyraźnie przegrywa po polsku.** Zdała jeden LEK po polsku, ale wszystkie 13 po angielsku.
- **GPT-4o i polski Bielik radzą sobie lepiej po polsku.** Autorzy: *„language transfer is more effective as the model's general performance improves”*.

Inne dane dla polskiego:

- **LLMzSzŁ** (arXiv 2501.02266, ok. 19 tys. pytań z polskich egzaminów): modele rzędu 70B parametrów wypadają podobnie po polsku i po angielsku, a małe najlepiej w swoim głównym języku treningowym.
- **PLCC** (600 pytań o polską kulturę, 2025): prowadzą wielojęzyczne modele frontier. Gemini-Exp-1206 ma 83,0, Claude 3.5 Sonnet 82,7, GPT-4o 81,3, a Bielik-2.3 62,2. To miara *wiedzy* o Polsce, nie języka promptu.
- **MoT-PL** (Politechnika Wrocławska, ICCS 2026): douczenie polskich modeli na polskich śladach rozumowania poprawiło rozumowanie po polsku, ale *pogorszyło* je po angielsku (do −45% względnie na LightR1). To kompromis pojemności modelu, nie dowód wyższości polskiego.

---

## 5. Inne języki słowiańskie (wyszukiwanie w ich własnych językach)

Wykonaliśmy ok. 44 zapytań w językach natywnych: czeskim, słowackim, ukraińskim, rosyjskim, bułgarskim, serbskim, chorwackim, słoweńskim i polskim.

Serbski i chorwacki dostały po 3–4 zapytania ze względu na budżet. Zwróciły tylko benchmarki modeli enkoderowych, więc te dwa języki pozostają luką.

| Język | Źródło | Wynik | Pewność |
|---|---|---|---|
| Ukraiński | Galeshchuk i in., UNLP 2026 (347 pytań eksperckich) | Duże LLM-y osiągają najwyżej 59,6%, małe 42,1%; prompty po ukraińsku i po angielsku | Wtórna |
| Ukraiński | Praca UNLP 2026 o korekcie gramatyki | Prompty ukraińskie najlepsze w korekcie gramatyki; w zero-shot tylko Claude zyskał na instrukcjach po ukraińsku | Wtórna (streszczenie) |
| Rosyjski | MERA Code (arXiv 2507.12284) | Prompty rosyjskie vs angielskie: mediana różnicy bliska zeru | Wtórna |
| Rosyjski | Prasa rosyjska (kod.ru, 3dnews) | Powtórzyła ranking OneRuler ze **sprzecznymi liczbami** | Tylko prasa |
| Słowacki | Harvan / Kopčan i in., LoResNLP 2025 | Natywne prompty słowackie dają mały zysk zależny od zadania; instrukcje ważniejsze niż język | Wtórna |
| Bułgarski | BgGPT 1.0 (INSAIT) | Autorzy piszą o słabszych wynikach przy promptach po bułgarsku; BgGPT 3.0 (III 2026) deklaruje poprawę, ale to deklaracje producenta | Poziom abstraktu |
| Bułgarski | MMLU-BG (LREC 2026) | 7 LLM-ów na angielskim MMLU vs bułgarskim MMLU-BG | Nie uzyskano liczb |
| Czeski | BenCzechMark (TACL 2025), 50 zadań | Modele wielojęzyczne wygrywają z czeskimi; brak bezpośredniego porównania z angielskim | Metadane pierwotne |
| Słoweński | GaMS3-12B (III 2026) | Deklarowany ponad 60% wskaźnik wygranych z GPT-4o na słoweńskiej arenie | Deklaracja autorów |
| Południowosłowiańskie | Kuzman Pungeršek i in. 2025 | Klasyfikacja zero-shot po chorwacku, serbsku, bośniacku i słoweńsku porównywalna z angielską | Wtórna |

**Wniosek:** w całej rodzinie słowiańskiej wyniki w języku natywnym są w najlepszym razie równe angielskim na dużych modelach i niższe na małych. Gdy treść pozostaje w języku natywnym, język promptu ma umiarkowany wpływ, zależny od zadania.

---

## 6. Koszt tokenów

| Źródło | Polski vs angielski |
|---|---|
| Ovcharov 2026, *Tokenizer Tax* (arXiv 2605.24718, tab. 4; 6 tokenizerów, m.in. GPT-4o i Llama 4) | **2,25 vs 1,23 tokena na słowo** (ok. 1,8×); czeski 2,28, ukraiński 2,66 |
| Publikacja o tokenizerze Bielik v3 (arXiv 2604.10799, tab. 1) | Polski potrzebuje ok. **1,33–1,83×** tokenów angielskiego w Gemma 3, Qwen3, Llama 3.2 i Mistral; tokenizer zoptymalizowany pod polski odwraca tę proporcję |
| Lundin i in. 2025, *Token Tax* (arXiv 2509.05486) | Każdy dodatkowy token na słowo obniża trafność o ok. 0,08–0,18 (badane języki afrykańskie, nie słowiańskie) |

Więcej tokenów to wyższy rachunek za API, mniej użytecznego kontekstu i większe opóźnienie. Ten sam prompt po polsku kosztuje zwykle **30–90% więcej**. Żadne źródło pierwotne nie mierzy tokenizerów OpenAI o200k, Claude i Gemini na tym samym polskim korpusie. Zmierz to sam przez endpointy count-tokens dostawców.

---

## 7. Rekomendacje praktyczne

1. **Pytaj w języku korpusu.** Różne języki zapytania i dokumentów to największa zmierzona kara w RAG.
2. **Instrukcje i system prompt pisz domyślnie po angielsku**, gdy liczy się koszt albo trafność małego modelu, i dodaj „Odpowiadaj po polsku”. Na modelach frontier polskie prompty są w porządku: różnica to 0–6 pkt, czasem na korzyść polskiego.
3. **Prawo, medycynę, kulturę i styl zostaw po polsku** i użyj mocnego modelu frontier albo polskiego. Najlepsze modele niewiele albo nic nie zyskują na tłumaczeniu.
4. **Małym i otwartym modelom pozwól myśleć po angielsku** (albo tłumacz trudne wejścia), a odpowiedź niech podają po polsku.
5. **Do polskiego używaj wyszukiwania dense lub hybrydowego z rerankerem** (Qwen3-Embedding, BGE-M3, multilingual-e5, stella-pl). Sam BM25 traci na fleksji.
6. **Testuj na własnych danych.** Przygotuj 50–200 prawdziwych promptów z oczekiwanymi odpowiedziami i uruchom je w obu językach. Publiczne benchmarki rzadko obejmują polski.

---

## 8. Metoda i kontrola jakości

- **Research:** 5 równoległych agentów Claude Haiku zajęło się długim kontekstem, wyszukiwaniem/RAG, rozumowaniem, źródłami słowiańskimi w językach natywnych oraz tokenami i polskimi benchmarkami. Razem wykonali ok. 165 wywołań narzędzi. Szukali po angielsku, polsku, czesku, słowacku, ukraińsku, rosyjsku, bułgarsku, serbsku, chorwacku i słoweńsku.
- **Weryfikacja:** model prowadzący (Claude Opus 5.5) w trzech dodatkowych przebiegach sprawdził kluczowe tezy w tekstach źródłowych:
  - OneRuler v3 HTML: wielkość próby, zakres 64K/128K, jedna książka na język, podpis rys. 3 i wzrost luki z 11% do 34%,
  - publikacja medyczna z EMNLP 2025, tab. 4,
  - mmPISA-bench, tab. 1,
  - XBCP, tab. 1, 4 i 5,
  - MoT-PL, tab. 4,
  - tabela wielojęzyczności Anthropic.
- **Poprawki względem wcześniejszej wersji raportu:**
  - Cytat o językach romańskich pochodzi z rys. 3, nie z rys. 4.
  - arXiv 2507.07543 dotyczy biasu retrievalu, nie kosztu.
  - Zakres Linguistic Nepotism dla rosyjskiego to 5,3–12,2 pkt.
  - Praca Galeshchuk i in. to zwykła publikacja UNLP, nie shared task.
  - Wartości z tabeli BgGPT 1.0 (75,08/60,51) nie dało się ponownie zweryfikować, więc je usunięto.
- **Problemy jakościowe w pracy agentów:**
  - Agent słowiański opierał się głównie na fragmentach z wyników wyszukiwania.
  - Streszczenia generowane przez narzędzie fetch u dwóch agentów zawierały błędy, które wyszły dopiero przy dosłownym tekście. Przykład: jedno twierdziło, że Llama-8B nie występuje w pracy medycznej, inne, że mmPISA nie ma tab. 1.
  - Liczby oznaczone powyżej jako *wtórne* nie zostały odczytane z oryginalnych tabel.
- **Główne ograniczenie:** polskiego brakuje w większości dużych wielojęzycznych benchmarków rozumowania i RAG. Nie ma też badania z 2026 roku porównującego *prompty* po polsku i po angielsku na obecnych modelach frontier.

## Źródła
- OneRuler (COLM 2025): https://arxiv.org/abs/2503.01996 · HTML v3 https://arxiv.org/html/2503.01996v3
- Wywiad z M. Karpińską (Nauka w Polsce): https://scienceinpoland.pl/en/news/news,110407,polish-language-not-superior-ai-prompting-researchers-say.html
- Sprostowanie Spider's Web: https://spidersweb.pl/2025/11/polski-jezyk-ai-microsoft-dementuje-oneruler.html
- Pierwotny artykuł: https://cryps.pl/sztuczna-inteligencja-mowi-po-polsku-nasz-jezyk-zdeklasowal-angielski-w-najnowszym-rankingu/
- MLRBench (EACL 2026): https://aclanthology.org/2026.eacl-long.290/ · MGAL (ICML 2026): https://proceedings.mlr.press/v306/li26ib.html
- BEIR-PL: https://arxiv.org/abs/2305.19840 · PIRB: https://arxiv.org/abs/2402.13350 · PL-MTEB: https://arxiv.org/abs/2405.10138
- Linguistic Nepotism (ICML 2026): https://proceedings.mlr.press/v306/ki26a.html
- Park & Lee 2025: https://arxiv.org/abs/2502.11175 · Cross-Lingual Cost: https://arxiv.org/abs/2507.07543
- XBCP (2026): https://arxiv.org/abs/2606.15345
- Polski RAG 2026: https://aclanthology.org/2026.eacl-srw.55
- mmPISA-bench (2026): https://arxiv.org/abs/2606.07069
- Thellmann i in. EU20: https://arxiv.org/abs/2410.08928
- PolyMath: https://arxiv.org/abs/2504.18428 · Language Matters: https://arxiv.org/abs/2505.17407 · Barua i in.: https://arxiv.org/abs/2508.14828 · Reasoning Lingua Franca: https://arxiv.org/abs/2510.20647 · Yong i in.: https://arxiv.org/abs/2505.05408 · Liu i in. (NAACL 2025): https://aclanthology.org/2025.naacl-long.485
- Do Multilingual LLMs Think in English?: https://arxiv.org/abs/2502.15603 · Anthropic, Tracing the thoughts: https://www.anthropic.com/research/tracing-thoughts-language-model
- Wielojęzyczność Claude: https://platform.claude.com/docs/en/build-with-claude/multilingual-support · OpenAI MMMLU: https://huggingface.co/datasets/openai/MMMLU
- Polish–English medical knowledge transfer (EMNLP 2025 Findings): https://arxiv.org/abs/2412.00559
- LLMzSzŁ: https://arxiv.org/abs/2501.02266 · PLCC: https://arxiv.org/abs/2503.00995 · MoT-PL: https://arxiv.org/abs/2605.13290 · PLLuM: https://arxiv.org/abs/2511.03823 · Bielik 11B v3: https://arxiv.org/abs/2601.11579
- UNLP 2026 (ukraiński): https://aclanthology.org/2026.unlp-1.12/ · MERA Code: https://arxiv.org/abs/2507.12284 · Prompty słowackie: https://aclanthology.org/2025.lowresnlp-1.10 · BgGPT 1.0: https://arxiv.org/abs/2412.10893 · MMLU-BG: https://aclanthology.org/2026.lrec-1.366/ · BenCzechMark: https://aclanthology.org/2025.tacl-1.50 · GaMS3: https://arxiv.org/abs/2603.01691 · Serbian SuperGLUE: https://aclanthology.org/2026.loreslm-1.30
- Tokenizer Tax (Ovcharov 2026): https://arxiv.org/abs/2605.24718 · Tokenizer Bielik v3: https://arxiv.org/abs/2604.10799 · Token Tax (Lundin i in.): https://arxiv.org/abs/2509.05486 · Petrov i in. (NeurIPS 2023): https://arxiv.org/abs/2305.15425
