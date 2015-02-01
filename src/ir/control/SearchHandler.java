package ir.control;

import ir.model.WebPage;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.highlight.Highlighter;
import org.apache.lucene.search.highlight.InvalidTokenOffsetsException;
import org.apache.lucene.search.highlight.QueryScorer;
import org.apache.lucene.search.highlight.SimpleHTMLFormatter;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;
import org.jsoup.Jsoup;
import org.jsoup.select.Elements;



/**
 * 
 * SearchHandler class, in charge of searching with the existing index. It returns a ranked list,
 * of type WebPage. These items should contain all information necessary for printing the search
 * results.
 * <p>
 * By accessing the source code, the VERBOSE and DEBUG_MODE flags can be changed so as to print status
 * messages during the run.
 * 
 *  @author Gabriel
 *  
 *  */

public class SearchHandler {
	/**Configurable variables */
	
	/**Maximum number of results, by default 10. */
	final int numberOfResults = 10; // Number of results: how many results should be returned for a query?
	
	/**For debugging and studied runs. It requests the class to print some status messages. As shipped its value is false.*/
	private static boolean VERBOSE = false;
	
	/**For debugging and studied runs. It requests the class to print additional status messages. As shipped its value is false.*/	
	@SuppressWarnings("unused")
	private static boolean DEBUG_MODE=false;
	
	
	/**Additional class members */
	
	/**Location of the index.*/
	private Directory indexFolder=null;
	
	/**Functions */
	
	/**
	 * Parametric constructor.
	 * @param indexFolder Folder where the index is located.
	 * */
	public SearchHandler(String indexFolder) {
		try {
			this.indexFolder=FSDirectory.open(new File(indexFolder));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			System.out.println("Error: Search Handler not initialized: Invalid directory.");
		}
	}
	
	/**
	 * The main method to handle searching over an existing index. It receives a query and
	 * generates a ranked list of type WebPage. If there are no results, it returns an empty list.
	 * <p>
	 * Additional specs: The search string is lowercased before doing the query. It's analysed with a Lucene Standard Analyzer
	 * and the search is performed using a MultiFieldQueryParser, over the code, title and content sections of the index entries.
	 * 
	 * @param queryString A string containing the query.  
	 * @throws IOException
	 * @throws ParseException
	 */
	@SuppressWarnings("deprecation")
	public List<WebPage> searchIndex(String queryString) throws IOException,ParseException {
	
		queryString=queryString.toLowerCase();//We lower case the search string. It was observed to improve results.
		
		List<WebPage> resultingList = new ArrayList<WebPage>();
		
		/*
		 * "normal" query
		 */
		if (VERBOSE)
			System.out.println("Searching for '" + queryString + "'");
		
		StandardAnalyzer sa = new StandardAnalyzer(Version.LUCENE_4_10_0);
		MultiFieldQueryParser multiFieldQP = new MultiFieldQueryParser(new String[] { "code", "title", "content", "programming_language" }, sa);
		Query q = multiFieldQP.parse(queryString);
		
		IndexReader ir = IndexReader.open(indexFolder);
		IndexSearcher is = new IndexSearcher(ir);
		TopDocs results = is.search(q, numberOfResults);
		ScoreDoc[] hits = results.scoreDocs;

		    
		if (VERBOSE){
			
			System.out.println("Total matching documents: " + results.totalHits);		
		}
		
		/*
		* Print the result list
		*/
		SimpleHTMLFormatter htmlFormatter = new SimpleHTMLFormatter();	
		Highlighter highlighter = new Highlighter(htmlFormatter, new QueryScorer(q));		
		
		for (int i = 0; i < hits.length; ++i) {	
				Integer rank=(i + 1);
				String title;
				String url;
				String code;
				String summary;
				float relevanceScore=hits[i].score;
				
				if (VERBOSE){
					System.out.println("********************************************");
					System.out.println("Rank=" + rank + " Score=" + relevanceScore);
				}
				
				Document doc = is.doc(hits[i].doc);
		
				title = doc.get("title");
				if (title == null) {
					title="No title available for this document";
				}
				
				url = doc.get("url");
				if (url == null) {
					url="No URL available for this document";
				}
				
				code= doc.get("code");
				if (code == null) {
					code="No code snippets available for this document";
				}
				
				if (VERBOSE){
					System.out.println("Title: "+title+ " URL:"+url);
					System.out.println("Code Snippets: "+code);
				}
				

				
		        String text="";
		        Elements bodyAux  = Jsoup.parseBodyFragment(doc.get("content")).body().getAllElements();
		        for (int k=0; k<bodyAux.size(); k++){
		        	boolean isAdded=false;
		        	for (int j=0; j<queryString.split(" ").length; j++){
		        		if (!isAdded){
		        			if (bodyAux.get(k).text().contains(queryString.split(" ")[j])){
		        				isAdded=true;
		        				text+=bodyAux.get(k).ownText();
		        			}
		        		}
		        	}
		        }
	        	String [] resultStrArray=null;
		        if (text.length()<2){
		        	try {
						resultStrArray= highlighter.getBestFragments(sa, queryString, Jsoup.parseBodyFragment(doc.get("content")).body().text(), 2);
					} catch (InvalidTokenOffsetsException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
		        }
		        else {
		        	try {
		        		resultStrArray= highlighter.getBestFragments(sa, queryString, text, 2);
		        	} catch (InvalidTokenOffsetsException e1) {
		        		// TODO Auto-generated catch block
		        		e1.printStackTrace();
		        	}
		        }
		        if ((resultStrArray != null) && resultStrArray.length>0 ) {
					String resultStr="";
					for (int k=0; k<resultStrArray.length; k++){
						resultStr+=resultStrArray[k]+"...";
					}
					resultStr = resultStr.replace("\n", "").replace("\r", "");
					summary=resultStr;
		        }
		        else{
					summary=doc.get("summary").replace("\n", "").replace("\r", "");
					if (summary.length()>140){
						summary=summary.substring(0, 140);
						summary+="...";
					}
		       	}
		        if(summary.length()<1){
		        	summary="No highlights for this document.";
		        }
		        
		        if (VERBOSE){
					System.out.println("Highlights: "+summary);
				}
		       
		        resultingList.add(new WebPage(rank, title, url, summary, code, relevanceScore, results.totalHits));
			}
		return resultingList;
	}

}
