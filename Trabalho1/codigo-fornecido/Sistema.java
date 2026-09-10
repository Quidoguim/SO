// PUCRS - Escola Politécnica - Sistemas Operacionais
// Prof. Fernando Dotti
// Código fornecido como parte da solução do projeto de Sistemas Operacionais
//
// Estrutura deste código:
//    Todo código está dentro da classe *Sistema*
//    Dentro de Sistema, encontra-se acima a definição de HW:
//           Memory,  Word, 
//           CPU tem Opcodes (codigos de operacoes suportadas na cpu),
//               e Interrupcoes possíveis, define o que executa para cada instrucao
//           VM -  a máquina virtual é uma instanciação de CPU e Memória
//    Depois as definições de SW:
//					InterruptHandling, SysCallHandling  e
//					GerenteMemoria (paginação: aloca/desaloca frames, tradução de endereço)
//    A seguir temos utilitários para usar o sistema
//           carga paginada, início de execução e dump de memória (física e lógica)
//    Por último os programas existentes, que podem ser copiados em memória.
//           Isto representa programas armazenados.
//    Veja o main.  Ele instancia o Sistema (com tamMem/tamPg escolhidos) e chama run(),
//           que aloca, carrega e executa os processos via GerenteMemoria

import java.util.*;

public class Sistema {

	// -------------------------------------------------------------------------------------------------------
	// --------------------- H A R D W A R E - definicoes de HW
	// ----------------------------------------------

	// -------------------------------------------------------------------------------------------------------
	// --------------------- M E M O R I A - definicoes de palavra de memoria,
	// memória ----------------------

	public class Memory {
		public Word[] pos; // pos[i] é a posição i da memória. cada posição é uma palavra.

		public Memory(int size) {
			pos = new Word[size];
			for (int i = 0; i < pos.length; i++) {
				pos[i] = new Word(Opcode.___, -1, -1, -1);
			}
			; // cada posicao da memoria inicializada
		}
	}

	public class Word {    // cada posicao da memoria tem uma instrucao (ou um dado)
		public Opcode opc; //
		public int ra;     // indice do primeiro registrador da operacao (Rs ou Rd cfe opcode na tabela)
		public int rb;     // indice do segundo registrador da operacao (Rc ou Rs cfe operacao)
		public int p;      // parametro para instrucao (k ou A cfe operacao), ou o dado, se opcode = DADO

		public Word(Opcode _opc, int _ra, int _rb, int _p) { // vide definição da VM - colunas vermelhas da tabela
			opc = _opc;
			ra = _ra;
			rb = _rb;
			p  = _p;
		}
	}

	// -------------------------------------------------------------------------------------------------------
	// --------------------- C P U - definicoes da CPU
	// -----------------------------------------------------

	public enum Opcode {
		DATA, ___,              // se memoria nesta posicao tem um dado, usa DATA, se nao usada ee NULO ___
		JMP, JMPI, JMPIG, JMPIL, JMPIE, // desvios
		JMPIM, JMPIGM, JMPILM, JMPIEM,
		JMPIGK, JMPILK, JMPIEK, JMPIGT,
		ADDI, SUBI, ADD, SUB, MULT,    // matematicos
		LDI, LDD, STD, LDX, STX, MOVE, // movimentacao
		SYSCALL, STOP                  // chamada de sistema e parada
	}

	public enum Interrupts {           // possiveis interrupcoes que esta CPU gera
		noInterrupt, intEnderecoInvalido, intInstrucaoInvalida, intOverflow, intTimer;
	}

	public class CPU {
		private int maxInt; // valores maximo e minimo para inteiros nesta cpu
		private int minInt;
		                    // CONTEXTO da CPU ...
		private int pc;     // ... composto de program counter,
		private Word ir;    // instruction register,
		private int[] reg;  // registradores da CPU
		private Interrupts irpt; // durante instrucao, interrupcao pode ser sinalizada
		                    // FIM CONTEXTO DA CPU: tudo que precisa sobre o estado de um processo para
		                    // executa-lo
		                    // nas proximas versoes isto pode modificar

		private Word[] m;   // m é o array de memória "física", CPU tem uma ref a m para acessar

		private int tamPg;          // tamanho de página/frame usado na tradução de endereços
		private int[] tabelaPaginas; // tabela de páginas do processo atualmente no contexto da CPU

		private int delta;          // tamanho da fatia de tempo, em número de instruções (round-robin)
		private int contadorDelta;  // quantas instruções já executou o processo atual nesta fatia

		private InterruptHandling ih;    // significa desvio para rotinas de tratamento de Int - se int ligada, desvia
		private SysCallHandling sysCall; // significa desvio para tratamento de chamadas de sistema

		private boolean cpuStop;    // flag para parar CPU - caso de interrupcao que acaba o processo, ou chamada stop -
									// nesta versao acaba o sistema no fim do prog

		                            // auxilio aa depuração
		private volatile boolean debug; // se true entao mostra cada instrucao em execucao - volatile: lido pela
										// thread de escalonamento, alterado pela thread do shell (traceOn/traceOff)
		private Utilities u;        // para debug (dump)

		public CPU(Memory _mem, boolean _debug, int _tamPg, int _delta) { // ref a MEMORIA passada na criacao da CPU
			maxInt = 32767;            // capacidade de representacao modelada
			minInt = -32767;           // se exceder deve gerar interrupcao de overflow
			m = _mem.pos;              // usa o atributo 'm' para acessar a memoria, só para ficar mais pratico
			tamPg = _tamPg;
			delta = _delta;
			// reg NÃO é alocado aqui: a cada setContext(pcb), reg passa a ser a mesma referência de pcb.reg
			// (o registrador do processo É o contexto guardado no PCB enquanto ele estiver rodando)

			debug = _debug;            // se true, print da instrucao em execucao

		}

		public void setAddressOfHandlers(InterruptHandling _ih, SysCallHandling _sysCall) {
			ih = _ih;                  // aponta para rotinas de tratamento de int
			sysCall = _sysCall;        // aponta para rotinas de tratamento de chamadas de sistema
		}

		public void setUtilities(Utilities _u) {
			u = _u;                     // aponta para rotinas utilitárias - fazer dump da memória na tela
		}

		public void setDebug(boolean _debug) { // liga/desliga trace de instrucoes (comandos traceOn/traceOff)
			debug = _debug;
		}


                                       // tradução de endereço lógico -> físico, via tabela de páginas do processo
		private int traduz(int enderecoLogico) { // todo acesso a memoria tem que ser traduzido e validado -
			                                      // retorna o endereço físico, ou -1 se o endereço é inválido
			if (enderecoLogico < 0) {
				irpt = Interrupts.intEnderecoInvalido;   // se nao for liga interrupcao no meio da exec da instrucao
				return -1;
			}
			int pagina = enderecoLogico / tamPg;
			if (pagina >= tabelaPaginas.length) {        // página fora da tabela do processo -> acesso indevido
				irpt = Interrupts.intEnderecoInvalido;
				return -1;
			}
			int frame = tabelaPaginas[pagina];
			return frame * tamPg + (enderecoLogico % tamPg);
		}

