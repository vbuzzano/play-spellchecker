package play.modules.spellchecker;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.concurrent.Future;

import play.Logger;
import play.i18n.Lang;
import play.jobs.Job;

/**
 * 
 * @author Vincent Buzzano <vincent.buzzano@gmail.com>
 *
 */
public final class SpellChecker {

	/**
	 * Language to use fr, fr_CH, en, et...
	 */
	private String lang;
	
	/** 
	 * Input Stream 
	 */
	private OutputStreamWriter writer = null;

	/** 
	 * Error Stream 
	 */
	private BufferedReader errorReader = null;

	/** 
	 * Output Stream 
	 */
	private BufferedReader reader = null;

	/** 
	 * Aspell process. 
	 */
	private Process aspell = null;

	/**
	 * Charset Name to use to read stream
	 */
	private String charsetName;
	
	/**
	 * does the process started
	 */
	private boolean started;
	
	private String textToCheck;

	private String error;

	private Throwable exception;
	
	/**
	 * check a text
	 * 
	 * @param pTerm The word to correct.
	 * @return An array of possible corrections. 
	 */
	static public SpellChecker with(String text) {
		SpellChecker a = new SpellChecker();
		a.setLang(Lang.get());
		a.setText(text);
		
		return a;
	}

	/**
	 * check a text
	 * 
	 * @param pTerm The word to correct.
	 * @return An array of possible corrections. 
	 */
	static public SpellChecker with(String text, String lang) {
		SpellChecker a = with(text);
		a.setLang(lang);
		return a;
	}
	
	/**
	 * check a text
	 * 
	 * @param term The word to correct.
	 * @return An array of possible corrections. 
	 */
	static public SpellChecker with(String text, String lang, String charsetName) {
		SpellChecker a = with(text, lang);
		a.setCharsetName(charsetName);
		return a;
	}

	/**
	 * Constructor
	 */
	private SpellChecker() {
		this.lang = null;
		this.charsetName = "ISO-8859-1";
	}
	
	/**
	 * Constructor
	 * @param lang
	 */
	private SpellChecker(String lang) {
		this();
		this.lang = lang;
		this.started = false;
	}

	/**
	 * Constructor
	 * @param lang
	 * @param charsetName
	 */
	private SpellChecker(String lang, String charsetName) {
		this(lang);
		this.charsetName = charsetName;
	}
	
	/**
	 * Start process and communication with aspell
	 * @throws IOException
	 */
	protected boolean start() {
		if (started) {
			Logger.warn("SpellChecker is already started");
			return true;
		}
		
		// create environnement array
		String[] env = new String[0];

		// create aspell command
		String[] command = new String[3];
		command[0] = "aspell";
		command[1] = "--lang=" + lang;
		command[2] = "-a";

		// create aspell process
		try {
			aspell = Runtime.getRuntime().exec(command, env);

			reader = new BufferedReader(
					new InputStreamReader(
							aspell.getInputStream(), 
							charsetName
					));
			
			writer = new OutputStreamWriter(
						new BufferedOutputStream(aspell.getOutputStream()),
						charsetName
					);
			
			errorReader = new BufferedReader(
					new InputStreamReader(
							aspell.getErrorStream(), 
							charsetName
					));
		
			String version = reader.readLine();
			if (version == null || !version.startsWith("@")
					|| !version.contains("Aspell")) {
				stop();
				setError(null, "There is a problem with Aspell signature");
			} else		
				started = true;

		} catch (Exception e) {
			setError(e, e.getMessage());
			started = false;
			stop();
		}
		
		return started;
	}
	

	/**
	 * Stop communication and process with aspell.
	 */
	protected void stop() {
		if (!started) {
			Logger.warn("SpellChecker is not started");
			return;
		}

		try {
			if (reader != null) {
				reader.close();
				reader = null;
			}
		} catch (Exception e) {
			Logger.error(e, e.getMessage());
		};

		try {
			if (writer != null) {
				writer.close();
				writer = null;
			}
		} catch (Exception e) {
			Logger.error(e, e.getMessage());
		};
			
		try {
			if (errorReader != null) {
				errorReader.close();
				errorReader = null;
			}
		} catch (Exception e) {
			Logger.error(e, e.getMessage());
		};
			
		try {
			if (aspell != null) {
				aspell.destroy();
				aspell = null;
			}
		} catch (Exception e) {
			Logger.error(e, e.getMessage());
		};
		started = false;		
	}
	
	/**
	 * Restart aspell process
	 */
	protected void restart() {
		if (started) stop();
		start();
	}
	
	/**
	 * Get dictionnary language
	 * @return
	 */
	public String getLang() {
		return lang;
	}
	
	/**
	 * Set dictionnary language
	 * @param lang
	 */
	public void setLang(String lang) {
		this.lang = lang;
		if (started) {
			restart();
		}
	}
	
	public String getCharsetName() {
		return charsetName;
	}

	public void setCharsetName(String charsetName) {
		this.charsetName = charsetName;
		if (started) {
			restart();
		}
	}

