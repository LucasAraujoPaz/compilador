import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public interface Closure
	extends Valor {

	public Valor aplicar(Expressao input);
}

interface InvocacaoDeClosure
	extends Expressao {
}

class ClosureImpl
	implements Closure {
	
	public final Funcao funcao;
	public final Map<Parametro, Optional<Valor>> escopo;
	
	public ClosureImpl(Funcao funcao) {
		this.funcao = funcao;
		this.escopo = funcao.getUpvalues().stream()
				.collect(Collectors.toMap(Function.identity(), p -> p.valor));
	}
	
	private void inverterEscopo() {
		for (var entry : escopo.entrySet()) {
			var valorOriginalDoParametro = entry.getKey().valor;
			var valorCapturado = entry.getValue();
			entry.getKey().valor = valorCapturado;
			entry.setValue(valorOriginalDoParametro);
		}
	}
	
	@Override
	public Valor aplicar(Expressao input) {
		
		inverterEscopo();
		var retorno = funcao.aplicar(input);
		inverterEscopo();
		
		return retorno;
	}
	
	@Override
	public Closure obterValorNativo() {
		return this;
	}
}

class InvocacaoDeClosureImpl
	implements InvocacaoDeClosure {
	
	public final Expressao closure;
	public final Expressao input;
	
	public InvocacaoDeClosureImpl(Expressao closure, Expressao input) {
		this.closure = closure;
		this.input = input;
	}
	
	public Valor avaliar() {
		return ((Closure) closure.avaliar())
				.aplicar(input);
	}
}

interface Funcao
	extends Expressao {

	public Valor aplicar(Expressao input);
	public Collection<Parametro> getUpvalues();
	public Closure avaliar();
}

class FuncaoLiteral
	implements Funcao {

	public final Parametro parametro;
	public Expressao corpo;
	public final Collection<Parametro> upvalues;
	
	public FuncaoLiteral(Parametro parametro, Expressao corpo, Collection<Parametro> upvalues) {
		this.parametro = parametro;
		this.corpo = corpo;
		this.upvalues = upvalues;
	}
	
	@Override
	public Collection<Parametro> getUpvalues() {
		return upvalues;
	}
	
	@Override
	public Valor aplicar(Expressao input) { 
		var previo = this.parametro.valor;
		this.parametro.valor = Optional.of(input.avaliar());
		var resultado = corpo.avaliar();
		this.parametro.valor = previo;
		return resultado;
	}

	@Override
	public Closure avaliar() {
		return new ClosureImpl(this); 
	}
}

class Parametro implements Expressao {
	protected Optional<Valor> valor = Optional.empty();
	
	@Override
	public Valor avaliar() {
		return valor.get();
	}
}