		private boolean testOverflow(int v) {             // toda operacao matematica deve avaliar se ocorre overflow
			if ((v < minInt) || (v > maxInt)) {
				irpt = Interrupts.intOverflow;            // se houver liga interrupcao no meio da exec da instrucao
				return false;
			}
			;
			return true;
		}

		public void setContext(PCB pcb) { // usado para setar o contexto da cpu para rodar (ou retomar) um processo
			pc = pcb.pc;                                   // pc cfe endereco logico, de onde o processo parou
			reg = pcb.reg;                                 // aliasing: reg passa a ser o array de contexto do PCB
			tabelaPaginas = pcb.tabelaPaginas;              // tabela de páginas do processo que assume a CPU
			contadorDelta = 0;                              // nova fatia de tempo começa do zero
			irpt = Interrupts.noInterrupt;                  // reset da interrupcao registrada
		}

		// salva o contexto do processo que está perdendo a CPU (usado ao preemptar por timer).
		// só o pc precisa ser copiado: reg já é a mesma referência de pcb.reg (aliasing em setContext)
		public void salvaContexto(PCB pcb) {
			pcb.pc = pc;
		}

		public void run() {                               // execucao da CPU supoe que o contexto da CPU, vide acima, 
														  // esta devidamente setado
			cpuStop = false;
			while (!cpuStop) {      // ciclo de instrucoes. acaba cfe resultado da exec da instrucao, veja cada caso.

				// --------------------------------------------------------------------------------------------------
				// FASE DE FETCH
				int pcFisico = traduz(pc);
				if (pcFisico != -1) { // pc valido
					ir = m[pcFisico];  // <<<<<<<<<<<< AQUI faz FETCH - busca posicao da memoria apontada por pc, guarda em ir
					             // resto é dump de debug
					if (debug) {
						// synchronized: essas linhas formam UM evento de trace, e não podem se
						// intercalar com um dump/dumpM disparado pelo shell ao mesmo tempo
						synchronized (System.out) {
							System.out.print("                                              regs: ");
							for (int i = 0; i < 10; i++) {
								System.out.print(" r[" + i + "]:" + reg[i]);
							}
							;
							System.out.println();
							System.out.print("                      pc: " + pc + "       exec: ");
							u.dump(ir);
						}
					}

				// --------------------------------------------------------------------------------------------------
				// FASE DE EXECUCAO DA INSTRUCAO CARREGADA NO ir
					switch (ir.opc) {       // conforme o opcode (código de operação) executa

						// Instrucoes de Busca e Armazenamento em Memoria
						case LDI: // Rd ← k        veja a tabela de instrucoes do HW simulado para entender a semantica da instrucao
							reg[ir.ra] = ir.p;
							pc++;
							break;
						case LDD: // Rd <- [A]
							int endLDD = traduz(ir.p);
							if (endLDD != -1) {
								reg[ir.ra] = m[endLDD].p;
								pc++;
							}
							break;
						case LDX: // RD <- [RS] // NOVA
							int endLDX = traduz(reg[ir.rb]);
							if (endLDX != -1) {
								reg[ir.ra] = m[endLDX].p;
								pc++;
							}
							break;
						case STD: // [A] ← Rs
							int endSTD = traduz(ir.p);
							if (endSTD != -1) {
								m[endSTD].opc = Opcode.DATA;
								m[endSTD].p = reg[ir.ra];
								pc++;
                                if (debug)
								    {   synchronized (System.out) {
									    System.out.print("                                  ");
									    u.dump(endSTD,endSTD+1);
									}
									}
								}
							break;
						case STX: // [Rd] ←Rs
							int endSTX = traduz(reg[ir.ra]);
							if (endSTX != -1) {
								m[endSTX].opc = Opcode.DATA;
								m[endSTX].p = reg[ir.rb];
								pc++;
							}
							;
							break;
						case MOVE: // RD <- RS
							reg[ir.ra] = reg[ir.rb];
							pc++;
							break;
						// Instrucoes Aritmeticas
						case ADD: // Rd ← Rd + Rs
							reg[ir.ra] = reg[ir.ra] + reg[ir.rb];
							testOverflow(reg[ir.ra]);
							pc++;
							break;
						case ADDI: // Rd ← Rd + k
							reg[ir.ra] = reg[ir.ra] + ir.p;
							testOverflow(reg[ir.ra]);
							pc++;
							break;
						case SUB: // Rd ← Rd - Rs
							reg[ir.ra] = reg[ir.ra] - reg[ir.rb];
							testOverflow(reg[ir.ra]);
							pc++;
							break;
						case SUBI: // RD <- RD - k // NOVA
							reg[ir.ra] = reg[ir.ra] - ir.p;
							testOverflow(reg[ir.ra]);
							pc++;
							break;
						case MULT: // Rd <- Rd * Rs
							reg[ir.ra] = reg[ir.ra] * reg[ir.rb];
							testOverflow(reg[ir.ra]);
							pc++;
							break;

						// Instrucoes JUMP
						case JMP: // PC <- k
							pc = ir.p;
							break;
						case JMPIM: // PC <- [A]
							int endJMPIM = traduz(ir.p);
							if (endJMPIM != -1) {
								pc = m[endJMPIM].p;
							}
							break;
						case JMPIG: // If Rc > 0 Then PC ← Rs Else PC ← PC +1
							if (reg[ir.rb] > 0) {
								pc = reg[ir.ra];
							} else {
								pc++;
							}
							break;
						case JMPIGK: // If RC > 0 then PC <- k else PC++
							if (reg[ir.rb] > 0) {
								pc = ir.p;
							} else {
								pc++;
							}
							break;
						case JMPILK: // If RC < 0 then PC <- k else PC++
							if (reg[ir.rb] < 0) {
								pc = ir.p;
							} else {
								pc++;
							}
							break;
						case JMPIEK: // If RC = 0 then PC <- k else PC++
							if (reg[ir.rb] == 0) {
								pc = ir.p;
							} else {
								pc++;
							}
							break;
						case JMPIL: // if Rc < 0 then PC <- Rs Else PC <- PC +1
							if (reg[ir.rb] < 0) {
								pc = reg[ir.ra];
							} else {
								pc++;
							}
							break;
						case JMPIE: // If Rc = 0 Then PC <- Rs Else PC <- PC +1
							if (reg[ir.rb] == 0) {
								pc = reg[ir.ra];
							} else {
								pc++;
							}
							break;
						case JMPIGM: // If RC > 0 then PC <- [A] else PC++
							if (reg[ir.rb] > 0) {
								int endJMPIGM = traduz(ir.p);
								if (endJMPIGM != -1) {
									pc = m[endJMPIGM].p;
								}
							} else {
								pc++;
							}
							break;
						case JMPILM: // If RC < 0 then PC <- k else PC++
							if (reg[ir.rb] < 0) {
								int endJMPILM = traduz(ir.p);
								if (endJMPILM != -1) {
									pc = m[endJMPILM].p;
								}
							} else {
								pc++;
							}
							break;
						case JMPIEM: // If RC = 0 then PC <- k else PC++
							if (reg[ir.rb] == 0) {
								int endJMPIEM = traduz(ir.p);
								if (endJMPIEM != -1) {
									pc = m[endJMPIEM].p;
								}
							} else {
								pc++;
							}
							break;
						case JMPIGT: // If RS>RC then PC <- k else PC++
							if (reg[ir.ra] > reg[ir.rb]) {
								pc = ir.p;
							} else {
								pc++;
							}
							break;

						case DATA: // pc está sobre área supostamente de dados
							irpt = Interrupts.intInstrucaoInvalida;
							break;

						// Chamadas de sistema
						case SYSCALL:
							sysCall.handle(); // <<<<< aqui desvia para rotina de chamada de sistema, no momento so
												// temos IO
							pc++;
							break;

						case STOP: // por enquanto, para execucao
							sysCall.stop();
							cpuStop = true;
							break;

						// Inexistente
						default:
							irpt = Interrupts.intInstrucaoInvalida;
							break;
					}
				}
				// --------------------------------------------------------------------------------------------------
				// FATIA DE TEMPO: conta uma instrução completa, sem erro nem STOP, para o round-robin
				if (!cpuStop && irpt == Interrupts.noInterrupt) {
					contadorDelta++;
					if (contadorDelta >= delta) {
						irpt = Interrupts.intTimer;
					}
				}
				// --------------------------------------------------------------------------------------------------
				// VERIFICA INTERRUPÇÃO !!! - TERCEIRA FASE DO CICLO DE INSTRUÇÕES
				if (irpt != Interrupts.noInterrupt) { // existe interrupção
					ih.handle(irpt);                  // desvia para rotina de tratamento - esta rotina é do SO
					cpuStop = true;                   // nesta versao, para a CPU
				}
			} // FIM DO CICLO DE UMA INSTRUÇÃO
		}
	}
	// ------------------ C P U - fim
	// -----------------------------------------------------------------------
	// ------------------------------------------------------------------------------------------------------

