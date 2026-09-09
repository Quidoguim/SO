# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

This is a Sistemas Operacionais (Operating Systems) coursework repository for PUCRS — Escola Politécnica (Prof. Fernando Luís Dotti). `Trabalho1` implements an operating system on top of a simulated virtual machine, built up in progressive phases (memory manager → process manager → scheduler). The professor's original assignment PDFs live in `Trabalho1/enunciado/` and are the authoritative spec — re-read them before implementing a new phase, since the requirements are detailed and easy to misremember (paging semantics, PCB/shell command names, round-robin mechanics).

## Commands

There is no build tool (no Maven/Gradle) and no test framework — this is intentional, matching the plain single-file style the professor provided.

```bash
cd Trabalho1/codigo-fornecido
javac Sistema.java
java Sistema
```

Validation is manual: run it and read the console output (trace of executed instructions when CPU debug is on, memory dumps, `SYSCALL`/interrupt messages). Compiled `.class` files are gitignored — delete them locally after testing (`rm -f Sistema*.class`) rather than committing them.

## Architecture

Everything lives in one file, `Trabalho1/codigo-fornecido/Sistema.java`, as nested classes inside the top-level `Sistema` class. This is a deliberate style choice (matches the professor's own code and keeps submission simple), not something to refactor into multiple files/packages.

**HW layer (simulated hardware — the part that must never change program semantics):**
- `Memory` / `Word`: memory is a flat array of `Word` (`opc`, `ra`, `rb`, `p`). `Opcode.DATA` marks a data word instead of an instruction.
- `CPU`: has `pc`, `ir`, 8 general registers (`reg[0..7]`) plus `reg[8]`/`reg[9]` reserved for I/O syscall parameters, and an `Interrupts` flag. `run()` is the fetch-execute loop; `setContext(pc, tabelaPaginas)` primes it to start/resume a process.
- `Programs`: a fixed set of hand-assembled `Word[]` programs (`fatorialV2`, `fibonacci10v2`, `PC` bubble sort, etc.) used as test payloads. **Never modify these images** — the assignment explicitly requires programs to run unmodified; only the loading/execution machinery around them changes between phases.

**SW layer (the OS being built, inside the `SO` class):**
- `GerenteMemoria` (Fase 1A, done): paging-based memory manager. `aloca(nroPalavras)` returns an `int[] tabelaPaginas` (page → frame) or `null` if there isn't enough free memory — a deliberate deviation from the enunciado's suggested `Boolean aloca(OUT tabelaPaginas)` signature, since a nullable return is more idiomatic in Java and has the same call-site semantics. `desaloca(tabelaPaginas)` frees the frames.
- Address translation lives in `CPU.traduz(enderecoLogico)` (not in `GerenteMemoria`) — it converts a logical address to physical via the *currently running process's* `tabelaPaginas` (set by `setContext`), and raises `intEnderecoInvalido` if the page is outside that table. This replaced the original HW-only `legal(e)` bounds check. Every memory access in `CPU.run()` goes through `traduz()` first — when adding new instructions or touching this loop, remember indirect-jump opcodes (`JMPIM`/`JMPIGM`/`JMPILM`/`JMPIEM`) also read memory via `ir.p` and must be translated too (three of them had no bounds check at all in the professor's original code — this was fixed as part of 1A since untranslated paging access would otherwise corrupt memory or throw `ArrayIndexOutOfBoundsException`).
- `SysCallHandling.handle()`'s write case also translates `reg[9]` via `hw.cpu.traduz(...)` before indexing physical memory, since it holds a logical address — easy to forget when extending syscalls (e.g. adding the read case, still an empty stub).
- `Utilities.carregaPaginado(imagem, tabelaPaginas)` copies a program's words into their assigned frames (page `i/tamPg`, offset `i%tamPg`); `dumpLogico` mirrors that translation for inspecting a process's memory as it "sees" it. The old contiguous `loadProgram`/`loadAndExec` were removed — all loading is paginated now.
- Cross-inner-class private access (e.g. `SysCallHandling` calling the `private` `CPU.traduz`, or reading `hw.cpu.pc`/`hw.cpu.reg`) is normal here: Java allows sibling inner classes of the same enclosing class to reach each other's private members. This codebase relies on that instead of getters — keep following that pattern rather than introducing accessors.
- `Sistema(tamMem, tamPg)` / `HW(tamMem, tamPg)`: both must stay configurable — the assignment requires the system to work across different memory/page sizes, and `main()` demonstrates this by instantiating `Sistema` twice with different configs.

## Phase status

| Phase | Description | Status |
| --- | --- | --- |
| HW | Simulated hardware (professor-provided) | Provided |
| 1A | Memory manager — paging (frame alloc/dealloc, logical→physical translation) | Done (commit `87c08bd`) |
| 1B | Process manager — PCB, ready queue, interactive shell (`new`, `rm`, `ps`, `dump`, `exec`, `traceOn`/`Off`, `exit`) | Not started |
| 1C | Scheduling — round-robin by time slice, context save/restore, `execAll`, continuous multithreaded execution | Not started |

For 1B, the process-creation orchestration currently hardcoded in `Sistema.run()` (alloc → load → run, written as a Fase-1A demo) is meant to move into a `GerenteProcessos.criaProcesso(...)` method that reuses the existing `GerenteMemoria.aloca` and `Utilities.carregaPaginado`.

## Working conventions for this repo

- Implement one phase at a time; each depends on the previous one working correctly. Read the relevant `Trabalho1/enunciado/0N-*.pdf` before starting a phase.
- No automated tests are used — treat a successful `javac` + a `java Sistema` run whose console output matches expected program results (e.g. factorial/Fibonacci values, correct page tables, no unexpected interrupts) as the verification bar.
- Never `git commit` or `git push` without the user explicitly asking in that turn.
