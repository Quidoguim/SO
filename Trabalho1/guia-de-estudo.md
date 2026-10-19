# Trabalho 1 de SO — Guia de Estudo

Guilherme Guimarães Santos

Foco: o que cada enunciado pede, o que foi feito e por quê. Cada seção termina com perguntas prováveis do professor.

## Mapa rápido

| Fase | Enunciado | Peça | Classe(s) |
| --- | --- | --- | --- |
| HW | 01 | CPU, memória, interrupções (do professor) | `CPU`, `Memory`, `Word` |
| 1A | 02 | Gerente de Memória (paginação) | `GerenteMemoria`, `CPU.traduz` |
| 1B | 03 | Gerente de Processos + shell | `GerenteProcessos`, `PCB`, `Shell` |
| 1C | 04 | Escalonador round-robin + execução contínua | `Escalonador`, `InterruptHandling`, `ThreadEscalonador` |

O enunciado 05 só traz diagramas de referência. A versão com E/S (Console, fila de bloqueados) não foi implementada: o enunciado 04 diz que E/S vem "mais adiante".

**Configuração:** `new Sistema(1024, 8, 5)` no `main()` = memória, tamanho de página, delta. O enunciado 02 diz que o professor vai testar outros valores: troque ali e recompile. Testado com `512, 16, 3`.

---

## HW (enunciado 01)

- **Pede:** CPU com `pc`, `ir`, 8 registradores; memória de palavras; ciclo busca → executa → atualiza `pc`.
- **Fizemos:** igual, sem mudar nada dos programas. O ciclo ganhou uma terceira fase: verificar interrupção ao fim de cada instrução.
- **Detalhe:** a CPU tem **10** registradores. `reg[8]` e `reg[9]` são os parâmetros da `SYSCALL` (`reg[8]=2` escreve, `reg[9]` = endereço). Já vinha assim no código do professor.

**Perguntas prováveis**
- *O enunciado diz 8 registradores, por que tem 10?* → Os dois extras levam os parâmetros da chamada de sistema.
- *Quando a CPU olha as interrupções?* → No fim de cada instrução. Por isso um processo nunca é interrompido "no meio" de uma instrução.

---

## 1A — Paginação (enunciado 02)

**Ideia:** a memória é dividida em frames do tamanho de uma página. O processo pensa que está contínuo a partir do 0 (endereço lógico); a tabela de páginas diz em que frame está cada página.

```
físico = tabelaPaginas[lógico / tamPg] * tamPg + (lógico % tamPg)
```

Exemplo: páginas `[5, 6, 9, 10]`, lógico 16 → página 2 → frame 9 → 9×8 + 0 = 72.

| Enunciado pede | Fizemos | Por quê |
| --- | --- | --- |
| `Boolean aloca(IN nroPalavras, OUT tabelaPaginas)` | `int[] aloca(nroPalavras)`, `null` se não cabe | Java não tem parâmetro de saída. Mesmo efeito. |
| Alocar código e dados | Aloca `ceil(tamanho / tamPg)` frames, os primeiros livres | Simples; os frames não precisam ser contíguos |
| `desaloca` libera os frames | Só marca livre, **não apaga** | O enunciado 04 §3.1 pede ver o resultado em memória depois que o processo termina |
| (não pede) | **Zera** o frame ao entregá-lo | Um processo novo nunca vê lixo de outro (ver proteção abaixo) |
| Traduzir todo acesso | `CPU.traduz` em toda leitura/escrita, inclusive saltos indiretos e a `SYSCALL` | Se um acesso escapar da tradução, o processo lê/escreve em memória física errada |

**Proteção de memória:** `traduz` gera `intEnderecoInvalido` se a página não está na tabela do processo (ou endereço negativo). A checagem é **por página, não por palavra**: um programa de 30 palavras ocupa 4 páginas (32 palavras) e consegue acessar 30 e 31 sem erro. Mas essas palavras são frames dele, zerados na alocação, então nunca têm dado de outro processo.

**Perguntas prováveis**
- *Por que zerar na alocação e não na liberação?* → Zerar na liberação apagaria o resultado do processo que terminou, e o 04 §3.1 pede para vê-lo. Zerar na alocação protege do mesmo jeito.
- *Por que PB e PC dão `intEnderecoInvalido`?* → Acessam endereços além das páginas alocadas (PB grava em 50 com 2 páginas; PC usa 96–99 com 7 páginas). Os programas não podem ser alterados, então é a proteção funcionando.
- *Com páginas de 16 e 1024 palavras?* → 64 frames. Um programa de 30 palavras usa 2 páginas.
- *Páginas `[2, 7]`, página 8, lógico 10?* → página 1 → frame 7 → 7×8 + 2 = 58.

---

## 1B — Processos e shell (enunciado 03)

**PCB** guarda: `id`, estado, tamanho, tabela de páginas, `pc` e registradores. Estados: `PRONTO` e `RODANDO` (quando termina, o processo some).
**GP** guarda: todos os processos, a fila de prontos e o ponteiro `running`.