	// ------------------- HW - constituido de CPU e MEMORIA
	// -----------------------------------------------
	public class HW {
		public Memory mem;
		public CPU cpu;
		public int tamPg; // tamanho de página/frame usado pelo gerente de memória e pela CPU

		public HW(int tamMem, int tamPg, int delta) {
			mem = new Memory(tamMem);
			this.tamPg = tamPg;
			cpu = new CPU(mem, false, tamPg, delta); // debug desligado por padrão - liga/desliga via traceOn/traceOff
		}
	}
	// -------------------------------------------------------------------------------------------------------

	// --------------------H A R D W A R E - fim
	// -------------------------------------------------------------
	// -------------------------------------------------------------------------------------------------------

	// ///////////////////////////////////////////////////////////////////////////////////////////////////////

	// -------------------------------------------------------------------------------------------------------
	// -------------------------------------------------------------------------------------------------------
	// ------------------- SW - inicio - Sistema Operacional
	// -------------------------------------------------

	// ------------------- I N T E R R U P C O E S - rotinas de tratamento
	// ----------------------------------
	public class InterruptHandling {
		private HW hw; // referencia ao hw se tiver que setar algo
		private GerenteProcessos gp;
		private Escalonador escalonador;

		public InterruptHandling(HW _hw, GerenteProcessos _gp, Escalonador _escalonador) {
			hw = _hw;
			gp = _gp;
			escalonador = _escalonador;
		}

		public void handle(Interrupts irpt) {
			if (irpt == Interrupts.intTimer) {
				// fim de fatia de tempo: processo continua vivo, só perde a CPU e volta para prontos
				PCB interrompido = gp.getRunning();
				hw.cpu.salvaContexto(interrompido);
				System.out.println("                                               TIMER: processo " + interrompido.id
						+ " perde a CPU, volta para fila de prontos");
				gp.preemptaProcessoEmExecucao();
			} else {
				// acesso indevido, instrucao invalida ou overflow: processo é finalizado de vez
				PCB finalizado = gp.getRunning();
				System.out.println("                                               Interrupcao " + irpt
						+ "   processo " + (finalizado != null ? finalizado.id : "?") + " finalizado");
				gp.finalizaProcessoEmExecucao();
			}
			escalonador.escalona(); // libera a CPU para o próximo processo pronto, se houver
		}
	}

	// ------------------- C H A M A D A S D E S I S T E M A - rotinas de tratamento
	// ----------------------
	public class SysCallHandling {
		private HW hw; // referencia ao hw se tiver que setar algo
		private GerenteProcessos gp;
		private Escalonador escalonador;

		public SysCallHandling(HW _hw, GerenteProcessos _gp, Escalonador _escalonador) {
			hw = _hw;
			gp = _gp;
			escalonador = _escalonador;
		}

		public void stop() { // chamada de sistema indicando final de programa
							 // desaloca o processo (memoria + pcb) e libera a CPU para o próximo pronto
			System.out.println("                                               SYSCALL STOP");
			gp.finalizaProcessoEmExecucao();
			escalonador.escalona();
		}

		public void handle() { // chamada de sistema 
			                   // suporta somente IO, com parametros 
							   // reg[8] = in ou out    e reg[9] endereco do inteiro
			System.out.println("SYSCALL pars:  " + hw.cpu.reg[8] + " / " + hw.cpu.reg[9]);

			if  (hw.cpu.reg[8]==1){
				  // leitura ...

			} else if (hw.cpu.reg[8]==2){
				  // escrita - escreve o conteuodo da memoria na posicao dada em reg[9]
				  // reg[9] é um endereço lógico do processo, precisa ser traduzido antes de acessar a memória física
				  int endFisico = hw.cpu.traduz(hw.cpu.reg[9]);
				  if (endFisico != -1) {
					  System.out.println("OUT:   "+ hw.mem.pos[endFisico].p);
				  }
			} else {System.out.println("  PARAMETRO INVALIDO"); }
		}
	}

	// ------------------ U T I L I T A R I O S D O S I S T E M A
	// -----------------------------------------
	// ------------------ load é invocado a partir de requisição do usuário

	// carga na memória
	public class Utilities {
		private HW hw;

		public Utilities(HW _hw) {
			hw = _hw;
		}

