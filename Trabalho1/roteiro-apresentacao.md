# Roteiro da apresentação — Trabalho 1

Este documento tem três partes:
1. Como compilar e rodar, no macOS e no Windows.
2. A referência de todos os comandos do shell, com o uso de cada um.
3. A sequência de comandos que cobre os 6 itens pedidos pelo professor, com a saída esperada.

Os números de frame e as posições de memória indicados foram conferidos numa execução real, na configuração padrão: memória de 1024 palavras, páginas de 8 e delta 5.

---

## 1. Compilar e rodar

A partir da raiz do repositório:

**macOS / Linux (Terminal):**
```bash
cd Trabalho1/codigo-fornecido
javac -encoding UTF-8 Sistema.java
java Sistema
```

**Windows (PowerShell ou Prompt de Comando):**
```
cd Trabalho1\codigo-fornecido
javac -encoding UTF-8 Sistema.java
java Sistema
```

- **Requisito:** um JDK instalado, com `javac` e `java` no PATH. Para conferir, use `java -version` e `javac -version`.
- **Por que `-encoding UTF-8`:** o código-fonte original tem setas `←` nos comentários. Em JDKs até a versão 17, o Windows compila com a codificação Windows-1252 e dá `error: unmappable character`. Com a flag, compila em qualquer sistema e em qualquer JDK. Do JDK 18 em diante a flag é opcional, porque UTF-8 já é o padrão.
- **Para sair do sistema:** comando `exit`. Em emergência, use Ctrl+C.
- **Para apagar os `.class` depois:** `rm -f Sistema*.class` no macOS, ou `del Sistema*.class` no Windows.

Os comandos do shell (seção 2) são lidos pelo próprio programa Java, então são iguais nos dois sistemas.

---

## 2. Referência de comandos do shell

| Comando | Uso |
|---|---|
| `new <programa>` | Cria um processo. O Gerente de Memória aloca os frames, o programa é carregado página a página, o PCB é criado e vai para a fila de prontos. Responde `processo criado. id=N`. Programas: `fatorial`, `fatorialV2`, `progMinimo`, `fibonacci10`, `fibonacci10v2`, `fibonacciREAD`, `PB`, `PC`. |
| `ps` | Lista os processos existentes: id, estado (`PRONTO`/`RODANDO`), tamanho em palavras e a tabela de páginas (os frames, em ordem de página). |
| `dump <id>` | Mostra o PCB (estado, pc, registradores, páginas) e a memória do processo como ele a enxerga. Cada linha mostra `endereço lógico (fisico X)`. |
| `dumpM <ini> <fim>` | Mostra a memória física de `ini` até `fim - 1`, independente de processo. |
| `rm <id>` | Remove o processo e libera seus frames. Recusa se o processo estiver `RODANDO`; nesse caso, tente de novo. |
| `exec <id>` | Prioriza um processo `PRONTO`: ele passa para o início da fila e é o próximo a ganhar a CPU. A execução em si é sempre feita pelo escalonador, em background. |
| `execAll` | Retoma o escalonador, se estiver pausado, e espera até não sobrar nenhum processo. |
| `traceOn` / `traceOff` | Liga e desliga o trace: cada instrução executada aparece com `pc` e registradores. |
| `pause` | Pausa o escalonador, a partir do fim da fatia atual. Os processos ficam parados na fila, e dá para listar e inspecionar antes de executar. |
| `resume` | Retoma o escalonador. |
| `delay <ms>` | Espera `ms` milissegundos após cada fatia de tempo, para acompanhar o round-robin a olho. `delay 0` volta à velocidade normal. Pode ser trocado com o sistema rodando: o novo valor vale a partir da espera atual. |
| `exit` | Encerra o sistema. |

Mensagens que aparecem durante a execução:
- `TIMER: processo N perde a CPU, volta para fila de prontos`: fim da fatia de tempo (delta instruções); é a preempção do round-robin.
- `SYSCALL STOP`: o processo terminou e foi desalocado.
- `OUT: 120`: chamada de sistema de escrita (só o `fatorialV2` usa).
- `Interrupcao intEnderecoInvalido`: o processo acessou um endereço fora das suas páginas e foi finalizado.

