package ir.control;

import ir.model.WebPage;
import ir.view.*;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

import org.apache.lucene.queryparser.classic.ParseException;

/**
 * 
 *          This is the main class of the project. The control flow of the
 *          program starts and ends in the main method of this class. Creating
 *          other methods here is not advised.
 *          <p>
 *          As shipped it only starts the GUI. But by accessing the source code it can be manually changed
 *          to allow console runs. This could be useful for debugging. If expecting messages on this
 *          scenario, DEBUG_MODE and VERBOSE can be set to true in the SearchHandler and WebCrawler classes.
 *          <p>
 *          NOTE: The seeds passed as input to the WebCrawler object must be valid.
 *          <p>
 *          
 * @author Gabriel
 */

public class CodeSearch {
	
	/**
	 * Main function. No arguments used as input.
	 * 
	 * */
	public static void main(String[] args) {
		
		boolean useGUI=true;

		if (useGUI){
			java.awt.EventQueue.invokeLater(new Runnable() {
	            public void run() {
	                new CodeSearchUI().setVisible(true);
	            }});

		}

		else{//Former code, still useful for testing control and model packages
			/**Configurable variables */
			String dir = "default_index";
			boolean createIndex=true; //Signals if the index should be created or read from the directory.
			int depth= 1;
			String searchString="highlighter";
			String urlSeed="http://stackoverflow.com/questions/17535514/lucene-highlighter"; //"https://github.com/joyent/node/tree/master/benchmark/arrays";//
			List<URL> seeds= new ArrayList<URL>(); //Seeds to be passed as input
		
			/**
			 * We set up our first seed (web page url). 
			 */
			URL url = null;
			try {
				url = new URL(urlSeed);
			} catch (MalformedURLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			seeds.add(url);
		
			/**
			 * Here we access the Webcrawler by using getInstance().
			 * The function crawl takes as input the seed URLs, the crawling depth, the selected directory, and
			 * a boolean createIndex, that tells the object if an index will be created or read from the selected
			 * directory.
			 * 
			 * For this example the seeds list has only one url: urlSeed.
			 *  
			 */
			try {
				WebCrawler.getInstance().crawl(seeds, depth, dir, createIndex);
			} catch (InterruptedException e) {
				// 	TODO Auto-generated catch block
				e.printStackTrace();
			}
		
			/**
			 * 	Now we perform a query over the index and crawled pages. 
			 * The searchIndex function takes as input the query as a string.
			 * 
			 * The function outputs a ScoreDoc array with the results, of size 10 (by default)
			 * 
			 * */
		
			SearchHandler mysearcher = new SearchHandler(dir);
			try {
				List<WebPage> hits= new ArrayList<WebPage>();
				hits.addAll(mysearcher.searchIndex(searchString));
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (ParseException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
}