		// carga paginada: cada posição lógica i do programa é copiada, sem alterações,
		// para o frame indicado por tabelaPaginas[i / tamPg], no deslocamento i % tamPg
		public void carregaPaginado(Word[] imagem, int[] tabelaPaginas) {
			Word[] m = hw.mem.pos; // m[] é o array de posições memória do hw
			int tamPg = hw.tamPg;
			for (int i = 0; i < imagem.length; i++) {
				int frame = tabelaPaginas[i / tamPg];
				int fisico = frame * tamPg + (i % tamPg);
				m[fisico].opc = imagem[i].opc;
				m[fisico].ra = imagem[i].ra;
				m[fisico].rb = imagem[i].rb;
				m[fisico].p = imagem[i].p;
			}
		}

		// dump da memória. synchronized(System.out): cada palavra deve imprimir como um
		// bloco atômico, sem se intercalar com o trace da CPU rodando em outra thread
		public void dump(Word w) { // funcoes de DUMP nao existem em hardware - colocadas aqui para facilidade
			synchronized (System.out) {
				System.out.print("[ ");
				System.out.print(w.opc);
				System.out.print(", ");
				System.out.print(w.ra);
				System.out.print(", ");
				System.out.print(w.rb);
				System.out.print(", ");
				System.out.print(w.p);
				System.out.println("  ] ");
			}
		}

		public void dump(int ini, int fim) {
			Word[] m = hw.mem.pos; // m[] é o array de posições memória do hw
			synchronized (System.out) { // o intervalo inteiro imprime como um bloco só
				for (int i = ini; i < fim; i++) {
					System.out.print(i);
					System.out.print(":  ");
					dump(m[i]);
				}
			}
		}

		// dump lógico: percorre o espaço de endereçamento do processo (0..tamanho-1)
		// traduzindo cada posição pela tabela de páginas, para conferir que o processo
		// "enxerga" sua memória contígua mesmo espalhada fisicamente em frames
		public void dumpLogico(int[] tabelaPaginas, int tamanho) {
			Word[] m = hw.mem.pos;
			int tamPg = hw.tamPg;
			synchronized (System.out) { // o dump inteiro imprime como um bloco só
			for (int i = 0; i < tamanho; i++) {
				int frame = tabelaPaginas[i / tamPg];
				int fisico = frame * tamPg + (i % tamPg);
				System.out.print(i + " (fisico " + fisico + "):  ");
				dump(m[fisico]);
			}
			}
		}
	}

	// ------------------ G E R E N T E D E M E M O R I A - paginação
	// -----------------------------------------
	public class GerenteMemoria {
		private int tamPg;
		private int numFrames;
		private boolean[] frameOcupado; // controle de quadros livres/ocupados
		private Word[] m; // memória física, usado só para limpar frames ao desalocar

		public GerenteMemoria(Memory mem, int tamPg) {
			this.tamPg = tamPg;
			this.m = mem.pos;
			numFrames = mem.pos.length / tamPg;
			frameOcupado = new boolean[numFrames];
		}

		// aloca os frames necessários para nroPalavras. Retorna a tabelaPaginas
		// (tabelaPaginas[pagina] = frame), ou null se não há memória livre suficiente
		public int[] aloca(int nroPalavras) {
			int nroPaginas = (int) Math.ceil(nroPalavras / (double) tamPg);

			int livres = 0;
			for (int f = 0; f < numFrames; f++) {
				if (!frameOcupado[f]) livres++;
			}
			if (livres < nroPaginas) {
				return null; // não há memória suficiente
			}

			int[] tabelaPaginas = new int[nroPaginas];
			int pagina = 0;
			for (int f = 0; f < numFrames && pagina < nroPaginas; f++) {
				if (!frameOcupado[f]) {
					frameOcupado[f] = true;
					tabelaPaginas[pagina] = f;
					pagina++;
				}
			}
			return tabelaPaginas;
		}

		// libera os frames usados por um processo, limpando seu conteúdo - sem isso, um
		// processo futuro que reaproveitasse o frame poderia ler dados deixados por este
		// (ex.: preenchimento não usado da última página, cujo tamanho não é conferido
		// byte a byte por traduz - só a página como um todo)
		public void desaloca(int[] tabelaPaginas) {
			for (int frame : tabelaPaginas) {
				frameOcupado[frame] = false;
				limpaFrame(frame);
			}
		}

		private void limpaFrame(int frame) {
			int inicio = frame * tamPg;
			for (int i = inicio; i < inicio + tamPg; i++) {
				m[i].opc = Opcode.___;
				m[i].ra = -1;
				m[i].rb = -1;
				m[i].p = -1;
			}
		}
	}

	// ------------------ G E R E N T E D E P R O C E S S O S
	// -----------------------------------------
	public enum EstadoProcesso { PRONTO, RODANDO, TERMINADO }

	public class PCB {
		public int id;
		public int[] tabelaPaginas;
		public int tamanho;          // nro de palavras do programa (tamanho lógico do processo)
		public int pc;               // pc do processo (0 = início; salvo/restaurado a cada troca de contexto)
		public int[] reg;            // contexto dos registradores do processo (10 posições, como a CPU) -
									 // enquanto o processo roda, é o MESMO array referenciado por CPU.reg
		public EstadoProcesso estado;

		public PCB(int id, int[] tabelaPaginas, int tamanho) {
			this.id = id;
			this.tabelaPaginas = tabelaPaginas;
			this.tamanho = tamanho;
			this.pc = 0;
			this.reg = new int[10];
			this.estado = EstadoProcesso.PRONTO;
		}
	}

	// A partir da Fase 1C, tanto a thread do shell (new/rm/ps/dump) quanto a
	// thread de escalonamento mexem nestas estruturas - por isso todo método é
	// synchronized (lock no próprio GerenteProcessos), e coleções retornadas são
	// cópias defensivas (evita ConcurrentModificationException em ps/execAll).
	public class GerenteProcessos {
		private GerenteMemoria gm;
		private Utilities utils;
		private LinkedHashMap<Integer, PCB> todosProcessos = new LinkedHashMap<>();
		private LinkedList<PCB> prontos = new LinkedList<>();
		private PCB running;
		private int proximoId = 1;

		public GerenteProcessos(GerenteMemoria gm, Utilities utils) {
			this.gm = gm;
			this.utils = utils;
		}

		// cria um processo a partir da imagem já resolvida do programa.
		// retorna o id do processo, ou -1 se não há memória suficiente
		public synchronized int criaProcesso(Word[] imagem) {
			int[] tabelaPaginas = gm.aloca(imagem.length);
			if (tabelaPaginas == null) {
				return -1; // sem memória suficiente
			}
			utils.carregaPaginado(imagem, tabelaPaginas);
			PCB pcb = new PCB(proximoId++, tabelaPaginas, imagem.length);
			todosProcessos.put(pcb.id, pcb);
			prontos.add(pcb);
			return pcb.id;
		}

