package l1;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Optional;

public class Parser {

	private final ArrayList<Token> tokens;
	private int indice = 0;
	private Contexto contexto = new Contexto(Optional.empty(), Optional.empty(), BibliotecaPadrao.bibliotecaPadrao());
	
	private Parser(ArrayList<Token> tokens) {
		this.tokens = tokens;
	}

	public static Declaracao checar(final String codigoFonte) {
		var tokens = Token.processar(codigoFonte);
		var parser = new Parser(tokens);
		var programa = parser.programa();
		asseverar(programa.size() > 0, "� necess�rio declarar a fun��o main", Optional.empty());
		var declaracaoMain = programa.get(programa.size() - 1);
		var funcaoMain = declaracaoMain.expressao;
		asseverar(declaracaoMain.token.texto().equals("main") && 
				funcaoMain instanceof Funcao f && 
				f.getParametro().tipo.equals(Tipo.TEXTO), 
				"A �ltima declara��o do arquivo deve ser a fun��o \"main\", com par�metro do tipo String", Optional.empty());
		return declaracaoMain;
	}

	public static String rodar(final String input, final String codigoFonte) {
		var declaracaoMain = checar(codigoFonte);
		var closureMain = (Closure) declaracaoMain.avaliar();
		var resultado = closureMain.aplicar(new TextoLiteral(input));
		var textualizado = resultado.obterValorNativo().toString();
		return textualizado;
	}

	private ArrayList<Declaracao> programa() {
		var declaracoes = new ArrayList<Declaracao>();

		while (atual().isPresent())
			declaracoes.add(declaracao());
		
		return declaracoes;
	}
	
	private Declaracao declaracao() {
		consumir(TipoDeToken.LET, "Declara��es come�am com Let");
		
		var identificador = consumir(TipoDeToken.IDENTIFICADOR, "Declara��o necessita de identificador");
		asseverar( ! identificador.tipo().ehPalavraReservada(), "Nome declarado n�o pode ser palavra reservada", Optional.of(identificador));

		consumir(TipoDeToken.DEFINIDO_COMO, "Declara��o necessita de :=");
		
		final Expressao expressao;
		final Declaracao declaracao;
		if (atual().isPresent() && atual().get().tipo() == TipoDeToken.FUNCTION) {
			consumir();
			var funcao = cabecalhoDeFuncao();
			declaracao = contexto.declarar(identificador, funcao);
			funcao.setCorpo( corpoDeFuncao(funcao) );
			expressao = funcao;
		} else {
			expressao = expressao(Precedencia.NENHUMA);
			declaracao = contexto.declarar(identificador, expressao);
		}
		
		consumir(TipoDeToken.PONTO, "Declara��o encerra com .");
		
		return declaracao;
	}

	private Expressao expressao(Precedencia precedencia) {
		
		asseverar(atual().map(t -> t.tipo().prefix.isPresent()).orElse(false), "Express�o esperada", atual());
		Token tokenEsquerdo = consumir();
		
		Expressao esquerda = tokenEsquerdo.tipo().prefix.get().apply(this);
		
		while (precedencia.ordinal() < precedencia().ordinal()) {
			
			asseverar(atual().map(t -> t.tipo().infix.isPresent()).orElse(false), "Operador esperado", atual());
			var operador = consumir();
			
			esquerda = operador.tipo().infix.get().apply(this, esquerda);
		}
		
		return esquerda;
	}
	
	Expressao unidade() {
		var token = anterior().get();
		return switch (token.tipo()) {
			case NUMERO -> new NumeroLiteral(Double.valueOf(token.texto()));
			case TEXTO -> new TextoLiteral(token.texto());
			case TRUE -> BooleanoLiteral.VERDADEIRO;
			case FALSE -> BooleanoLiteral.FALSO;
			default -> throw new IllegalArgumentException();
		};
	}
	
	Expressao referencia() {
		return contexto.obter(anterior().get());
	}
	
	Expressao grupo() {
		var agrupado = expressao(Precedencia.NENHUMA);
		consumir(TipoDeToken.PARENTESE_DIREITO, "Par�ntese direito esperado");
		
		return agrupado;
	}
	