	/**
	 * Set a term to check
	 * @param term
	 */
	private void setText(String text) {
		textToCheck = text;
	}

	/**
	 * add a term to check
	 * @param term
	 */
	private void clearText() {
		textToCheck = null;
	}

	
	/**
	 * Check now
	 * @return
	 * @throws SpellCheckerException
	 */
	@SuppressWarnings("unchecked")
	public List<String> check() throws SpellCheckerException {
		SpellCheckerTermJob job = new SpellCheckerTermJob(this);
		List<String> list = null;
		try {
			list = (List<String>) job.call();
		} catch (Exception e) {}
		return list;
	}
	
	public Future<?> checkAsync() {
		SpellCheckerTermJob job = new SpellCheckerTermJob(this);
		return job.now();	
	}
	
	/**
	 * execute 
	 */
	protected List<TermSuggestions> execute() {
		List<TermSuggestions> list= null;
		
		clearError();
		
		if (!started) 
			start();

		if (started) {
			list = find(textToCheck);
			stop();
		}
		
		if (error != null)
			throw new SpellCheckerException(exception, error);
		
		return list;
	}
	
	/**
	 * Find spelling corrections for a given misspelled word.
	 * 
	 */
	public List<TermSuggestions> find(String term) {

		List<TermSuggestions> list= new ArrayList<SpellChecker.TermSuggestions>();
		try {
			writer.flush();

			writer.write((term+"\n"));
			writer.flush();
			
			String line = reader.readLine();
			while(line != null && line.length() > 0) {
				// convert result
				TermSuggestions ts = convertResult(line);
				// add suggestion to list
				if (ts.bad)
					list.add(ts);
				// next aspell result
				line = reader.readLine();
			}
		} catch (Exception e) {
			try {
				setError(e, errorReader.readLine());
			} catch (IOException e1) {
				setError(e, e.getLocalizedMessage());
			}
		}
		return list;
	}

	/**
	 * Converts a line from aspell 
	 * @param result, a result line from aspell.
	 * @return suggestions.
	 */
	private TermSuggestions convertResult(String line) {
		TermSuggestions termSuggestions = new TermSuggestions();
		
		StringTokenizer st = null;
		StringTokenizer sg = null;
		try {
			if (line.equals("*")) {
				termSuggestions.bad = false;
			} else if (line.substring(0, 1).equals("&")) {
				termSuggestions.bad = true;

				st = new StringTokenizer(line, ":", false);
				if (st.hasMoreTokens()) {

					// read result header '& term count position'
					String[] head = st.nextToken().split(" "); // get bad word info
					if (head.length != 4)
						throw new SpellCheckerException(null, "Cannot understand Aspell header :" + head);
					termSuggestions.term = head[1].trim();
					termSuggestions.count = Integer.parseInt(head[2].trim());
					termSuggestions.position = Integer.parseInt(head[3].trim());
					
					// get suggestions
					String suggestions = st.nextToken();

					// tokenize suggestions and loop
					String[] token = suggestions.split(",");
					for (String t : token)
						termSuggestions.list.add(t.trim());						
				} else {
					throw new SpellCheckerException(null, "Aspell result not supported : " + line); 
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			sg = null;
			st = null;			
		}
		return termSuggestions;
	}

	
	/**
	 * Converts the result from the aspell 
	 * @param result, a result line from aspell.
	 * @return suggestions.
	 */
	private List<String> convertResults(String backcandidates) {
		List<String> suggestions = new ArrayList<String>();
		StringTokenizer st = null;
		StringTokenizer st2 = null;
		try {
			if (!backcandidates.equals("*")) {
				st = new StringTokenizer(backcandidates, ":", false);
				if (st.hasMoreTokens()) {
					// skip things before column
					st.nextToken(); // remove & at the begining of line
					// skip things before column
					String stuffAfterColon = st.nextToken();
					// tokenize and loop
					st2 = new StringTokenizer(stuffAfterColon, ",", false);
					while (st2.hasMoreTokens())
						suggestions.add(st2.nextToken().trim());
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			st2 = null;
			st = null;			
		}
		return suggestions;
	}

	public void clearError() {
		error = null;
		exception = null;
	}
	
	private void setError(Exception e, String message) {
		error = message;
		exception = e;
		Logger.error(e, message);
	}

	public String getError() {
		return error;
	}
	
	public boolean hasError() {
		return error != null || exception != null;
	}

	
	/**
	 * Term Checker Job
	 * @author vincent
	 *
	 */
	static public class SpellCheckerTermJob extends Job {
		
		private SpellChecker checker;
		
		public SpellCheckerTermJob(SpellChecker checker) {
			this.checker = checker;
		}
		
		@Override
		public void doJob() throws Exception {
			doJobWithResult();
		}

		@Override
		public Object doJobWithResult() throws Exception {
			return checker.execute();
		}
	}


	static public class TermSuggestions {
		public boolean bad;
		public String term;
		public int position;
		public int count; // number of suggestions
		public List<String> list;
		
		public TermSuggestions() {
			list = new ArrayList<String>();
		}
	}
}