		// tenta remover o processo com o id dado. Checagem de estado e remoção são
		// atômicas (um único método synchronized) para não competir com a thread
		// de escalonamento promovendo esse mesmo processo a RODANDO nesse meio-tempo
		public synchronized String tentaRemover(int id) {
			PCB pcb = todosProcessos.get(id);
			if (pcb == null) {
				return "processo nao encontrado: " + id;
			}
			if (pcb.estado == EstadoProcesso.RODANDO) {
				return "processo " + id + " esta em execucao, tente novamente";
			}
			gm.desaloca(pcb.tabelaPaginas);
			prontos.remove(pcb);
			todosProcessos.remove(id);
			return "processo removido: " + id;
		}

		public synchronized PCB buscaProcesso(int id) {
			return todosProcessos.get(id);
		}

		public synchronized List<PCB> getTodosProcessos() {
			return new ArrayList<>(todosProcessos.values()); // cópia defensiva
		}

		// tira o próximo processo pronto da fila e já o marca como rodando, atomicamente.
		// precisa ser UM único método synchronized (não dois): se o "tirar da fila" e o
		// "marcar como rodando" fossem passos separados, o processo ficaria num estado
		// intermediário (fora de prontos, mas ainda com estado==PRONTO) onde tentaRemover
		// não o reconheceria como em execução e poderia desaloca-lo por baixo do escalonador
		public synchronized PCB escalonaProximo() {
			PCB proximo = prontos.poll();
			if (proximo == null) {
				return null;
			}
			running = proximo;
			proximo.estado = EstadoProcesso.RODANDO;
			return proximo;
		}

		// processo perdeu a CPU por fim de fatia de tempo, mas continua vivo: volta pro fim da fila
		public synchronized void preemptaProcessoEmExecucao() {
			running.estado = EstadoProcesso.PRONTO;
			prontos.add(running);
			running = null;
		}

		// processo em execução terminou (STOP) ou foi finalizado por erro: sai do sistema de vez
		public synchronized void finalizaProcessoEmExecucao() {
			if (running == null) {
				return;
			}
			gm.desaloca(running.tabelaPaginas);
			todosProcessos.remove(running.id);
			running = null;
		}

		public synchronized PCB getRunning() {
			return running;
		}
	}

	// ------------------ E S C A L O N A D O R - round-robin
	// -----------------------------------------
	public class Escalonador {
		private HW hw;
		private GerenteProcessos gp;

		public Escalonador(HW hw, GerenteProcessos gp) {
			this.hw = hw;
			this.gp = gp;
		}

		// escolhe o próximo processo pronto (se houver) e prepara a CPU para executá-lo.
		// só é chamado a partir de uma única thread por vez (a de escalonamento, direta
		// ou indiretamente via ih.handle/sysCall.stop rodando dentro de hw.cpu.run()),
		// então a segurança vem inteiramente do método synchronized do GP que ele chama
		public void escalona() {
			PCB proximo = gp.escalonaProximo(); // atômico: tira de prontos e marca RODANDO numa só chamada
			if (proximo == null) {
				return; // fila de prontos vazia - CPU fica ociosa
			}
			hw.cpu.setContext(proximo);
		}
	}

	public class SO {
		public InterruptHandling ih;
		public SysCallHandling sc;
		public Utilities utils;
		public GerenteMemoria gm;
		public GerenteProcessos gp;
		public Escalonador escalonador;

		public SO(HW hw) {
			utils = new Utilities(hw);
			gm = new GerenteMemoria(hw.mem, hw.tamPg);
			gp = new GerenteProcessos(gm, utils);
			escalonador = new Escalonador(hw, gp); // gm/gp/escalonador antes de ih/sc, que agora dependem deles
			ih = new InterruptHandling(hw, gp, escalonador); // rotinas de tratamento de int
			sc = new SysCallHandling(hw, gp, escalonador); // chamadas de sistema
			hw.cpu.setAddressOfHandlers(ih, sc);
		}
	}

	// ------------------ T H R E A D D E E S C A L O N A M E N T O - fundo
	// -----------------------------------------
	// Roda continuamente desde o início do sistema: sempre que a CPU está livre,
	// tenta escalonar o próximo processo pronto e executa uma fatia. É a thread
	// que, junto com a do Shell, satisfaz o requisito de escalonamento contínuo
	// e independente de comandos (Fase 1C, seção 3.2).
	public class ThreadEscalonador extends Thread {
		private HW hw;
		private SO so;
		private volatile boolean rodando = true;

		public ThreadEscalonador(HW hw, SO so) {
			this.hw = hw;
			this.so = so;
			setDaemon(true); // não impede a JVM de encerrar quando o shell sair
		}

		public void run() {
			while (rodando) {
				try {
					if (so.gp.getRunning() == null) {
						so.escalonador.escalona(); // tenta achar um processo pronto (bootstrap ou CPU ociosa)
					}
					if (so.gp.getRunning() != null) {
						hw.cpu.run(); // roda uma fatia (ou até terminar/erro); ao voltar, o handler já preparou o próximo
					} else {
						try {
							Thread.sleep(20);
						} catch (InterruptedException e) {
							// interrompida por encerra() - volta a checar o flag rodando
						}
					}
				} catch (RuntimeException e) {
					// nunca deixa uma exceção inesperada matar esta thread silenciosamente - isso
					// travaria o escalonamento para sempre, com o shell continuando de pé como se
					// nada tivesse acontecido. Libera o processo que estava rodando (se algum) e
					// segue em frente; hw.cpu.setContext() reseta o estado da CPU no próximo escalona()
					System.out.println("ERRO inesperado no escalonador (" + e + ") - processo em execucao finalizado, escalonamento continua");
					so.gp.finalizaProcessoEmExecucao();
				}
			}
		}

		public void encerra() {
			rodando = false;
			interrupt();
		}
	}

	// ------------------ S H E L L - interface de comandos do usuário
	// -----------------------------------------
	public class Shell {
		private HW hw;
		private SO so;
		private Programs progs;
		private ThreadEscalonador threadEscalonador;

		public Shell(HW hw, SO so, Programs progs) {
			this.hw = hw;
			this.so = so;
			this.progs = progs;
			this.threadEscalonador = new ThreadEscalonador(hw, so);
			this.threadEscalonador.start();
		}

		public void loop() {
			Scanner scanner = new Scanner(System.in);
			System.out.println("Sistema pronto (escalonamento continuo em background). Comandos: new <programa>, rm <id>, ps, dump <id>, dumpM <ini> <fim>, exec <id>, execAll, traceOn, traceOff, exit");
			boolean rodando = true;
			while (rodando) {
				System.out.print("> ");
				if (!scanner.hasNextLine()) {
					break; // entrada acabou (ex.: stdin redirecionado)
				}
				String linha = scanner.nextLine().trim();
				if (linha.isEmpty()) {
					continue;
				}
				String[] partes = linha.split("\\s+");
				String cmd = partes[0];

				switch (cmd) {
					case "new":
						cmdNew(partes);
						break;
					case "rm":
						cmdRm(partes);
						break;
					case "ps":
						cmdPs();
						break;
					case "dump":
						cmdDump(partes);
						break;
					case "dumpM":
						cmdDumpM(partes);
						break;
					case "exec":
						cmdExec(partes);
						break;
					case "execAll":
						cmdExecAll();
						break;
					case "traceOn":
						hw.cpu.setDebug(true);
						System.out.println("trace ligado");
						break;
					case "traceOff":
						hw.cpu.setDebug(false);
						System.out.println("trace desligado");
						break;
					case "exit":
						rodando = false;
						break;
					default:
						System.out.println("comando desconhecido: " + cmd);
				}
			}
			threadEscalonador.encerra();
			scanner.close();
			System.out.println("Encerrando o sistema.");
		}