Comandos inválidos, como argumento faltando, id inexistente ou intervalo fora da memória, só imprimem uma mensagem de uso. O shell não cai.

---

## 3. Sequência da apresentação (itens 1 a 6 do professor)

Digite (ou cole) **uma linha por vez**, dentro do `java Sistema`, e mostre a saída antes de seguir. Não cole o bloco inteiro de uma só vez: as mensagens do escalonador se misturam com a saída dos seus comandos e o tempo das etapas 5 e 6 deixa de fazer sentido.

Duas coisas que podem confundir durante a execução:
- **O prompt `> ` some.** As mensagens `TIMER` chegam de outra thread e empurram o prompt para cima. O shell continua esperando: digite o comando e dê Enter normalmente, mesmo que o texto apareça no meio das mensagens. Não precisa de ESC.
- **Precisa de mais tempo?** Digite `pause`. O escalonador para ao fim da fatia atual, e `resume` continua de onde parou. Use quando o professor fizer uma pergunta no meio do rodízio.

### Itens 1 e 2: carga de diferentes processos e listagem

```
pause
new fatorialV2
new fibonacci10v2
new progMinimo
ps
```

O `pause` vem primeiro para os processos ficarem parados na fila de prontos. Sem ele, cada processo roda e termina em milissegundos, antes de você conseguir digitar o `ps`.

Esperado:
```
id=1  estado=PRONTO  tamanho=20  paginas=[0, 1, 2]
id=2  estado=PRONTO  tamanho=32  paginas=[3, 4, 5, 6]
id=3  estado=PRONTO  tamanho=14  paginas=[7, 8]
```

### Item 3: memória de processo em quadros não contíguos, antes da execução

```
rm 2
new fatorial
new fibonacci10
ps
dump 5
```

O `rm 2` libera os frames 3 a 6 e abre um "buraco" na memória. O `fatorial` (id 4) precisa de 2 frames e fica com os frames 3 e 4. O `fibonacci10` (id 5) precisa de 4 frames: recebe os frames 5 e 6, que sobraram do buraco, e os frames livres seguintes, 9 e 10.

Esperado no `ps`: `id=5 ... paginas=[5, 6, 9, 10]`.

No `dump 5`, aponte o seguinte:
- `pc=0` e os registradores zerados: o processo ainda não executou nenhuma instrução.
- A tradução de endereço: `15 (fisico 55)` e logo depois `16 (fisico 72)`. O endereço lógico é contínuo, mas o físico salta de frame (página 1 → frame 6, página 2 → frame 9).

### Itens 5 e 6: escalonando vários processos, e um processo novo entrando no rodízio

```
delay 2000
resume
ps
new fatorialV2
ps
```

O `delay 2000` faz o escalonador esperar 2 segundos depois de cada fatia, tempo de sobra para digitar entre uma linha `TIMER` e a seguinte. Depois do `resume` aparecem as linhas `TIMER: processo N perde a CPU`, alternando entre os processos (1, 3, 4, 5, 1, …). O processo 3 (`progMinimo`) é o primeiro a terminar, na segunda fatia dele (`SYSCALL STOP`).

O `new fatorialV2` cria o processo 6 com os outros ainda rodando. O segundo `ps` mostra o processo 6 na lista, e em seguida ele aparece no rodízio dos `TIMER`, sem precisar de comando nenhum.

**Janela de tempo.** Com 2 s por fatia, os processos 1, 4 e 5 ficam vivos juntos por cerca de 40 s depois do `resume`. Faça `ps` e `new fatorialV2` nesse intervalo. Se demorar (por exemplo, por causa de uma pergunta do professor), use `pause`, responda e continue com `resume`. Se quiser mais folga ainda, suba para `delay 3000` antes do `resume`.

Esperado no segundo `ps`, conforme o momento em que você digitar o `new`:
- Processo 3 já terminou (uns 12 s depois do `resume`): o processo 6 fica com `paginas=[7, 8, 11]`. Ele reaproveita os frames 7 e 8, que o processo 3 liberou, e depois pega o primeiro frame livre, o 11. É a alocação não contígua em ação, agora com um processo entrando no sistema já em funcionamento.
- Processo 3 ainda na lista: o processo 6 fica com `paginas=[11, 12, 13]`, os primeiros frames livres.

