package play.modules.spellchecker;

/**
 * SpellChecker Exception
 * @author Vincent Buzzano <vincent.buzzano@gmail.com>
 *
 */
public class SpellCheckerException extends RuntimeException {
	
	private static final long serialVersionUID = 1L;
	
	String error;
	
	public SpellCheckerException(Throwable t, String error) {
		super(error, t);
		this.error = error;
	}
	
	public String getError() {
		return error;
	}
}