		private void cmdNew(String[] partes) {
			if (partes.length < 2) {
				System.out.println("uso: new <nomePrograma>");
				return;
			}
			Word[] imagem = progs.retrieveProgram(partes[1]);
			if (imagem == null) {
				System.out.println("programa desconhecido: " + partes[1]);
				return;
			}
			int id = so.gp.criaProcesso(imagem);
			if (id == -1) {
				System.out.println("falha ao criar processo: memoria insuficiente");
			} else {
				System.out.println("processo criado. id=" + id);
			}
		}

		private void cmdRm(String[] partes) {
			Integer id = parseId(partes, 1, "uso: rm <id>");
			if (id == null) {
				return;
			}
			System.out.println(so.gp.tentaRemover(id));
		}

		private void cmdPs() {
			for (PCB pcb : so.gp.getTodosProcessos()) {
				System.out.println("id=" + pcb.id + "  estado=" + pcb.estado + "  tamanho=" + pcb.tamanho
						+ "  paginas=" + Arrays.toString(pcb.tabelaPaginas));
			}
		}

		private void cmdDump(String[] partes) {
			Integer id = parseId(partes, 1, "uso: dump <id>");
			if (id == null) {
				return;
			}
			PCB pcb = so.gp.buscaProcesso(id);
			if (pcb == null) {
				System.out.println("processo nao encontrado: " + id);
				return;
			}
			System.out.println("PCB id=" + pcb.id + "  estado=" + pcb.estado + "  tamanho=" + pcb.tamanho
					+ "  pc=" + pcb.pc + "  reg=" + Arrays.toString(pcb.reg)
					+ "  paginas=" + Arrays.toString(pcb.tabelaPaginas));
			so.utils.dumpLogico(pcb.tabelaPaginas, pcb.tamanho);
		}

		private void cmdDumpM(String[] partes) {
			if (partes.length < 3) {
				System.out.println("uso: dumpM <inicio> <fim>");
				return;
			}
			try {
				int ini = Integer.parseInt(partes[1]);
				int fim = Integer.parseInt(partes[2]);
				int tamMem = hw.mem.pos.length;
				if (ini < 0 || fim > tamMem || ini > fim) {
					System.out.println("intervalo invalido: memoria tem " + tamMem + " posicoes (0.." + (tamMem - 1) + ")");
					return;
				}
				so.utils.dump(ini, fim);
			} catch (NumberFormatException e) {
				System.out.println("uso: dumpM <inicio> <fim> (inteiros)");
			}
		}

		// a partir da Fase 1C a execução é automática e contínua (thread de escalonamento
		// em background) - exec não dispara mais a CPU diretamente (evitaria condição de
		// corrida com essa thread); fica como consulta rápida do estado do processo
		private void cmdExec(String[] partes) {
			Integer id = parseId(partes, 1, "uso: exec <id>");
			if (id == null) {
				return;
			}
			PCB pcb = so.gp.buscaProcesso(id);
			if (pcb == null) {
				System.out.println("processo nao encontrado: " + id);
				return;
			}
			System.out.println("processo " + id + " estado=" + pcb.estado
					+ " (execucao e automatica em background - 'exec' nao dispara nada, so informa)");
		}

		// bloqueia até não sobrar nenhum processo no sistema - a execução em si acontece
		// na thread de escalonamento, execAll so espera o resultado ficar pronto
		private void cmdExecAll() {
			System.out.println("aguardando todos os processos atuais terminarem...");
			while (!so.gp.getTodosProcessos().isEmpty()) {
				try {
					Thread.sleep(20);
				} catch (InterruptedException e) {
					return;
				}
			}
			System.out.println("execAll concluido - nenhum processo restante");
		}

		private Integer parseId(String[] partes, int indice, String usoMsg) {
			if (partes.length <= indice) {
				System.out.println(usoMsg);
				return null;
			}
			try {
				return Integer.parseInt(partes[indice]);
			} catch (NumberFormatException e) {
				System.out.println(usoMsg);
				return null;
			}
		}
	}

	// -------------------------------------------------------------------------------------------------------
	// ------------------- S I S T E M A
	// --------------------------------------------------------------------

	public HW hw;
	public SO so;
	public Programs progs;

	public Sistema(int tamMem, int tamPg, int delta) {
		hw = new HW(tamMem, tamPg, delta); // memoria do HW tem tamMem palavras, paginada em blocos de tamPg,
											// fatia de tempo de delta instrucoes por processo (round-robin)
		so = new SO(hw);
		hw.cpu.setUtilities(so.utils); // permite cpu fazer dump de memoria ao avancar
		progs = new Programs();
	}

	public void run() {
		new Shell(hw, so, progs).loop();
	}
	// ------------------- S I S T E M A - fim
	// --------------------------------------------------------------
	// -------------------------------------------------------------------------------------------------------

	// -------------------------------------------------------------------------------------------------------
	// ------------------- instancia e testa sistema
	public static void main(String args[]) {
		new Sistema(1024, 8, 5).run();
	}

	// -------------------------------------------------------------------------------------------------------
	// -------------------------------------------------------------------------------------------------------
	// -------------------------------------------------------------------------------------------------------
	// --------------- P R O G R A M A S - não fazem parte do sistema
	// esta classe representa programas armazenados (como se estivessem em disco)
	// que podem ser carregados para a memória (load faz isto)

	public class Program {
		public String name;
		public Word[] image;

		public Program(String n, Word[] i) {
			name = n;
			image = i;
		}
	}

	public class Programs {

		public Word[] retrieveProgram(String pname) {
			for (Program p : progs) {
				if (p != null && p.name.equals(pname))
					return p.image;
			}
			return null;
		}