Os dois resultados estão certos. O que muda é só quais frames estavam livres naquele instante.

> Se o `new fatorialV2` for digitado só depois que o processo 1 terminou, o processo 6 pega os frames 0 a 2. O `dumpM 0 20` do item 4 passa a mostrar o 120 do processo 6, não o do processo 1. O valor é o mesmo, mas a explicação muda. Confira no `ps` que o processo 1 ainda está na lista antes de criar o 6.

### Item 4: executar e mostrar resultado coerente em memória

```
delay 0
execAll
dumpM 0 20
dumpM 32 36
dumpM 72 88
```

O `delay 0` vem antes do `execAll`: com 2 s por fatia, esperar os processos restantes levaria mais de um minuto. Com `delay 0`, o escalonador volta à velocidade normal e termina tudo em milissegundos (o novo valor vale depois da espera atual, no máximo 2 s). O `execAll` espera todos os processos terminarem. A memória de um processo que terminou não é apagada; ela só é limpa quando aquele frame for entregue a outro processo. Por isso os resultados continuam lá:

| Processo | Comando | Onde olhar | Resultado esperado |
|---|---|---|---|
| fatorialV2 (id 1, frames 0–2) | `dumpM 0 20` | posição 18 | `[ DATA, -1, -1, 120 ]` (5! = 120; também apareceu `OUT: 120`) |
| fatorial (id 4, frames 3–4) | `dumpM 32 36` | posição 34 (lógica 10: página 1 → frame 4 → 4×8+2) | `[ DATA, -1, -1, 5040 ]` (7! = 5040) |
| fibonacci10 (id 5, frames 5, 6, 9, 10) | `dumpM 72 88` | posições 76–86 (lógicas 20–30) | `0 1 1 2 3 5 8 13 21 34 55` |

Para encerrar, use `exit`.

> Se os comandos forem digitados em outra ordem, os frames mudam. Confira sempre os frames no `ps` antes do `execAll` e ajuste os intervalos do `dumpM`. Endereço físico = frame × 8 + (endereço lógico mod 8).

### Extra (opcional): trace de instruções

```
pause
traceOn
new progMinimo
resume
```

Mostra cada instrução com `pc` e registradores, e cada `STD` mostra a posição gravada (999 nas posições lógicas 8 a 12). Depois use `traceOff`.

---

## Bloco para o bloco de notas

A mesma sequência da seção 3, sem formatação. Copie **uma linha por vez** para dentro do `java Sistema`; o bloco é só uma lista de consulta, não é para colar de uma vez.

```
pause
new fatorialV2
new fibonacci10v2
new progMinimo
ps
rm 2
new fatorial
new fibonacci10
ps
dump 5
delay 2000
resume
ps
new fatorialV2
ps
delay 0
execAll
dumpM 0 20
dumpM 32 36
dumpM 72 88
exit
```

---

## Observações para perguntas do professor

- **PB e PC terminam com `intEnderecoInvalido`.** Eles acessam endereços lógicos além do tamanho da própria imagem: o PB grava em 50 com 16 palavras de imagem, e o PC usa 96 a 99 com 54. Como a alocação é pelo tamanho do programa (código + dados) e os programas não podem ser alterados, é a proteção de memória funcionando. Por isso ficam fora do roteiro.
- **`fibonacciREAD` termina sem gerar a série.** A chamada de sistema de leitura (`reg[8]=1`) ainda é um stub, como no código original.
- **Durante o `delay`, um processo pode aparecer `RODANDO` no `ps`.** Ele já foi escolhido pelo escalonador e está aguardando o fim da espera para executar.
- **Comandos `pause`, `resume` e `delay`.** São comandos extras, criados para a demonstração. O enunciado 03 permite outros comandos ("Você pode criar outros nomes para os comandos"). Sem eles, a execução contínua (enunciado 04 §3.2) termina os programas antes de dar para mostrar qualquer coisa.