	Expressao lista() {
		final var lista = new LinkedList<Expressao>();
		while (atual().map(token -> token.tipo() != TipoDeToken.COLCHETE_DIREITO).orElse(false)) {
			var expressao = expressao(Precedencia.NENHUMA);
			lista.add(expressao);
			if (atual().map(token -> token.tipo() == TipoDeToken.VIRGULA).orElse(false))
				consumir();
		}
		consumir(TipoDeToken.COLCHETE_DIREITO, "Colchete direito esperado para fechar lista");
		return new ListaLiteral(lista);
	}
	
	Expressao operadorUnario() {
		var token = anterior().get();
		Precedencia precedencia = switch (token.tipo()) {
			case MENOS -> Precedencia.EXPONENCIACAO; 
			case NOT -> Precedencia.EXPONENCIACAO;
			default -> throw new IllegalArgumentException();
		};
		return new OperadorUnario(token, expressao(precedencia));
	}
	
	Expressao operadorBinario(Expressao esquerda) {
		var token = anterior().get();
		Precedencia precedencia = switch (token.tipo()) {
			
			case EXPONENCIACAO -> Precedencia.MULTIPLICACAO;
			
			case MULTIPLICADO -> Precedencia.MULTIPLICACAO;
			case DIVIDIDO -> Precedencia.MULTIPLICACAO;
			case MODULO -> Precedencia.MULTIPLICACAO;
			case MAIS -> Precedencia.SOMA;
			case MENOS -> Precedencia.SOMA;
			case MAIOR -> Precedencia.COMPARACAO;
			case MENOR -> Precedencia.COMPARACAO;
			case MAIOR_OU_IGUAL -> Precedencia.COMPARACAO; 
			case MENOR_OU_IGUAL -> Precedencia.COMPARACAO;
			case IGUAL -> Precedencia.IGUALDADE;
			case DIFERENTE -> Precedencia.IGUALDADE;
			case AND -> Precedencia.E;
			case OR -> Precedencia.OU;
			default -> throw new IllegalArgumentException();
		};
		return new OperadorBinario(esquerda, token, expressao(precedencia));
	}

	Expressao se() {
		var condicoes = new ArrayList<Expressao>();
		var corpos = new ArrayList<Expressao>();
		
		final Runnable r = () -> {
			var condicao = expressao(Precedencia.NENHUMA);
			asseverar(condicao.obterTipo().equals(Tipo.BOOLEANO), "Apenas booleanos podem ser condi��es", anterior());
			condicoes.add(condicao);
			consumir(TipoDeToken.THEN, "Est� faltando o Then");
			corpos.add(expressao(Precedencia.NENHUMA));
			consumir(TipoDeToken.ELSE, "Est� faltando o Else");
		};
		
		r.run();
		
		while (atual().isPresent() && atual().get().tipo() == TipoDeToken.IF) {
			consumir();
			r.run();
		}

		corpos.add(expressao(Precedencia.NENHUMA));
		
		consumir(TipoDeToken.END, "End esperado ap�s express�o condicional");
		
		return new ExpressaoSeSenao(condicoes, corpos);
	}

	private FuncaoLiteral cabecalhoDeFuncao() {
		consumir(TipoDeToken.PARENTESE_ESQUERDO, "Par�ntese esquerdo necess�rio depois de Function");
		var tipoDoParametro = tipo();
		var parametro = consumir(TipoDeToken.IDENTIFICADOR, "Nome do par�metro esperado");
		consumir(TipoDeToken.PARENTESE_DIREITO, "Par�ntese direito necess�rio depois do par�metro");
		consumir(TipoDeToken.SETA_FINA, "Use seta para indicar o retorno da fun��o");
		var tipoDoRetorno = tipo();
		consumir(TipoDeToken.DOIS_PONTOS, "Use dois pontos \":\" antes de come�ar o corpo da fun��o");
		
		return new FuncaoLiteral(tipoDoRetorno, new Parametro(tipoDoParametro, parametro.texto()), null, new ArrayList<>());
	}
	