		public Program[] progs = {
				new Program("fatorial",
						new Word[] {
								// este fatorial so aceita valores positivos. nao pode ser zero
								// linha coment
								new Word(Opcode.LDI, 0, -1, 7), // 0 r0 é valor a calcular fatorial
								new Word(Opcode.LDI, 1, -1, 1), // 1 r1 é 1 para multiplicar (por r0)
								new Word(Opcode.LDI, 6, -1, 1), // 2 r6 é 1 o decremento
								new Word(Opcode.LDI, 7, -1, 8), // 3 r7 tem posicao 8 para fim do programa
								new Word(Opcode.JMPIE, 7, 0, 0), // 4 se r0=0 pula para r7(=8)
								new Word(Opcode.MULT, 1, 0, -1), // 5 r1 = r1 * r0 (r1 acumula o produto por cada termo)
								new Word(Opcode.SUB, 0, 6, -1), // 6 r0 = r0 - r6 (r6=1) decrementa r0 para proximo
																// termo
								new Word(Opcode.JMP, -1, -1, 4), // 7 vai p posicao 4
								new Word(Opcode.STD, 1, -1, 10), // 8 coloca valor de r1 na posição 10
								new Word(Opcode.STOP, -1, -1, -1), // 9 stop
								new Word(Opcode.DATA, -1, -1, -1) // 10 ao final o valor está na posição 10 da memória
						}),

				new Program("fatorialV2",
						new Word[] {
								new Word(Opcode.LDI, 0, -1, 5), // numero para colocar na memoria, ou pode ser lido
								new Word(Opcode.STD, 0, -1, 19),
								new Word(Opcode.LDD, 0, -1, 19),
								new Word(Opcode.LDI, 1, -1, -1),
								new Word(Opcode.LDI, 2, -1, 13), // SALVAR POS STOP
								new Word(Opcode.JMPIL, 2, 0, -1), // caso negativo pula pro STD
								new Word(Opcode.LDI, 1, -1, 1),
								new Word(Opcode.LDI, 6, -1, 1),
								new Word(Opcode.LDI, 7, -1, 13),
								new Word(Opcode.JMPIE, 7, 0, 0), // POS 9 pula para STD (Stop-1)
								new Word(Opcode.MULT, 1, 0, -1),
								new Word(Opcode.SUB, 0, 6, -1),
								new Word(Opcode.JMP, -1, -1, 9), // pula para o JMPIE
								new Word(Opcode.STD, 1, -1, 18),
								new Word(Opcode.LDI, 8, -1, 2), // escrita
								new Word(Opcode.LDI, 9, -1, 18), // endereco com valor a escrever
								new Word(Opcode.SYSCALL, -1, -1, -1),
								new Word(Opcode.STOP, -1, -1, -1), // POS 17
								new Word(Opcode.DATA, -1, -1, -1), // POS 18
								new Word(Opcode.DATA, -1, -1, -1) } // POS 19
				),

				new Program("progMinimo",
						new Word[] {
								new Word(Opcode.LDI, 0, -1, 999),
								new Word(Opcode.STD, 0, -1, 8),
								new Word(Opcode.STD, 0, -1, 9),
								new Word(Opcode.STD, 0, -1, 10),
								new Word(Opcode.STD, 0, -1, 11),
								new Word(Opcode.STD, 0, -1, 12),
								new Word(Opcode.STOP, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1), // 7
								new Word(Opcode.DATA, -1, -1, -1), // 8
								new Word(Opcode.DATA, -1, -1, -1), // 9
								new Word(Opcode.DATA, -1, -1, -1), // 10
								new Word(Opcode.DATA, -1, -1, -1), // 11
								new Word(Opcode.DATA, -1, -1, -1), // 12
								new Word(Opcode.DATA, -1, -1, -1) // 13
						}),

				new Program("fibonacci10",
						new Word[] { // mesmo que prog exemplo, so que usa r0 no lugar de r8
								new Word(Opcode.LDI, 1, -1, 0),
								new Word(Opcode.STD, 1, -1, 20),
								new Word(Opcode.LDI, 2, -1, 1),
								new Word(Opcode.STD, 2, -1, 21),
								new Word(Opcode.LDI, 0, -1, 22),
								new Word(Opcode.LDI, 6, -1, 6),
								new Word(Opcode.LDI, 7, -1, 31),
								new Word(Opcode.LDI, 3, -1, 0),
								new Word(Opcode.ADD, 3, 1, -1),
								new Word(Opcode.LDI, 1, -1, 0),
								new Word(Opcode.ADD, 1, 2, -1),
								new Word(Opcode.ADD, 2, 3, -1),
								new Word(Opcode.STX, 0, 2, -1),
								new Word(Opcode.ADDI, 0, -1, 1),
								new Word(Opcode.SUB, 7, 0, -1),
								new Word(Opcode.JMPIG, 6, 7, -1),
								new Word(Opcode.STOP, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1), // POS 20
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1) // ate aqui - serie de fibonacci ficara armazenada
						}),

