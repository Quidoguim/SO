# SO — Sistemas Operacionais

Repositório dedicado à disciplina de **Sistemas Operacionais** (PUCRS - Escola Politécnica, Prof. Fernando Luís Dotti). Contém os enunciados fornecidos pelo professor, o código-base da máquina virtual simulada e as implementações desenvolvidas ao longo do curso.

## Estrutura

```
Trabalho1/
├── enunciado/              # material fornecido pelo professor
│   ├── 01-definicao-hardware-simulado.pdf
│   ├── 02-gerente-memoria-paginacao.pdf
│   ├── 03-gerente-processos.pdf
│   ├── 04-escalonamento.pdf
│   └── 05-esquema-geral-referencia.pdf
└── codigo-fornecido/
    └── Sistema.java        # hardware simulado (CPU, memória, ciclo de instruções) fornecido como base
```

## Trabalho 1 — Sistema Operacional sobre máquina virtual simulada

O trabalho é dividido em fases progressivas, construídas sobre o hardware simulado (`Sistema.java`): uma CPU com PC, IR e 8 registradores, executando um conjunto de ~26 instruções sobre uma memória de 1024 posições.

| Fase | Descrição | Status |
| --- | --- | --- |
| HW | Hardware simulado (fornecido pelo professor) | ✅ Fornecido |
| 1A | Gerente de Memória — paginação (alocação/desalocação de frames, tradução de endereço lógico→físico) | ✅ Implementado |
| 1B | Gerente de Processos — PCB, fila de prontos, shell interativo (`new`, `rm`, `ps`, `dump`, `exec`, `traceOn/Off`, `exit`) | ✅ Implementado |
| 1C | Escalonamento — round-robin por fatia de tempo, salvamento/restauração de contexto, `execAll`, execução contínua multithreaded | ✅ Implementado |

## Licença

Este repositório está sob a licença MIT — veja [LICENSE](LICENSE).
