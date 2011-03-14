package controllers;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import play.i18n.Lang;
import play.modules.spellchecker.SpellChecker;
import play.mvc.Controller;

public class SpellCheckerApi extends Controller {

	final public static String CHARSET_NAME = "ISO-8859-1";

	/**
	 * Test 
	 */
	public static void test() {
		render();
	}
	
	public static void check(String text, String lang) {
		List<String> list = null;
		Future<List<String>> task = null;
				
		if (text != null)
			try {
				text = URLDecoder.decode(text, "UTF-8");
			} catch (UnsupportedEncodingException e) {
				error(e);
			}
		
		if (request.isNew) {
			if (lang == null || lang.trim().length() == 0) lang = Lang.get();
			 task = (Future<List<String>>)
			 	SpellChecker.with(text, lang).checkAsync();
			request.args.put("task", task);
			waitFor(task);
		}

		try {
			task = (Future<List<String>>) request.args.get("task");
			list = task.get();
		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (ExecutionException e) {
			e.printStackTrace();
		}
		
		
		render(list);		
	}
}