				new Program("fibonacci10v2",
						new Word[] { // mesmo que prog exemplo, so que usa r0 no lugar de r8
								new Word(Opcode.LDI, 1, -1, 0),
								new Word(Opcode.STD, 1, -1, 20),
								new Word(Opcode.LDI, 2, -1, 1),
								new Word(Opcode.STD, 2, -1, 21),
								new Word(Opcode.LDI, 0, -1, 22),
								new Word(Opcode.LDI, 6, -1, 6),
								new Word(Opcode.LDI, 7, -1, 31),
								new Word(Opcode.MOVE, 3, 1, -1),
								new Word(Opcode.MOVE, 1, 2, -1),
								new Word(Opcode.ADD, 2, 3, -1),
								new Word(Opcode.STX, 0, 2, -1),
								new Word(Opcode.ADDI, 0, -1, 1),
								new Word(Opcode.SUB, 7, 0, -1),
								new Word(Opcode.JMPIG, 6, 7, -1),
								new Word(Opcode.STOP, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1), // POS 20
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1) // ate aqui - serie de fibonacci ficara armazenada
						}),
				new Program("fibonacciREAD",
						new Word[] {
								// mesmo que prog exemplo, so que usa r0 no lugar de r8
								new Word(Opcode.LDI, 8, -1, 1), // leitura
								new Word(Opcode.LDI, 9, -1, 55), // endereco a guardar o tamanho da serie de fib a gerar
																	// - pode ser de 1 a 20
								new Word(Opcode.SYSCALL, -1, -1, -1),
								new Word(Opcode.LDD, 7, -1, 55),
								new Word(Opcode.LDI, 3, -1, 0),
								new Word(Opcode.ADD, 3, 7, -1),
								new Word(Opcode.LDI, 4, -1, 36), // posicao para qual ira pular (stop) *
								new Word(Opcode.LDI, 1, -1, -1), // caso negativo
								new Word(Opcode.STD, 1, -1, 41),
								new Word(Opcode.JMPIL, 4, 7, -1), // pula pra stop caso negativo *
								new Word(Opcode.JMPIE, 4, 7, -1), // pula pra stop caso 0
								new Word(Opcode.ADDI, 7, -1, 41), // fibonacci + posição do stop
								new Word(Opcode.LDI, 1, -1, 0),
								new Word(Opcode.STD, 1, -1, 41), // 25 posicao de memoria onde inicia a serie de
																	// fibonacci gerada
								new Word(Opcode.SUBI, 3, -1, 1), // se 1 pula pro stop
								new Word(Opcode.JMPIE, 4, 3, -1),
								new Word(Opcode.ADDI, 3, -1, 1),
								new Word(Opcode.LDI, 2, -1, 1),
								new Word(Opcode.STD, 2, -1, 42),
								new Word(Opcode.SUBI, 3, -1, 2), // se 2 pula pro stop
								new Word(Opcode.JMPIE, 4, 3, -1),
								new Word(Opcode.LDI, 0, -1, 43),
								new Word(Opcode.LDI, 6, -1, 25), // salva posição de retorno do loop
								new Word(Opcode.LDI, 5, -1, 0), // salva tamanho
								new Word(Opcode.ADD, 5, 7, -1),
								new Word(Opcode.LDI, 7, -1, 0), // zera (inicio do loop)
								new Word(Opcode.ADD, 7, 5, -1), // recarrega tamanho
								new Word(Opcode.LDI, 3, -1, 0),
								new Word(Opcode.ADD, 3, 1, -1),
								new Word(Opcode.LDI, 1, -1, 0),
								new Word(Opcode.ADD, 1, 2, -1),
								new Word(Opcode.ADD, 2, 3, -1),
								new Word(Opcode.STX, 0, 2, -1),
								new Word(Opcode.ADDI, 0, -1, 1),
								new Word(Opcode.SUB, 7, 0, -1),
								new Word(Opcode.JMPIG, 6, 7, -1), // volta para o inicio do loop
								new Word(Opcode.STOP, -1, -1, -1), // POS 36
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1), // POS 41
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1)
						}),
				new Program("PB",
						new Word[] {
								// dado um inteiro em alguma posição de memória,
								// se for negativo armazena -1 na saída; se for positivo responde o fatorial do
								// número na saída
								new Word(Opcode.LDI, 0, -1, 7), // numero para colocar na memoria
								new Word(Opcode.STD, 0, -1, 50),
								new Word(Opcode.LDD, 0, -1, 50),
								new Word(Opcode.LDI, 1, -1, -1),
								new Word(Opcode.LDI, 2, -1, 13), // SALVAR POS STOP
								new Word(Opcode.JMPIL, 2, 0, -1), // caso negativo pula pro STD
								new Word(Opcode.LDI, 1, -1, 1),
								new Word(Opcode.LDI, 6, -1, 1),
								new Word(Opcode.LDI, 7, -1, 13),
								new Word(Opcode.JMPIE, 7, 0, 0), // POS 9 pula pra STD (Stop-1)
								new Word(Opcode.MULT, 1, 0, -1),
								new Word(Opcode.SUB, 0, 6, -1),
								new Word(Opcode.JMP, -1, -1, 9), // pula para o JMPIE
								new Word(Opcode.STD, 1, -1, 15),
								new Word(Opcode.STOP, -1, -1, -1), // POS 14
								new Word(Opcode.DATA, -1, -1, -1) // POS 15
						}),
				new Program("PC",
						new Word[] {
								// Para um N definido (10 por exemplo)
								// o programa ordena um vetor de N números em alguma posição de memória;
								// ordena usando bubble sort
								// loop ate que não swap nada
								// passando pelos N valores
								// faz swap de vizinhos se da esquerda maior que da direita
								new Word(Opcode.LDI, 7, -1, 5), // TAMANHO DO BUBBLE SORT (N)
								new Word(Opcode.LDI, 6, -1, 5), // aux N
								new Word(Opcode.LDI, 5, -1, 46), // LOCAL DA MEMORIA
								new Word(Opcode.LDI, 4, -1, 47), // aux local memoria
								new Word(Opcode.LDI, 0, -1, 4), // colocando valores na memoria
								new Word(Opcode.STD, 0, -1, 46),
								new Word(Opcode.LDI, 0, -1, 3),
								new Word(Opcode.STD, 0, -1, 47),
								new Word(Opcode.LDI, 0, -1, 5),
								new Word(Opcode.STD, 0, -1, 48),
								new Word(Opcode.LDI, 0, -1, 1),
								new Word(Opcode.STD, 0, -1, 49),
								new Word(Opcode.LDI, 0, -1, 2),
								new Word(Opcode.STD, 0, -1, 50), // colocando valores na memoria até aqui - POS 13
								new Word(Opcode.LDI, 3, -1, 25), // Posicao para pulo CHAVE 1
								new Word(Opcode.STD, 3, -1, 99),
								new Word(Opcode.LDI, 3, -1, 22), // Posicao para pulo CHAVE 2
								new Word(Opcode.STD, 3, -1, 98),
								new Word(Opcode.LDI, 3, -1, 38), // Posicao para pulo CHAVE 3
								new Word(Opcode.STD, 3, -1, 97),
								new Word(Opcode.LDI, 3, -1, 25), // Posicao para pulo CHAVE 4 (não usada)
								new Word(Opcode.STD, 3, -1, 96),
								new Word(Opcode.LDI, 6, -1, 0), // r6 = r7 - 1 POS 22
								new Word(Opcode.ADD, 6, 7, -1),
								new Word(Opcode.SUBI, 6, -1, 1), // ate aqui
								new Word(Opcode.JMPIEM, -1, 6, 97), // CHAVE 3 para pular quando r7 for 1 e r6 0 para
																	// interomper o loop de vez do programa
								new Word(Opcode.LDX, 0, 5, -1), // r0 e ra pegando valores das posições da memoria POS
																// 26
								new Word(Opcode.LDX, 1, 4, -1),
								new Word(Opcode.LDI, 2, -1, 0),
								new Word(Opcode.ADD, 2, 0, -1),
								new Word(Opcode.SUB, 2, 1, -1),
								new Word(Opcode.ADDI, 4, -1, 1),
								new Word(Opcode.SUBI, 6, -1, 1),
								new Word(Opcode.JMPILM, -1, 2, 99), // LOOP chave 1 caso neg procura prox
								new Word(Opcode.STX, 5, 1, -1),
								new Word(Opcode.SUBI, 4, -1, 1),
								new Word(Opcode.STX, 4, 0, -1),
								new Word(Opcode.ADDI, 4, -1, 1),
								new Word(Opcode.JMPIGM, -1, 6, 99), // LOOP chave 1 POS 38
								new Word(Opcode.ADDI, 5, -1, 1),
								new Word(Opcode.SUBI, 7, -1, 1),
								new Word(Opcode.LDI, 4, -1, 0), // r4 = r5 + 1 POS 41
								new Word(Opcode.ADD, 4, 5, -1),
								new Word(Opcode.ADDI, 4, -1, 1), // ate aqui
								new Word(Opcode.JMPIGM, -1, 7, 98), // LOOP chave 2
								new Word(Opcode.STOP, -1, -1, -1), // POS 45
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1)
						})
		};
	}
}