| Enunciado pede | Fizemos | Por quê |
| --- | --- | --- |
| `boolean criaProcesso(programa)` | Devolve o `id` (ou `-1`) | O `new` precisa mostrar o id. Achar o programa pelo nome fica no Shell, para "programa não existe" e "sem memória" darem mensagens diferentes |
| `rm <id>`, "tenha ele executado ou não" | Remove, mas **recusa se estiver `RODANDO`** | Liberar os frames de quem está na CPU faria ele escrever em memória que já pode ser de outro processo |
| `exec <id>` executa o processo | Só **coloca o processo no início da fila** | Desde o 1C a CPU roda sozinha em outra thread; se o Shell também mexesse na CPU, as duas brigariam por ela |
| Comandos com os nomes dados, "pode criar outros" | Extras: `execAll`, `pause`, `resume`, `delay` | `pause`/`delay` existem para a demonstração: sem eles tudo termina em milissegundos |

**Perguntas prováveis**
- *O que é o PCB e por que ele existe?* → É a ficha do processo. Com vários processos na memória, o SO precisa saber onde cada um está e onde parou.
- *Por que o `exec` não executa?* → A execução é contínua (04 §3.2). Quem manda na CPU é só a thread do escalonador; o `exec` só dá prioridade.

---

## 1C — Escalonamento round-robin (enunciado 04)

**Ciclo:** processo roda `delta` instruções → timer → salva contexto → volta ao fim da fila → próximo da fila ganha a CPU. `STOP` ou erro → libera memória e PCB → próximo da fila.

| Enunciado pede | Fizemos | Por quê |
| --- | --- | --- |
| Relógio: thread que dorme **ou** contador de instruções | Contador (`contadorDelta`); ao chegar em `delta`, liga `intTimer` | O enunciado sugere: mais simples e previsível. Reaproveita o mesmo ponto onde a CPU já tratava interrupções |
| Salvar contexto no PCB | `setContext` faz `reg = pcb.reg` (mesmo array); na preempção só copia o `pc` | Enquanto o processo roda, os registradores da CPU já **são** os do PCB. Não precisa copiar nada além do `pc` |
| `STOP`: liberar memória e PCB, escalonar outro (§2.2) | `finalizaProcessoEmExecucao()` + `escalona()` | Literal do enunciado |
| Erro (overflow, endereço inválido, instrução inválida) | Mesmo caminho do `STOP` | Diagrama do 05: "finalizar processo, chamar escalonador" |
| `execAll` roda todos até acabar (§3.1) | Retoma o escalonador e espera não sobrar processo | Com execução contínua, quem executa já é a thread; `execAll` só espera |

**Perguntas prováveis**
- *O que é o contexto e por que salvar o `pc`?* → `pc` + registradores. Sem o `pc`, o processo voltaria do início em vez de onde parou.
- *Por que não copiar os registradores?* → Eles já estão no PCB: a CPU usa o próprio array do PCB.
- *E com `delta` = 1? E 1000?* → 1: troca a cada instrução, muito chaveamento. 1000: quase sem rodízio, um processo pequeno termina antes de perder a CPU.
- *O que acontece com o processo no fim da fatia? E no `STOP`?* → Fatia: volta para o fim da fila, vivo. `STOP`: sai do sistema.

---

## 1C — Execução contínua e threads (enunciado 04 §3.2)

**Pede:** escalonar sem depender de comando; no mínimo duas threads (usuário + escalonamento).

| Thread | Faz |
| --- | --- |
| Shell (principal) | Lê e executa comandos |
| `ThreadEscalonador` | Laço: se ninguém roda, escalona; se tem processo, roda uma fatia; senão dorme 20 ms |

**Diferença do diagrama do 05:** lá são 3 threads (Shell, Escalonador, CPU) acordadas por semáforos. Aqui a `ThreadEscalonador` faz o papel de escalonador e de CPU, e verifica a fila a cada 20 ms (polling). Atende ao "no mínimo duas threads" com menos peças para errar. O custo: até 20 ms de atraso para pegar um processo novo.

**Cuidados porque duas threads mexem nas mesmas filas**

| Cuidado | Por quê |
| --- | --- |
| Todo método do `GerenteProcessos` é `synchronized` | Shell (`new`, `rm`, `ps`) e escalonador alteram as mesmas filas |
| `escalonaProximo()` tira da fila **e** marca `RODANDO` num único método | Se fossem dois passos, um `rm` no meio veria o processo ainda `PRONTO` e liberaria os frames que a CPU ia usar |
| `escalona()` não tem lock próprio | Só a thread do escalonador o chama; a segurança vem do GP |
| `debug`, `pausado`, `delayMs`, `rodando` são `volatile` | São escritos pelo Shell e lidos pela outra thread; `volatile` garante que a mudança seja vista |
| Impressões em `synchronized (System.out)` | Sem isso, trace e `dump` se misturam linha a linha |
| `try/catch` no laço da thread | Uma exceção mataria a thread em silêncio e o escalonamento pararia para sempre |

**Perguntas prováveis**
- *Por que duas threads?* → Exigência do §3.2: o Shell fica bloqueado esperando digitação, então outra thread precisa rodar os processos enquanto isso.
- *O que elas compartilham?* → As estruturas do GP (todos os processos, fila de prontos, `running`) e a memória.
- *Por que polling e não semáforo?* → Mais simples e cumpre o requisito; o preço é acordar a cada 20 ms.
- *O `dump` de um processo rodando é exato?* → Não necessariamente: lê sem travar, pode mostrar `pc`/registradores de um instante intermediário. É só leitura de depuração, nada se corrompe.
- *`fibonacciREAD` não funciona?* → A leitura da `SYSCALL` (`reg[8]=1`) continua vazia, como no código original.