	private Expressao corpoDeFuncao(Funcao funcao) {
		this.contexto = new Contexto(Optional.ofNullable(this.contexto), Optional.ofNullable(funcao), new HashMap<>());
		var corpo = expressao(Precedencia.NENHUMA);
		asseverar(corpo.obterTipo().ehSubtipoDe(funcao.obterTipo().tipoDoRetorno),
				"Corpo da fun��o incompat�vel com seu tipo de retorno", anterior());
		this.contexto = this.contexto.pai.get();
		consumir(TipoDeToken.END, "End esperado como t�rmino da fun��o");
		return corpo;
	}
	
	Expressao funcao() {
		var funcao = cabecalhoDeFuncao();
		funcao.setCorpo( corpoDeFuncao(funcao) );
		return funcao; 
	}

	private Tipo tipo() { 
		asseverar(atual().isPresent(), "Tipo esperado", atual());
		final var token = consumir();
		
		Tipo retorno = switch (token.texto()) {
			case "Number" -> Tipo.NUMERO;
			case "Boolean" -> Tipo.BOOLEANO;
			case "String" -> Tipo.TEXTO;
			case "[" -> { 
				var tipoDoArray = new Tipo.Lista(tipo()); 
				consumir(TipoDeToken.COLCHETE_DIREITO, "Colchete direito esperado"); 
				yield tipoDoArray; 
			}
			case "(" -> {
				var tipoDoParametro = tipo();
				consumir(TipoDeToken.PARENTESE_DIREITO, "Par�ntese direito esperado");
				consumir(TipoDeToken.SETA_FINA, "Seta esperada antes do tipo de retorno da fun��o");
				var tipoDoRetorno = tipo();
				var tipoDaFuncao = new Tipo.Funcao(tipoDoParametro, tipoDoRetorno); 
				yield tipoDaFuncao; 
			}
			case "Any" -> Tipo.QUALQUER;
			default -> { asseverar(false, "Tipo inv�lido", Optional.ofNullable(token)); yield Tipo.QUALQUER; }
		};
		
		return retorno;
	}

	Expressao invocacao(Expressao esquerda) {
		var argumento = expressao(Precedencia.NENHUMA);
		asseverar(esquerda.obterTipo() instanceof Tipo.Funcao, "Express�o n�o invoc�vel", anterior());
		var tipoDaFuncao = (Tipo.Funcao) esquerda.obterTipo(); 
		asseverar(argumento.obterTipo().ehSubtipoDe(tipoDaFuncao.tipoDoParametro), 
				"Tipo da fun��o incompat�vel com o par�metro passado", anterior());
		consumir(TipoDeToken.PARENTESE_DIREITO, "Use par�ntese direito ap�s argumento da invoca��o");
		return new InvocacaoImpl(esquerda, argumento);
	}
	
	private Optional<Token> atual() {
		if (indice >= tokens.size())
			return Optional.empty();
		
		return Optional.ofNullable(tokens.get(indice));
	}
	
	private Optional<Token> anterior() {
		if (indice == 0)
			return Optional.empty();
		
		return Optional.ofNullable(tokens.get(indice - 1));
	}
	
	private Token consumir() {
		var token = atual();
		asseverar(token.isPresent(), "", token);
		indice++;
		return token.get();
	}
	
	private Token consumir(TipoDeToken tipo, String erro) {
		var token = atual();
		asseverar(token.isPresent() && token.get().tipo() == tipo, erro, token);
		return consumir();
	}
	
	private Precedencia precedencia() {
		return atual().map(token -> token.tipo().precedenciaInfix).orElse(Precedencia.NENHUMA);
	}
	
	static void asseverar(boolean condicao, String mensagem, Optional<Token> token) {
		
		if (condicao)
			return;
		
		var complemento = token.isPresent() ? "Linha " + token.get().linha() + ": " : "Fim do arquivo: ";
		
		Testes.asseverar(condicao, complemento + mensagem);
	}
}