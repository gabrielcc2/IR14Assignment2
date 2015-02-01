package ir.control;

import ir.model.ItemUrl;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;
import org.apache.lucene.document.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.jsoup.Connection.Response;
import org.jsoup.Jsoup;




import net.sf.classifier4J.summariser.*; 

/**
 * 
 * WebCrawler class, allowing to crawl over a list of seeds, with a given crawling depth
 * and using or not, an existing index. 
 * <p>
 * It is a singleton. This design decision allows it to keep a consistent state.
 * <p>
 * It uses several helper threads of a nested runnable class called CrawlerThreads.
 * Each one crawls a specific set of hosts (which are assigned by the main thread, running the innerCrawl function), 
 * with a wait time between requests to the same host.
 * <p>
 * The WebCrawler object acts as manager, loading/creating the index and starting/restarting the threads to run 
 * over the seeds and newly found URLs. Additionally it oversees the assignment of hostnames to threads, and the
 * checking of URLs before visiting.
 * <p>
 * The processing overall begins with the Main Thread loading the lists of visited and excluded pages. Then, it validates
 * that the seeds are correct and visits the first one, indexing it. This visit is distinguished from others, because if 
 * an index must be created, it is done at this point. After visiting the first page, the main thread extracts from it
 * a set of normalized tentativeUrls. Then it further checks them and adds them to a queue of sorts, called nextUrls.
 * <p>
 * Then it starts threads, assigning each one to a hostname from nextUrls, so each helper thread can index a page and 
 * in turn extract a set of normalized tentativeUrls from each page. 
 * <p>
 * Then these are then checked by the main thread (WebCrawler), and inserted in nextUrls. 
 * <p>
 * This queue can then be scanned by each helper thread, in search of URLs from their assigned hostnames. 
 *<p>
 * More specifically the helper threads themselves request a connection to a host, in order to get a page from each URL (using Jsoup)
 * and/or to get a robots.txt file for complying with exclusion requests from servers. 
 * They index the pages found (using a Lucene Index), and according to depth restrictions, they crawl outlinks, 
 * adding them to the list of tentativeUrls. 
 * They also mark each page as visited. To do this they remove it from the queue of nextUrls and insert it into
 * a list called visitedUrls.
 * <p>
 * The processing stops in the main thread when all threads are finished and the lists for tentativeUrls and nextUrls are empty. 
 * Then the thread joins the helper threads.
 * <p>
 * In general, before attempting to access a URLs from a list, we carry out the following 5 steps:
 * 				1) check if they are repeated in the current list or when joined with another list, 
 * 				2) check if they are already visited,
 * 				3) normalize them: in our code, this means to remove text preceeded by ?,#, javascript:, " ", and checking
 * 				   for malformed exceptions for urls.
 * 				4) check if they are valid &
 * 				5) check if they are to be excluded.*This has to be done in the updated list.
 * <p>
 * Support for interacting with servers using the robots.txt protocol is provided, although in a very 
 * simple way, and not parsing complex disallow expressions that use wildcards, such as 
 *                                                                             "Disallow: /?This#Line%20/*+*OrThis"
 * <p>
 * By accessing the source code, the VERBOSE and DEBUG_MODE flags can be changed so as to print status
 * messages during the run.
 * <p>
	 *  Some suggestions for future work:
	 *  <p>
	 *  ADD URL VALIDATION. This refers to: 
	 *     
	 * 	   A validation of the URL itself. This refers to a path been exceedingly long, or a bot trap
	 *     which can be seen in the form of the URL. This could be done in a function that is called
	 *     isValid, set up below. Other verifications could be easily embedded by using this function.
	 *     Other forms of validation could also be added as part of the normalization processing.
	 *  <p>   
	 *     Avoiding bot traps in other forms.
	 *  <p>
	 *  STORE AND USE STATS:
	 *     As part of this validation analysis, it could also be suggested to develop an object for storing
	 *     providing, and using Stats about the crawling. 
	 *  <p>
	 *  IMPROVE PARSING OF ROBOTS.TXT FILE. AVOID EXCEPTION WHEN ROBOTS.TXT NOT FOUND.
	 *  <p>
	 *  MORE INTERACTIVE HANDLING OF THE "NO INDEX FOUND IN DIRECTORY" SCENARIO:
	 *  	As it is, only a System.out message is shown. This could be improved via the GUI.
	 *  <p>
	 *  LOAD BALANCING TO DISTRIBUTE THE WORK AMONG THE THREADS
	 *  <p>
	 *  ADDING COMPRESSION AND FORMATTING TO VISITED AND EXCLUDED FILES, SO AS TO REDUCE RUNTIME FOR LOADING THEM
	 *  <p>
	 *  IMPROVEMENTS TO QUERY AND INDEXING, SO AS TO SUPPORT SEARCH WITHIN A SPECIFIC SITE, AMONG OTHER OPTIONS.
	 *  <p>
	 *  PERFORM MORE TESTS OF THE CHECK FAIL-SAFE PROPERTIES OF ERRORS WHILE VISITING THE FIRST URL IN THE SEEDS LIST.
	 * 		These properties are present in the remaining URLS, as they are handled in helper threads.    
	 * <p>
	 *  ADD MORE SITE-SPECIFIC CHECKS FOR DUPLICATE PAGES. THIS COULD BE DONE IN THE isVisited FUNCTION, FOLLOWING OUR
	 *  APPROACH FOR STACKOVERFLOW. ADDITIONALLY THE SAME COULD BE DONE FOR NORMALIZING.
	 *  <p>
	 *  IMPROVE USE OF LUCENE LOCKS TO AVOID org.apache.lucene.store.LockObtainFailedExceptions AND THUS AVOID STALLS OR NOT
	 *  INDEXING OF SOME PAGES.
	 *  <p>
	 *  WORK ON SITE INDPENDENT DUPLICATE URL OR PAGE DETECTION
	 *  <p>
	 *  LIST ALL PAGES NOT INDEXED BECAUSE OF EXCEPTIONS, NEXT TO THE EXCEPTIONS FOR FURTHER ANALYSIS ON THE CAUSES AND IMPROVEMENTS
	 *  <p>
	 *  IMPROVE THE PROGRAMMING LANGUAGE DETECTION
	 *  	So far our program just searches for the name of some programming languages in the content. If it finds any then it adds
	 *  	a programming language field and boosts it by 1.5. The benefits of this have not been fully tested. Additionally this 
	 *  	might be improved with better detection mechanisms, and a more complete domain description.
 * 
 * @author Gabriel 
 * @author Rene
 * 
 */

public class WebCrawler {
	
	/**Singleton instance of type WebCrawler */
	private static WebCrawler instance= null;
	
	/**Configurable variables */
	
	/**For debugging and studied runs. It requests the class to print some status messages. As shipped its value is true. This
	 * establishes the message that will be printed during the crawling proceeds.
	 * */
	private static boolean VERBOSE=true; 
	
	/**For debugging and studied runs. It requests the class to print additional status messages. As shipped its value is false.*/
	private static boolean DEBUG_MODE=false; 
	
	/**Maximum number of threads. By default 100.*/
	private static int MAX_NUM_THREADS=100;
	
	/**Minimum document length for indexing. By default 20.*/
	private static int MINIMUM_DOC_LENGTH_FOR_INDEXING=20;
	
	/**Wait time for each thread between requests to a host. By default 500 milliseconds.*/
	private static int WAIT_TIME_IN_MILLISECONDS_THREADS=500; 
	
	/**Wait time for main thread before attempting to join an apparently idle thread and then restart it. By default 500 milliseconds.*/
	private static int WAIT_TIME_IN_MILLISECONDS_MAIN_THREAD=500; //Wait time for main thread
	
	/**Name of visited file. By default visited.txt*/
	private static String VISITED_FILE="visited.txt";
	
	/**Name of excluded file. By default excluded.txt*/
	private static String EXCLUDED_FILE="excluded.txt";
	
	/**Name of location for default index. By default the folder default_index*/
	private static String DEFAULT_INDEX_FOLDER="default_index";
	
	/**User agent name, for Jsoup. This prevents some exceptions. By default: Mozilla/5.0 (Macintosh; Intel Mac OS X 10_9_2) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/33.0.1750.152 Safari/537.36.*/
	private static String USER_AGENT="Mozilla/5.0 (Macintosh; Intel Mac OS X 10_9_2) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/33.0.1750.152 Safari/537.36";

	/**Referrer name, for Jsoup. This prevents some exceptions. By default: http://www.google.com*/
	private static String REFERRER="http://www.google.com";
	
	/**Additional class members*/

	/**Seed for randomization*/
	private Random rand = new Random(); //Seed for randomization
	
	/**Current index folder.*/
	private String currentIndexFolder=null; //Directory for index
	
	/**Flag that indicates if the system uses a non-default index. */
	private boolean usingNonDefaultIndex=false;
	
	/**Flag that indicates if the system is currently crawling. */
	private boolean isCrawling=false;

	/**Maximum crawl depth for a given run. */
	private int maxCrawlDepth;

	/** The following 3 lists determine the processing workflow:
	 * 	After each web page is visited, a list of tentativeUrls can be established. These are normalized Urls.
	 *  Shortly after this list can be improved, by validating it. It results in the nextUrls list.
	 *  Finally, after being visited, they can be removed from all lists and inserted in the visited list.
	 * */
	
	/**Pool of URLs that could be used, tentatively, for crawling. They are stored as elements of type ItemUrl, so they can be grouped with their depth.*/
	private List <ItemUrl> tentativeUrls= new ArrayList<ItemUrl>();  
	/**Lock for synchronized access to tentativeUrls*/
	private Object tentativeUrls_lock = new int[1];

	/**Pool of validated URLs for crawling. They are stored as elements of type ItemUrl, so they can be grouped with their depth.*/
	private List <ItemUrl> nextUrls= new ArrayList<ItemUrl>(); //Pool of validated URLs for crawling, next to their depth. 

	/**Lock for synchronized access to nextUrls*/
	private Object nextUrls_lock = new int[1];

	/**List of visited URLs*/
	private List <URL> visitedUrls= new ArrayList<URL>();
	/**Lock for synchronized access to visitedUrls*/
	private Object visitedUrls_lock = new int[1];
	/**List of excluded URLs, built by complying with robots.txt standard.*/
	private List <URL> excludedUrls= new ArrayList<URL>();  
	/**Lock for synchronized access to excludedUrls*/
	private Object excludedUrls_lock = new int[1];
	
	
	/**Lock for granting all threads concurrent access to the stored index.*/
	private Object index_lock = new int[1]; 
	
	/**Map to connect hostname to index of specialized thread, in charge of visiting pages in this domain.*/
	private Map<String,Integer> hostIndex= new HashMap<String,Integer>(200); 
	
	/**Thread in charge of the crawling.*/
	private List<CrawlerThread> threads= new ArrayList<CrawlerThread>(); 
	
	/**Functions */
	
	/**Protected constructor function, to defeat instantiation. */
	protected WebCrawler(){
		 // Exists only to defeat instantiation.
	}
	
	/**getInstance function, for singleton use*/
	public static WebCrawler getInstance() {
	      if(instance == null) {
	         instance = new WebCrawler();
	      }
	      return instance;
	}
		
	/**
	 * Function to monitor the crawling for exceptions, and keep consistency. It launches the crawling by calling the private function
	 * innerCrawl, where the crawling is actually carried out.
	 * 
	 * @param seeds list of URLs corresponding to non-validated seeds. They are already checked for MalformedExpression.
	 * @param crawlDepth crawling depth
	 * @param indexFolderAddress index location
	 * @param resetIndex boolean flag indicating if the crawler should reset or create the index
	 * 
	 * @throws InterruptedException
	 * 
	 * @author Gabriel
	 * 
	 * */
	public void crawl(List<URL> seeds, int crawlDepth, String indexFolderAddress,  boolean resetIndex) throws InterruptedException {
		try {
			innerCrawl(seeds, crawlDepth, indexFolderAddress, resetIndex);
		}
		catch(InterruptedException e){
			System.out.println("Crawling Suspended:");
			System.out.println("Unfortunately there has been an exception that we could not handle.");
			System.out.println("Please close the window and try with other urls.");
			isCrawling=false;
			e.printStackTrace();
		}
		catch(ArrayIndexOutOfBoundsException e1){
			System.out.println("Crawling Suspended:");
			System.out.println("Unfortunately there has been an exception that we could not handle.");
			System.out.println("Please close the window and try with other urls.");
			isCrawling=false;
			e1.printStackTrace();
		}
		catch(IndexOutOfBoundsException e2){
			System.out.println("Crawling Suspended:");
			System.out.println("Unfortunately there has been an exception that we could not handle.");
			System.out.println("Please close the window and try with other urls.");
			isCrawling=false;
			e2.printStackTrace();
		}

	}
	
	/**
	 * Function that implements the crawling over a set of seeds
	 * 
	 * @param seeds list of URLs corresponding to non-validated seeds. They are already checked for MalformedExpression.
	 * @param crawlDepth crawling depth
	 * @param indexFolderAddress index location
	 * @param resetIndex boolean flag indicating if the crawler should reset or create the index
	 * 
	 * @throws InterruptedException 
	 * 
	 * @author Gabriel
	 */
	private void innerCrawl(List<URL> seeds, int crawlDepth, String indexFolderAddress,  boolean resetIndex) throws InterruptedException{
		isCrawling=true;
		threads.clear();
		hostIndex.clear();
		this.maxCrawlDepth=crawlDepth;
		this.currentIndexFolder=indexFolderAddress;
		
		tentativeUrls.clear();
		nextUrls.clear();
		visitedUrls.clear();
		excludedUrls.clear();
		
		List <URL> previouslyVisitedUrls = new ArrayList<URL>(); //Will only be used in VERBOSE mode.
		List <URL> previouslyExcludedUrls = new ArrayList<URL>(); //Will only be used in VERBOSE mode.
		List<URL> auxURLs= new ArrayList<URL>();//Auxiliary array
		usingNonDefaultIndex=false;
		if (indexFolderAddress.equals(DEFAULT_INDEX_FOLDER)||indexFolderAddress.equals("DEFAULT")){
			indexFolderAddress=DEFAULT_INDEX_FOLDER;
		}
		else{
			usingNonDefaultIndex=true;
		}
		
		if (VERBOSE){
			System.out.println("Crawling will begin shortly. It might take several minutes.");
			System.out.println("Note: HttpConnection exceptions might be shown for given URLs and servers. These pages are not indexed.");
			System.out.println("Note: Non-determinism in multi-threading with JVM and implicit use of Lucene's locks might cause eventual stalls, but the crawling is not interrupted and resumes after a minute.");
			System.out.println("Unless the program stops fully, these exceptions and stalls can be considered irrelevant.");
			if (!DEBUG_MODE)
				System.out.println("For more messages during the execution, the DEBUG_MODE flag could be used.(Only configurable in the code)");
		}
		
		/**Processing if the user wants to use an existing index...*/
		if (!resetIndex){ //User requests to start from a created index
			
			/**Here we load the visited and excluded lists*/
			if (!seeds.isEmpty()){
				BufferedReader reader=null;  
				try {
					reader = new BufferedReader(new FileReader(indexFolderAddress+"/"+VISITED_FILE));
					String line = null;
					while ((line = reader.readLine()) !=null){
						markAsVisited(new URL((line.toString()))); //The visited URLs file is read and loaded to the visited array.
					}
				}
				catch (Exception e) {
					e.printStackTrace();
					System.out.println("Visited URLs file not found");
				} finally {
					try {
						reader.close();
					} catch (Exception e) {
					  e.printStackTrace();
					}
				}
				try {
					reader = new BufferedReader(new FileReader(indexFolderAddress+"/"+EXCLUDED_FILE));
					String line = null;
					while ((line = reader.readLine()) !=null){
						excludedUrls.add(new URL((line.toString()))); //The excluded URLs file is read and loaded to the excluded array.
					}
				}
				catch (Exception e) {
					e.printStackTrace();
					System.out.println("Excluded URLs file not found");
				} finally {
					try {
						reader.close();
					} catch (Exception e) {
					  e.printStackTrace();
					}
				}
				try { //Now we check if there is no index in the selected directory. If so, an index is created.
					FSDirectory dir = FSDirectory.open(new File(indexFolderAddress));
					// Check if there already is an existing index on the directory
					if (!DirectoryReader.indexExists(dir)) {
						System.out.println("There is no index in the selected directory.");
						
						/**Suggestions for future work: For this case if there is no index, we just show a message to the user.
						*  
						* Perhaps a more interactive scheme could be arranged in coordination with the class in charge of
						* user interaction.
						*  
						* **/
						
						resetIndex=true;
					}
					dir.close();
				}
				catch (IOException e1) {
					e1.printStackTrace();
					System.out.println("Selected directory could not be accessed.");
				}
			}
			
			/**Just in case we will remove repeated elements from the visitedUrls list.*/
			auxURLs.addAll(removeRepeated(visitedUrls));
			visitedUrls.clear();
			visitedUrls.addAll(auxURLs);
			
			auxURLs.clear();
			
			/**Just in case, we will also normalize all elements in the visitedUrls list*/
			threads.add(new CrawlerThread(0)); //Here we use a dummy thread, to use some functions of a crawlerThread object. This is not an actual thread.
			for (URL url: visitedUrls){
				try {
					auxURLs.add(new URL (threads.get(0).normalize(url.toString())));
				} catch (MalformedURLException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					System.out.println("One of the previously visited URLs was considered invalid:"+url.toString());
				}
			}
			threads.clear();//End of use of dummy thread
			visitedUrls.clear();
			visitedUrls.addAll(auxURLs);
			
			if (VERBOSE){ //Here we print the previouslyVisited URLs and previouslyExcluded URLs
				previouslyVisitedUrls.addAll(visitedUrls);
				if (!previouslyVisitedUrls.isEmpty()){
					System.out.println("********************************************");
					System.out.println("Previously visited URLs: ");
					
					/**This extra processing is to print them alphabetically */
					
					String [] list = new String [previouslyVisitedUrls.size()];
					int i=0;
					for (URL url: previouslyVisitedUrls){  
						list[i]=url.toString();
						i++;
					}
					Arrays.sort(list);
					for (int j=0; j<previouslyVisitedUrls.size(); j++){
						System.out.println(list[j]);
					}
					System.out.println("********************************************");
				}
				
				previouslyExcludedUrls.addAll(excludedUrls);
				if (!previouslyExcludedUrls.isEmpty()){
					System.out.println("********************************************");
					System.out.println("Previously excluded URLs: ");
					
					/**This extra processing is to print them alphabetically */
					
					String [] list = new String [previouslyExcludedUrls.size()];
					int i=0;
					for (URL url: previouslyExcludedUrls){  
						list[i]=url.toString();
						i++;
					}
					Arrays.sort(list);
					for (int j=0; j<previouslyExcludedUrls.size(); j++){
						System.out.println(list[j]);
					}
					System.out.println("********************************************");
					System.out.println("Crawling is being carried out. It might take several minutes.");
					System.out.println("Note: HttpConnection exceptions might be shown for given URLs and servers. These pages are not indexed.");
					System.out.println("Note: Non-determinism in multi-threading with JVM and implicit use of Lucene's locks might cause eventual stalls, but the crawling is not interrupted and resumes after a minute.");
					System.out.println("Unless the program stops fully, these exceptions and stalls can be considered irrelevant.");
					if (!DEBUG_MODE)
						System.out.println("For more messages during the execution, the DEBUG_MODE flag could be used.(Only configurable in the code)");
					System.out.println("********************************************");
				}
			}
		}
		/**End of processing if the user wants to use an existing index...*/
		

		/** In general, before using URLs from a list, we should carry out the following 5 steps:
		 * 				1) check if they are repeated in the current list or when joined with another list, 
		 * 				2) check if they are already visited,
		 * 				3) normalize them,
		 * 				4) check if they are valid &
		 * 				5) check if they are to be excluded.
		 * 
		 * */
		
		/**For the list of seeds we start by step(1): removing repeated elements.
		 *  
		 * */
		auxURLs.clear();
		auxURLs.addAll(removeRepeated(seeds));
		seeds.clear();
		seeds.addAll(auxURLs);
		
		 
		/** Now we remove the already visited URLs from the seeds list (step(2))
		 */
		
		auxURLs.clear();
		auxURLs.addAll(removeIfExistsInOtherList(seeds, visitedUrls));
		seeds.clear();
		seeds.addAll(auxURLs);
		
		/** Now we remove the excluded URLs from the seeds list (step(5))
		 */		
		auxURLs.clear();
		auxURLs.addAll(removeIfExistsInOtherList(seeds, excludedUrls));
		seeds.clear();
		seeds.addAll(auxURLs);
		

		if (!seeds.isEmpty()){//If seeds are not empty after steps 1, 2, 5... Most of the program runs inside this branch.
			
			threads.add(new CrawlerThread(0)); //Here we use a dummy thread, to use some functions of a crawlerThread object. This is not an actual thread.
		    
			//Now seeds are normalized and validated before being used. Steps (3) and (4)
			for (URL seed : seeds) {
				
		    	String tempURL=threads.get(0).normalize(seed.toString());//Note that this is a partial normalization, for a full normalization a base URL is needed.
		    	
		    	try {
		    		URL url2=new URL (tempURL);
					if (isValid(url2)){
						if (!isExcluded(url2)){ //Step (5), again after normalization.
							nextUrls.add(new ItemUrl(url2,0));
						}
					}
					
				} catch (MalformedURLException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
		    }
			
		    if (!nextUrls.isEmpty()){
		    	/** To start the process we crawl the first seed.
				*   This case is distinguished from others, because here the index can be created
				* */
		    	//Note we can visit because steps 1-5 have already been carried out.
		    	
		    	if (nextUrls.get(0).getDepth()+1<=maxCrawlDepth){//According to depth we can crawl or simply index
		    		List<URL> newPages= new ArrayList<URL>();
					try {//Now we index the page from the first URL and obtain a list of it's outlinks
						
						//We start by getting the excluded list of the host
						if(!isVisitedHost(nextUrls.get(0).getUrl())){
							newPages = threads.get(0).getExcludedList(nextUrls.get(0).getUrl());
							if (!newPages.isEmpty()){
								excludedUrls.addAll(newPages);
								auxURLs.clear();
								auxURLs.addAll(removeRepeated(excludedUrls));
								excludedUrls.clear();
								excludedUrls.addAll(auxURLs);
							}
						}
						newPages = threads.get(0).crawlAndIndexPage(nextUrls.get(0).getUrl(), resetIndex); //Note the use of createIndex instead of false
					} catch (Exception e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}	
					for (URL page: newPages){
						tentativeUrls.add(new ItemUrl(page, 1)); //We add the outlink list to tentativeUrls, with depth 1
					}
					/**Now we carry out steps (1) and (2) and (5) for the list of tentativeURLs
					 * */
					tentativeUrls=cleanUrlList(tentativeUrls);
					tentativeUrls=removeVisited(tentativeUrls, visitedUrls);
					tentativeUrls=removeExcluded(tentativeUrls, excludedUrls);
		    	}
		    	else if (nextUrls.get(0).getDepth()==maxCrawlDepth){ //We will simply index the page
		    		org.jsoup.nodes.Document doc=null;
					try {
						String startUrl=nextUrls.get(0).getUrl().toString();
						
						//Before indexing we will get the excluded list of the host
						if(!isVisitedHost(nextUrls.get(0).getUrl())){
							auxURLs = threads.get(0).getExcludedList(nextUrls.get(0).getUrl());
							if (!auxURLs.isEmpty()){
								excludedUrls.addAll(auxURLs);
								auxURLs.clear();
								auxURLs.addAll(removeRepeated(excludedUrls));
								excludedUrls.clear();
								excludedUrls.addAll(auxURLs);
							}
						}

						doc = Jsoup.connect(startUrl).userAgent(USER_AGENT).referrer(REFERRER).get();
												
						/**The following while loop tries to catch redirects and mark as visited all the intermediate URLs
						 * It checks if the location of the retrieved document is the same as the one from the 
						 * requested URL.*/
						
						while(!threads.get(0).normalize(doc.location()).equals(startUrl)){
							String aux=startUrl;
							Response response = Jsoup.connect(startUrl).userAgent(USER_AGENT).referrer(REFERRER).followRedirects(false).execute();
							startUrl=response.header("location");
							if(aux==null || startUrl==null){ //If the response has no header or fails, we stop the loop
								startUrl=threads.get(0).normalize(doc.location());
							}
							else if(aux==startUrl){ //If the response traps the request in a single site, we stop the loop
								startUrl=threads.get(0).normalize(doc.location());
							}
							else {
								startUrl=threads.get(0).normalize(startUrl, aux);
								try {
									markAsVisited(new URL(startUrl));//Note we mark all URLs as visited, except for the starting one, which is marked later.
								} catch (MalformedURLException e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
								}
							}
						}
						threads.get(0).indexPage(doc, new URL (threads.get(0).normalize(doc.location())), resetIndex);//Note the use of createIndex instead of false

					} catch (Exception e1) {
					// TODO Auto-generated catch block
						e1.printStackTrace();
					} 
		    	}
		    	markAsVisited(nextUrls.get(0).getUrl()); //The first URL seed is now marked as visited and removed from the nextUrls list.	
		    	nextUrls.remove(0);
		    }
		    
		    threads.clear();//End of dummy thread use
		    
		    /** Now that the index is created at least for one document, we can start processing while using an existing index.
		     * */
		    
		    
		    /*Boolean variables used to avoid synchronization issues.*/
		    boolean tue=tentativeUrls.isEmpty(); 
		    boolean nue=nextUrls.isEmpty();
		    boolean tb=threadsBusy();

		    /**Start of parallelized execution*/
		    while (!tue || !nue || tb){ /**The crawling proceeds as long as there are items in the tentativeUrls or the nextUrls, 
		    	                        * or there are threads busy.
		    	                        * 
		    	                        * In this while loop threads will be created and/or restarted, as well as assigned to specific hostnames. 
		    	                        * 
		    	                        * The threads themselves will consult the nextUrls list and crawl and/or index, according to their hostnames. 
		    	                        * The detected outlinks from the threads will be stored in the tentativeUrls list.
		    	                        * 
		    	                        * This list will be processed in this loop by the main thread, and then added to the nextUrls list, so threads
		    	                        * can process these URLs.
		    	                        * 
		    	                        * During this while loop we will perform steps 1, 2 and 4 over the nextUrls and tentativeUrls lists.
		    	                        * Step 3 is performed by the helper threads.
		    	                        * 
		    	                        **/
		    	
		    	List<ItemUrl> nextUrls2= new ArrayList<ItemUrl>(); //To improve synchronization, we work with local copies.
		    	synchronized (nextUrls_lock){
		    		nextUrls2.addAll(cleanUrlList(nextUrls)); 
		    		nextUrls.clear();
		    		nextUrls.addAll(nextUrls2);
		    	}
		    	
		    	for (ItemUrl url : nextUrls2){ //We will iterate over all pending urls, and assign to a host. Otherwise we just check if its already assigned.
		    		String host=url.getUrl().getHost();
		    		int selectedThread=-1;
		    		/**The hostIndex map will be used to map each thread to a host
		    		 * No locking will be required since it is only used by the main thread.
		    		 * */
		    		if(hostIndex.get(host)==null){ //No thread specialized on this host
		    			if (threads.size()>=MAX_NUM_THREADS){ //We cannot assign to a new thread
		    												 
		    				selectedThread=getFreeThread(); //So we try to assign to the first free thread.
		    				
		    				if (selectedThread==-1){     //All threads are busy, so we assign to a random thread.
		    					 selectedThread=rand.nextInt((threads.size()-1) + 1); //Suggestions for future work: Perhaps load balancing might be better
		    				}
			    			threads.get(selectedThread).addHost(host);//We add the host to the threads hostnames list
			    			hostIndex.put(host, selectedThread); //We assign the host to the thread
			    			
			    			/**Wait before attempting join. 
			    			 * This gives threads a chance to start and get busy, before the following check.
			    			 */
			    			Thread.sleep(WAIT_TIME_IN_MILLISECONDS_MAIN_THREAD);  
			    			
			    			if (!threads.get(selectedThread).isBusy()){//If the assigned thread isn't busy we have to restart it
			    				if(!threads.get(selectedThread).t.equals(Thread.currentThread())){
			    					try {
			    						if (VERBOSE){
			    							System.out.println("Main thread joining thread:"+selectedThread);
			    						}
			    						threads.get(selectedThread).t.join(); /**So as not to cancel the thread we join it. 
			    						* If the thread was not busy, this shouldn't be a problem.
			    						* */
			    					}
			    					catch (InterruptedException e) {
			    						System.out.println("Main thread Interrupted");
			   						}
			   					}
			    				/**Now we start a new thread with the same hostnames as the previous
			    				thread, AND the new hostname as well, but assigned to the current set of nextUrls.*/
			    				
			    				threads.get(selectedThread).t= new Thread (threads.get(selectedThread)); 
			    				threads.get(selectedThread).t.start();
			    				
			    				if (VERBOSE)
	    							System.out.println("Selected Thread Restarted: "+selectedThread);			    		
			   				}
		    				/**If the selected thread was busy, then it will find the hostname in it's list and process the corresponding pages.*/	
		    			}
		    			else{ //Threads available for starting
		    				threads.add(new CrawlerThread(threads.size()));
		    				selectedThread=threads.size()-1;
			    			threads.get(selectedThread).addHost(host);
			    			hostIndex.put(host, selectedThread);
		    				threads.get(selectedThread).t= new Thread (threads.get(selectedThread));
		    				threads.get(selectedThread).t.start();
		    			}
		    		}
		    		else {//A specialized thread already exists for this hostname
		    			selectedThread=hostIndex.get(host);
		    			
		    			/**Wait before attempting join. 
		    			 * This gives threads a chance to start and get busy, before the following check.
		    			 */
		    			
		    			Thread.sleep(WAIT_TIME_IN_MILLISECONDS_MAIN_THREAD);
		    			if (!threads.get(selectedThread).isBusy()){
		    				if(!threads.get(selectedThread).t.equals(Thread.currentThread())){//If the assigned thread isn't busy we have to restart it
		    					try {
		    						if (VERBOSE){
		    							System.out.println("Main thread joining (at this point) thread:"+selectedThread);
		    						}
		    						threads.get(selectedThread).t.join(); /**So as not to cancel the thread we join it. 
		    						* If the thread was not busy, this shouldn't be a problem.
		    						* */
		    					}
		   						catch (InterruptedException e) {
		   							System.out.println("Main thread Interrupted");
		   						}
		   					}
		    				/**Now we start a new thread with the same hostnames as the previous
		    				thread, but assigned to the current set of nextUrls.*/

		    				threads.get(selectedThread).t= new Thread (threads.get(selectedThread));	
		    				threads.get(selectedThread).t.start();
			    			if (VERBOSE)
	    						System.out.println("Selected Thread Restarted (at this point): "+selectedThread);

	    				}
	    				/**If the selected thread was busy, then it will find the hostname in it's list and process the corresponding pages.*/	
		    		}
		    	}
		    	
		    	nextUrls2.clear();
		    	
		    	//Now the main thread must process the tentativeUrls, adding those that are valid, to the nextUrls list.
		    	List<ItemUrl> tentativeUrls2=new ArrayList<ItemUrl>();
		    	synchronized (tentativeUrls_lock){
		    		tentativeUrls2.addAll(tentativeUrls);
		    		tentativeUrls.clear();
		    	}
		    	List<ItemUrl> tentativeValidUrls= new ArrayList<ItemUrl>();;
		    	for (ItemUrl url: tentativeUrls2){
		    			if (isValid(url.getUrl())){//Step (4)
		    				tentativeValidUrls.add(url); 
		    			}
		    	}
		    	tentativeUrls2.clear();
		    	
		    	/**Now the main thread checks if among the tentativeValidUrls there are repeated URLs. 
		    	*  If so, it will only allow the one with the lowest depth. Step(1)
		    	**/
		    	tentativeUrls2.addAll(cleanUrlList(tentativeValidUrls));//Step (1)
		    	
		    	/**Now the main thread checks for excluded URLs in the tentative list
		    	 */
				/** Now we remove the excluded URLs from the seeds list (Step(5))
				 */
		    	tentativeValidUrls.clear();
				synchronized (excludedUrls_lock){
					tentativeValidUrls.addAll(removeExcluded(tentativeUrls2, excludedUrls));
				}
				tentativeUrls2.clear();
				tentativeUrls2.addAll(tentativeValidUrls);
				
		    	nextUrls2.addAll(tentativeUrls2);

		    	List<URL> visitedUrls2 = new ArrayList<URL> ();
		    	synchronized (visitedUrls_lock){
		    		visitedUrls2.addAll(visitedUrls);
		    	}
		    	synchronized (nextUrls_lock){
		    		List <ItemUrl> auxList = new ArrayList<ItemUrl>();
		    		auxList.addAll(nextUrls);
		    		auxList.addAll(nextUrls2); //tentative URLs
		    		nextUrls.clear();
		    		nextUrls.addAll(cleanUrlList(auxList));//From repeated URLs, only the one with lower depth is kept while merging tentative URLs and nextURLs (Step (1))
		    		auxList.clear();
		    		auxList.addAll(nextUrls);
		    		nextUrls.clear();
		    		nextUrls.addAll(removeVisited(auxList, visitedUrls2));//Step (2)
		    		nue=nextUrls.isEmpty();		    		//Update of flags
		    	}
		    	synchronized (tentativeUrls_lock){
		    		tue=tentativeUrls.isEmpty();//Update of flags
		    	}
		    	tb=threadsBusy(); //Update of flags
		    	
	    		if (DEBUG_MODE){
	    			System.out.println("Main thread is still alive!: Stopping flags:"+!tue+" "+!nue+" "+tb);
	    			if (tue && nue && tb){
	    				System.out.println("At this point Main thread is only waiting for threads to finish.");
	    				System.out.println("Threads Status:");
	    				for (int i=0; i<threads.size(); i++){
	    					System.out.println("Thread "+i+" isBusy="+threads.get(i).isBusy());
	    				}
	    			}
	    		}
		    	if(!tue || !nue || tb){//If the while loop will continue...
		    		try {
						Thread.sleep(WAIT_TIME_IN_MILLISECONDS_MAIN_THREAD); //Wait while everyone else does the work.
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
		    		if (DEBUG_MODE){
		    			System.out.println("Main thread Slept 1 second. Stopping flags:"+!tue+" "+!nue+" "+tb);
		    		}
		    	}

		    	//The flags are updated after the short nap.
		    	synchronized (nextUrls_lock){
		    		nue=nextUrls.isEmpty();
		    	}
		    	synchronized (tentativeUrls_lock){
		    		tue=tentativeUrls.isEmpty();
		    	}
		    	tb=threadsBusy();
		    }
		    //End of while loop
		    
			if (VERBOSE){
    			System.out.println("Main thread finished crawling. Next step: joining with helper threads.");
    		}
			
		    /** Just to be sure, the Main thread will now join all helper threads.
			*/
			for (int i=0; i<threads.size(); i++){
				if (!threads.get(i).t.equals(Thread.currentThread())){
		    		if (VERBOSE)
		    			System.out.println("Main thread joined with Thread: "+i);
		    		threads.get(i).t.join();
		    	}
		    }
		    /**End of parallelized execution*/
		    
			
			/**Now we store the visited URLs*/
		    PrintWriter writer;
			String prefix="";
			try {
				if (!usingNonDefaultIndex){
					prefix=DEFAULT_INDEX_FOLDER;
				}
				else{
					prefix=indexFolderAddress;
				}
				List<URL> auxList = new ArrayList<URL>();
				auxList.addAll(visitedUrls);
				visitedUrls.clear();
				visitedUrls.addAll(removeRepeated(auxList));
				FileOutputStream writer2 =  new FileOutputStream(prefix+"/"+VISITED_FILE);
				writer2.write((new String("")).getBytes());
				writer2.close();
				writer = new PrintWriter(new File (prefix+"/"+VISITED_FILE), "UTF-8");
				
				for (int s=0; s<visitedUrls.size(); s++){
		    		writer.println(visitedUrls.get(s).toString());
		    	}
				writer.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			/**Now we store the excluded URLs*/
			try {
				if (!usingNonDefaultIndex){
					prefix=DEFAULT_INDEX_FOLDER;
				}
				else{
					prefix=indexFolderAddress;
				}

				List<URL> auxList = new ArrayList<URL>();
				auxList.addAll(excludedUrls);
				excludedUrls.clear();
				excludedUrls.addAll(removeRepeated(auxList));
				FileOutputStream writer2 =  new FileOutputStream(prefix+"/"+EXCLUDED_FILE);
				writer2.write((new String("")).getBytes());
				writer2.close();
				writer = new PrintWriter(new File (prefix+"/"+EXCLUDED_FILE), "UTF-8");
				for (int s=0; s<excludedUrls.size(); s++){
		    		writer.println(excludedUrls.get(s).toString());
		    	}
				writer.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			if (VERBOSE){
				System.out.println("********************************************");
				System.out.println("Crawling completed successfully.");
				System.out.println("********************************************");
				List <URL> newlyVisitedUrls = new ArrayList<URL>();
				newlyVisitedUrls.addAll(visitedUrls);
				newlyVisitedUrls.removeAll(previouslyVisitedUrls);
				
				List <URL> newlyExcludedUrls = new ArrayList<URL>();
				newlyExcludedUrls.addAll(excludedUrls);
				newlyExcludedUrls.removeAll(previouslyExcludedUrls);

				
				if (newlyVisitedUrls.isEmpty()){
					System.out.println("Newly visited URLs: None.");
				}
				else{
					System.out.println("********************************************");
					System.out.println("Newly visited URLs: ");
					
					/**This extra processing is to print them alphabetically */
					
					String [] list2 = new String [newlyVisitedUrls.size()];
					int i=0;
					for (URL url: newlyVisitedUrls){
						list2[i]=url.toString();
						i++;
					}
					Arrays.sort(list2);
					for (int j=0; j<newlyVisitedUrls.size(); j++){
						System.out.println(list2[j]);
					}
					System.out.println("********************************************");
				}
				
				if (newlyExcludedUrls.isEmpty()){
					System.out.println("Newly excluded URLs: None.");
				}
				else{
					System.out.println("********************************************");
					System.out.println("Newly excluded URLs: ");
					
					/**This extra processing is to print them alphabetically */
					
					String [] list2 = new String [newlyExcludedUrls.size()];
					int i=0;
					for (URL url: newlyExcludedUrls){
						list2[i]=url.toString();
						i++;
					}
					Arrays.sort(list2);
					for (int j=0; j<newlyExcludedUrls.size(); j++){
						System.out.println(list2[j]);
					}
					System.out.println("********************************************");
				}
				
				System.out.println("********************************************");
				System.out.println("Crawling completed successfully.");
				System.out.println("********************************************");

		    }
		}
		else {
			if (VERBOSE){
				System.out.println("********************************************");
				System.out.println("Crawling not carried out: No new + valid URLs passed as seeds. Try with another seeds.");
				System.out.println("If not certain about this message: ");
				System.out.println("Please check if your submitted URLs are well-formed, have already been visited or have been excluded by visited servers.");
				System.out.println("********************************************");
			}
		}
		isCrawling=false;
	}

	/**
	 * Function to assert if crawler is busy crawling
	 * 
	 * @return true if crawler is busy, otherwise false. 
	 * @author Gabriel
	 * 
	 */
	public boolean isCrawling (){
		return isCrawling;
	}
	
	
	/**
	 * Function to get the location of the current index
	 * 
	 * @return string with current index
	 * @author Gabriel
	 * 
	 * */
	public String getCurrentIndex (){
		if (this.usingNonDefaultIndex){
			return currentIndexFolder;
		}
		return DEFAULT_INDEX_FOLDER;
	}
	

	/**
	 * Function that allows to set the location for a new index
	 * Can pass "DEFAULT" to set it to default location.
	 * As shipped the default location is "default_index".
	 * 
	 * @param index string with location of new index. Can pass "DEFAULT" to set it to default location.
	 * As shipped the default location is "default_index".
	 * 
	 * @author Gabriel	   
	 */
	public void setNewIndex(String index){
		if (index.equals("DEFAULT") || index.equals(DEFAULT_INDEX_FOLDER)){
			usingNonDefaultIndex=false;
			currentIndexFolder=DEFAULT_INDEX_FOLDER;
		}
		else {
			usingNonDefaultIndex=true;
			currentIndexFolder=index;
		}
	}
	
	/**
	 * Function to check if crawler is using a non-default index
	 * 
	 * @return true if crawler is using non-default index, otherwise false.
	 * 
	 * @author Gabriel	   
	 */
	public boolean getUsingNonDefaultIndex(){
		return usingNonDefaultIndex;
	}

	/**
	 * Function to get list of visited pages
	 * 
	 * @param address index location
	 * 
	 * @return array list of type string with list of visited pages in alphabetical order. This list can be empty.
	 * 
	 * @author Gabriel
	 */
	public List<String> getVisitedPages (String address){
		List<String> results=new ArrayList<String>();
		BufferedReader reader=null;  
		try {
			reader = new BufferedReader(new FileReader(address+"/"+VISITED_FILE));
			String line = null;
			while ((line = reader.readLine()) !=null){
				markAsVisited(new URL((line.toString()))); //The visited URLs file is read and loaded to the visited array.
			}
		}
		catch (Exception e) {
			e.printStackTrace();
			System.out.println("Visited URLs file not found");
			visitedUrls.clear();
			return results;
		} finally {
			try {
				reader.close();
			} catch (Exception e) {
			  e.printStackTrace();
			  visitedUrls.clear();
			  return results;
			}
		}		
		if (!visitedUrls.isEmpty()){
			/**This extra processing is to return them alphabetically */
				
			String [] list = new String [visitedUrls.size()];
			int i=0;
			for (URL url: visitedUrls){  
				list[i]=url.toString();
				i++;
			}
			Arrays.sort(list);
			for (int j=0; j<visitedUrls.size(); j++){
				results.add(list[j]);
			}	
		}
		visitedUrls.clear();
		return results;
	}

	/**
	 * Function to get list of excluded pages
	 * 
	 * @param address index location
	 *  
	 * @return array list of type string with list of excluded pages in alphabetical order. This list can be empty.
	 * 
	 * @author Gabriel
	 */
	public List<String> getExcludedPages (String address){
		List<String> results=new ArrayList<String>();
		BufferedReader reader=null;  
		excludedUrls.clear();
		try {
			reader = new BufferedReader(new FileReader(address+"/"+EXCLUDED_FILE));
			String line = null;
			while ((line = reader.readLine()) !=null){
				excludedUrls.add(new URL((line.toString()))); //The excluded URLs file is read and loaded to the excluded array.
			}
		}
		catch (Exception e) {
			e.printStackTrace();
			System.out.println("Excluded URLs file not found");
			excludedUrls.clear();
			return results;
		} finally {
			try {
				reader.close();
			} catch (Exception e) {
			  e.printStackTrace();
			  excludedUrls.clear();
			  return results;
			}
		}
		if (!excludedUrls.isEmpty()){
			/**This extra processing is to return them alphabetically */
				
			String [] list = new String [excludedUrls.size()];
			int i=0;
			for (URL url: excludedUrls){  
				list[i]=url.toString();
				i++;
			}
			Arrays.sort(list);
			for (int j=0; j<excludedUrls.size(); j++){
				results.add(list[j]);
			}	
		}
		excludedUrls.clear();
		return results;
	}
	
	
	/**
	 * Function to check if a URL has been marked as excluded
	 * 
	 * @author Gabriel
	 * @param pageLink url of page to check
	 * @return true if url appears in excluded list, otherwise false. 	   
	 */
	private boolean isExcluded (URL pageLink){
		boolean result=false;
		synchronized (excludedUrls_lock){

			for (int i=0; i<excludedUrls.size(); i++){
				if (pageLink.toString().startsWith(excludedUrls.get(i).toString()+"/") || pageLink.toString().trim().equals(excludedUrls.get(i).toString().trim())){
					result=true;
					i=excludedUrls.size();
				}
			}
		}
		return result;
	}
	
	/**
	 * Function to check if a URL is valid
	 * 
	 * At this point the function only returns true, but it's left as an easy alternative
	 * for embedding additional URL verifications, in the future.
	 * 
	 * @param pageLink url of page to check
	 * @return true if url is valid, false otherwise. As shipped, it is always true.  
	 */
	private boolean isValid (URL pageLink){
		return true;
	}
	
	/**Helper function that removes elements from one list if they exist in another list
	 * 
	 * @param list list from which elements will be removed
	 * @param blocked list of elements that should be removed from former list
	 * 
	 * @return array list of type URL, with cleaned-up urls. This list can be empty.
	 */
	private List<URL> removeIfExistsInOtherList(List<URL> list, List <URL> blocked){
		List<URL> results = new ArrayList<URL>();
		results.addAll(list);
		Set<Integer> removeList= new LinkedHashSet<Integer>(100);
		for (int i=0; i<results.size(); i++){
			for (URL url: blocked){
				if (url.toString().equals(results.get(i).toString())){
					removeList.add(i);
				}
			}
		}
		int removedSoFar=0;
		for (int i: removeList){
			results.remove(i-removedSoFar);//This handles the shift from previous removals.
			removedSoFar++;
		}
		return results;
	}

	/**Helper function that removes repeated URLs from a list
	 * 
	 * @param list list from which urls should be removed
	 * @return array list of type URL, with cleaned-up urls. This list can be empty.
	 */
	private List<URL> removeRepeated (List<URL> list){
		List<URL> results = new ArrayList<URL>();
		results.addAll(list);
		Set<Integer> removeList= new LinkedHashSet<Integer>(100); 
		for (int i=0; i<results.size(); i++){
			for (int j=i+1; j<results.size(); j++){
				if(results.get(i).toString().equals(results.get(j).toString())){
					removeList.add(j);
				}
			}
		}
		int removedSoFar=0;
		for (int i: removeList){
			if ((i-removedSoFar)>0){
				results.remove(i-removedSoFar);//This handles the shift from previous removals.
			}
			removedSoFar++;
		}
		return results;
	}
	
	/**
	 * Helper function that takes a list of ItemUrl and returns a list without repeated ItemURL.urls,
	 * storing only the one with lowest depth (as this has a higher coverage, causing more crawling).
	 * 
	 * @param list from which ItemUrls will be cleaned-up
	 * 
	 * @return array list of type ItemUrl, with cleaned-up urls. This list can be empty.
	 *  
	 */
	private List<ItemUrl> cleanUrlList (List<ItemUrl> list){
		List<ItemUrl> result = new ArrayList<ItemUrl>();
    	List<Integer> discardPositions = new ArrayList<Integer>();
    	for (int i=0; i<list.size(); i++){
    		if (!discardPositions.contains((Integer)i)){
    			List <Integer> testPositions = new ArrayList<Integer>();
    			testPositions.add(i);
    			String iurl= list.get(i).getUrl().toString();
    			for (int j=i+1; j<list.size(); j++){
    				if(iurl.equals(list.get(j).getUrl().toString())){
    					testPositions.add(j);
    				}
    			}
    			if(testPositions.size()==1){
    				result.add(list.get(i));
    			}
    			else{
    				discardPositions.addAll(testPositions);
    				ItemUrl selectedItem = new ItemUrl (list.get(testPositions.get(0)).getUrl(), list.get(testPositions.get(0)).getDepth());
    				for (int k=1; k<testPositions.size(); k++){
    					if(list.get(testPositions.get(k)).getDepth()<selectedItem.getDepth()){
    						selectedItem.setDepth(list.get(testPositions.get(k)).getDepth());
    					}
    				}
    				result.add(selectedItem);
    			}
    		}
    	}
    	return result;
    }

    	/**
    	 * Helper function that takes a list of nextUrls and returns a list with the same items, but
    	 * without visited URLs, according to the visitedUrls2 list passed as input.
    	 * 
    	 * @param nextUrls2
    	 * @param visitedUrls2
    	 * 
    	 * @return array list of type ItemUrl, with cleaned-up urls. This list can be empty.
    	 *  
    	 */
    	private List<ItemUrl> removeVisited (List<ItemUrl> nextUrls2, List<URL> visitedUrls2){
    		List <ItemUrl> result = new ArrayList<ItemUrl>();
        	for (int i=0; i<nextUrls2.size(); i++){
        		boolean wasVisited=false;
        		for (int j=0; j<visitedUrls2.size(); j++){
        			if (nextUrls2.get(i).getUrl().toString().equals(visitedUrls2.get(j).toString())){
        				wasVisited=true;
        				j=visitedUrls2.size();
        			}
        		}
        		if (!wasVisited){
        			result.add(nextUrls2.get(i));
        		}
        	}
			return result;
    	}
    	
    	/**
    	 * Helper function that takes a list of nextUrls and returns a list with the same items, but
    	 * without excluded URLs, according to the visitedUrls2 list passed as input.
    	 * 
    	 * @param nextUrls2
    	 * @param visitedUrls2
    	 * 
    	 * @return array list of type ItemUrl, with cleaned-up urls. This list can be empty.
    	 *  
    	 */
    	private List<ItemUrl> removeExcluded (List<ItemUrl> nextUrls2, List<URL> visitedUrls2){
    		List <ItemUrl> result = new ArrayList<ItemUrl>();
        	for (int i=0; i<nextUrls2.size(); i++){
        		boolean isExcluded=false;
        		for (int j=0; j<visitedUrls2.size(); j++){
        			if (nextUrls2.get(i).getUrl().toString().startsWith(visitedUrls2.get(j).toString()+"/") ||
        				nextUrls2.get(i).getUrl().toString().trim().equals(visitedUrls2.get(j).toString().trim()))
        			{
        				isExcluded=true;
        				j=visitedUrls2.size();
        			}
        		}
        		if (!isExcluded){
        			result.add(nextUrls2.get(i));
        		}
        	}
			return result;
    	}

	
	/**
	 * Function to check if a URL has been visited
	 * Note: Uses visitedUrls_lock
	 * Additionally we perform some special checks so as to prevent visiting the same page when it has different URLS.
	 * But given that this is a very site-specific issue, we only include our approach for stackoverflow.com. Something
	 * similar could be done in the future for more sites. Our approach for stackoverflow.com could also be improved.
	 * 
	 * @param pageLink
	 * 
	 * @return true if url has been visited, false otherwise.
	 *  
	 */
	private boolean isVisited (URL pageLink){
		boolean result=false;
		if (!pageLink.toString().isEmpty()){
			boolean doStackoverflowCheck= pageLink.toString().contains("http://stackoverflow.com/questions/");
			synchronized (visitedUrls_lock){
				for (int i=0; i<visitedUrls.size(); i++){
					if (visitedUrls.get(i).toString().equals(pageLink.toString())){
						result= true;
						i=visitedUrls.size();
						break;
					}
					else if (doStackoverflowCheck && visitedUrls.get(i).toString().contains("http://stackoverflow.com/questions/")){
						/*The stackoverflow check consists of asserting if what follows the ...questions/ is a number for both,
						 * and then check if one string is contained in the other*/
						String beginningPL = pageLink.toString().split("stackoverflow.com/questions/")[1];
						String beginningVU = visitedUrls.get(i).toString().split("stackoverflow.com/questions/")[1];
						beginningPL=beginningPL.split("/")[0];
						beginningVU=beginningVU.split("/")[0];
						boolean isNumberPL=true;
						boolean isNumberVU=true;
						try {  
							@SuppressWarnings("unused")
							double d = Double.parseDouble(beginningPL);  
						}  
						catch(NumberFormatException nfe){  
							isNumberPL=false;  
						}
						try {  
							@SuppressWarnings("unused")
							double d2 = Double.parseDouble(beginningVU);  
						}  
						catch(NumberFormatException nfe){  
							isNumberVU=false;  
						}
						if(isNumberPL&&isNumberVU){
							if (pageLink.toString().contains(visitedUrls.get(i).toString()) ||
								visitedUrls.get(i).toString().contains(pageLink.toString())	){
								result= true;
								i=visitedUrls.size();
								break;
							}
						}
					}
				}

			}
		}
		return result;
	}
	
	/**
	 * Function to check if a URLs host has been visited. This is used for not visiting a host several times while chekcing for robots.txt.
	 * Note: Uses visitedUrls_lock
	 * 
	 * @param pageLink
	 * 
	 * @return true if the host has has been visited, false otherwise.
	 */
	private boolean isVisitedHost (URL pageLink){
		boolean result=false;
		if (!pageLink.toString().isEmpty()){
			synchronized (visitedUrls_lock){
				for (int i=0; i<visitedUrls.size(); i++){
					if ((visitedUrls.get(i).getProtocol()+visitedUrls.get(i).getHost().toString()).equals(pageLink.getProtocol()+pageLink.getHost().toString())){
						result=true;
						i=visitedUrls.size();
						break;
					}
				}
			}
		}
		return result;
	}
	
	/**
	 * Function to mark a URL as visited, using locks
	 * Note: Uses visitedUrls_lock
	 * 
	 * @param pageLink
	 *  
	 */
	private void markAsVisited (URL pageLink){
		synchronized (visitedUrls_lock){
			boolean mark=true;
			for (int i=0; i<visitedUrls.size(); i++){
				if (visitedUrls.get(i).toString().equals(pageLink.toString())){
					mark=false;
					i=visitedUrls.size();
				}
			}
			if (mark){
				visitedUrls.add(pageLink);
			}
		}
	}

	/**
	 * Function to retrieve the position of the first free thread, if any
	 * Note: Uses isBusy_lock from all existing threads.
	 * 
	 *  @return position of free thread, -1 if all are busy or threads array is empty.
	 */
	private int getFreeThread(){
		for (int i= 0; i<threads.size(); i++){
			if (!threads.get(i).isBusy())
				return i;
		}
		return -1;
	}
	
	/**
	 * Function similar to getFreeThread. Checks if there is at least one busy thread.
	 * 
	 * Note: Uses isBusy_lock from all existing threads.
	 * 
	 *  @return true if one thread is busy, false if all are free or threads array is empty.
	 */
	private boolean threadsBusy (){
		for (CrawlerThread thread: threads){
			if (thread.isBusy())
				return true;
		}
		return false;
	}

	/**Nested class */
	/**
	 * 
	 * CrawlerThread class implementing Runnable. It includes the crawling functionality for a single thread.
	 * <p>
	 * It was declared as a nested class for ease in the use of common & synchronized variables with the outer class.
	 * 
	 * @author Gabriel, Rene
	 * 
	 */
	private class CrawlerThread implements Runnable {
		/**Public class members */

		/**The id of a thread (position in threads array of WebCrawler)*/
		public int id;
		/**The thread itself.*/
		public Thread t;
		
		/**Private class members */
		
		/**isBusy flag, initially false*/
		private boolean isBusy=false;
		/**Lock for synchronized access to isBusy flag (since the main thread/crawler and the current thread can ask for it)*/
		private Object isBusy_lock = new int[1];
		
		/**List array holding the hostnames assigned to this thread. Local copy, additional to the index in WebCrawler class*/
		private List <String> hostnames = new ArrayList<String>(); //All the hostnames the thread is responsible for crawling.
		/**Lock for synchronized access to hostnames (since the main thread/crawler and the current thread can ask for it)*/
		private Object hostnames_lock = new int[1];
		
		/**Functions */
		/**
		 * Parametric constructor
		 * 
		 *  @param id of the thread. It should be the position of the thread in the threads array of the WebCrawler.
		 */		
		CrawlerThread(int id){
			this.id=id;
		}
		
		/**
		 * Function in charge of the crawling
		 *  
		 */
		public void run(){
			//First the thread signals that it is busy
			synchronized (isBusy_lock){
				isBusy=true;
			}
			
			boolean somethingLeftToCrawl=true;
			
			while (somethingLeftToCrawl){
				boolean noVisitDuringIteration=true;
				if (DEBUG_MODE){
					System.out.println("Thread "+id+ " entering while loop.");
				}
				
				List<String> hostnames2= new ArrayList<String>();
				List<ItemUrl> nextUrls2= new ArrayList<ItemUrl>();
				synchronized (hostnames_lock){
					hostnames2.addAll(hostnames); //We work with local copies so as to improve synchronization
				}	
				
				/**The thread will iterate over all its assigned hostnames, and for each one
				 * it will select an URL, if possible. To index or crawlAndIndex, according to 
				 * the depth. Then, it will sleep 5 seconds (so as to avoid DoS exceptions).*/
				
				for (String host: hostnames2){
					int nextUrl=-1;
					nextUrls2.clear();
					synchronized (nextUrls_lock){
						nextUrls2.addAll(nextUrls); //We work with local copies so as to improve synchronization
					}
					
					/** Here we find a URL pending to visit of the specific host*/
					for (int i=0; i<nextUrls2.size(); i++){
						if (nextUrls2.get(i).getUrl().getHost().equals(host)){
							if (nextUrl==-1){
								nextUrl=i;
								i=nextUrls2.size();
							}
						}
					}
					
					if (nextUrl!=-1){//An URL was found
						URL toVisit=null;
						try {
							toVisit = new URL (normalize(nextUrls2.get(nextUrl).getUrl().toString())); //Step 3
						} catch (MalformedURLException e2) {
							// TODO Auto-generated catch block
							e2.printStackTrace();
							/**In the case of an exception of the thread, we have still to remove the URL, and mark the thread as busy*/
							synchronized (nextUrls_lock){
								nextUrls.remove(nextUrls2.get(nextUrl)); 
							}
							synchronized (isBusy_lock){
								isBusy=false;
							}
						}
						ItemUrl url=new ItemUrl(toVisit, nextUrls2.get(nextUrl).getDepth());
						if (isVisited(toVisit)){//Step (2) Perhaps redundant, but its valid and perhaps wise, to check on an updated visitedURLs list...
							if (VERBOSE){
								System.out.println("Thread: "+id+" attempted to re-visit: "+toVisit.toString()+" but it was detected.");
							}
							/** If already visited, we can remove from the nextUrls lists (local and global)*/
							synchronized (nextUrls_lock){
								nextUrls.remove(nextUrls2.get(nextUrl));
							}
							nextUrls2.remove(nextUrl);
						}
						else{ 
							/**We can visit, Steps 1 to 5 have already been checked. 
							 * 1-3 and 5 over the nextUrls list in Main thread, 2 and 4 in this thread, and 4 also when loading outlinks.
							 */
							
							noVisitDuringIteration=false;
							boolean wasExcluded=false;
							
							if (url.getDepth()+1<=maxCrawlDepth){ //We crawl and index
								List<URL> results=new ArrayList<URL>();
								try {
									//Before indexing we will get the excluded list of the host
									if(!isVisitedHost(url.getUrl())){
										results = getExcludedList(url.getUrl());
										if (!results.isEmpty()){
											synchronized (excludedUrls_lock){
													excludedUrls.addAll(results);
													results.clear();
													results.addAll(removeRepeated(excludedUrls));
													excludedUrls.clear();
													excludedUrls.addAll(results);
											}
										}
									}
									if (!isExcluded(url.getUrl())){
										try{
											results=crawlAndIndexPage(url.getUrl(), false);
										} catch (Exception e){
										    synchronized (nextUrls_lock){
												nextUrls.remove(nextUrls2.get(nextUrl)); 
											}
											synchronized (isBusy_lock){
												isBusy=false;
											}
										}
									}
									else{synchronized (nextUrls_lock){
										nextUrls.remove(nextUrls2.get(nextUrl)); 
									}
									synchronized (isBusy_lock){
										isBusy=false;
									}
										if (VERBOSE){
											System.out.println("Before crawling: "+url.getUrl().toString()+" was excluded.");
										}
										wasExcluded=true;
									}
								} catch (Exception e1){
									e1.printStackTrace();
									/**In the case of an exception of the thread, we have still to remove the URL, and mark the thread as busy*/
									synchronized (nextUrls_lock){
										nextUrls.remove(nextUrls2.get(nextUrl)); 
									}
									synchronized (isBusy_lock){
										isBusy=false;
									}
								}
								synchronized (tentativeUrls_lock){
									for (URL result: results){
										tentativeUrls.add(new ItemUrl(result, url.getDepth()+1));//We add the results to the tentativeUrls list.
									}
								}
								if (!wasExcluded)
									markAsVisited(url.getUrl()); //We mark as visited
							}//End of crawl and index
							else if (url.getDepth()==maxCrawlDepth){ //We can only index
								org.jsoup.nodes.Document doc=null;
								String startUrl=url.getUrl().toString();
								try {
									//Before indexing we will get the excluded list of the host
									if(!isVisitedHost(url.getUrl())){
										List<URL> results = getExcludedList(url.getUrl());
										if (!results.isEmpty()){
											synchronized (excludedUrls_lock){
												excludedUrls.addAll(results);
												results.clear();
												results.addAll(removeRepeated(excludedUrls));
												excludedUrls.clear();
												excludedUrls.addAll(results);
											}
										}
									}
									if(!isExcluded(url.getUrl())){
										boolean connectionWasOk=true;
										try{
											doc = Jsoup.connect(startUrl).userAgent(USER_AGENT).referrer(REFERRER).get();
										} catch (Exception e){ //Mostly socket time-out
											synchronized (nextUrls_lock){
												nextUrls.remove(nextUrls2.get(nextUrl)); 
											}
											synchronized (isBusy_lock){
												isBusy=false;
											}
											connectionWasOk=false;
										}
										if (connectionWasOk){
											if (VERBOSE)
												System.out.println("Note:- Only indexing for url:"+ normalize(doc.location())+" "+id);
											try{
												indexPage(doc, new URL (normalize(doc.location())), false);}
											catch (Exception e){
												synchronized (nextUrls_lock){
													nextUrls.remove(nextUrls2.get(nextUrl)); 
												}
												synchronized (isBusy_lock){
													isBusy=false;
												}
											}
											/**The following while loop tries to catch redirects and mark as visited all the intermediate URLs
											 * It checks if the location of the retrieved document is the same as the one from the 
											 * requested URL.*/
								
											while(!normalize(doc.location()).equals(startUrl)){			
												Response response=null;
												try {
													String aux=startUrl;
													response = Jsoup.connect(startUrl).userAgent(USER_AGENT).referrer(REFERRER).followRedirects(false).execute();
													startUrl=response.header("location");
													if(aux==null || startUrl==null){//If the response has no header or fails, we stop the loop
														startUrl=normalize(doc.location());
													}
													else if(aux==startUrl){//If the response traps the request in a single site, we stop the loop
														startUrl=normalize(doc.location());
													}
													else {
														startUrl=normalize(startUrl, aux);
													try {
														URL tentativeURL=new URL(startUrl);
														markAsVisited(tentativeURL);//Note we mark all URLs as visited, except for the starting one, which is marked later.
													} catch (MalformedURLException e) {
														// TODO Auto-generated catch block
														e.printStackTrace();
														/**In the case of an exception of the thread, we have still to remove the URL, and mark the thread as busy*/
														synchronized (nextUrls_lock){
															nextUrls.remove(nextUrls2.get(nextUrl)); 
														}
														synchronized (isBusy_lock){
															isBusy=false;
														}
													}
												}
												} catch (Exception e) {
												// TODO Auto-generated catch block
													e.printStackTrace();
													startUrl=normalize(doc.location());
													synchronized (nextUrls_lock){
														nextUrls.remove(nextUrls2.get(nextUrl));
													}
													synchronized (isBusy_lock){
														isBusy=false;
													}
												}
											}//End of while
									}
										else{
											System.out.println("Connection timed-out when indexing: "+url.getUrl().toString()+", so it was not indexed.");
										}
									}
									else{
										if (VERBOSE){
											System.out.println("During indexing: "+url.getUrl().toString()+" was excluded.");
										}
										wasExcluded=true;
									}
								} catch (Exception e1) {
								// TODO Auto-generated catch block		
									e1.printStackTrace();
									synchronized (nextUrls_lock){
										nextUrls.remove(nextUrls2.get(nextUrl));
									}
									synchronized (isBusy_lock){
										isBusy=false;
									}
								}
								if (!wasExcluded)
									markAsVisited(url.getUrl());//Here we mark as visited the starting URL.
							}//End of else if
							synchronized (nextUrls_lock){
								nextUrls.remove(nextUrls2.get(nextUrl));
								if (VERBOSE){
									System.out.println("Remaining urls to visit= "+nextUrls.size()+ ". As seen from thread: "+id);
								}
							}
							nextUrls2.remove(nextUrl);
						}
					}//End of if URL found
					
					/**...
					 * 
					 * If no URL found for an assigned hostname, we do nothing...
					 * 
					 * */
					
				}//End of for loop over hostnames
				
				/**Now we must check if there is something left to crawl*/
				somethingLeftToCrawl=false;

				nextUrls2.clear();
				synchronized (nextUrls_lock){
					nextUrls2.addAll(nextUrls); //We make a local copy of the current nextUrls.
				}
				
				hostnames2.clear();
				synchronized (hostnames_lock){
					hostnames2.addAll(hostnames); //We make a local copy of the current assigned hostnames
				}
				
				//Loop for checking if at least a URL left to crawl belongs to one of the assigned hostnames
				for (String host: hostnames2){
					if (!somethingLeftToCrawl){
						for (int i=0; i<nextUrls2.size(); i++){
							if (nextUrls2.get(i).getUrl().getHost().toString().equals(host)){
								somethingLeftToCrawl=true;
								i=nextUrls2.size();
							}
						}
					}
				}
				
				if (somethingLeftToCrawl && !noVisitDuringIteration){
					/**Note that the noVisitDuringIteration flag allows the thread to not sleep if 
					 * it didn't visit any host after a loop.*/
					try {
						Thread.sleep(WAIT_TIME_IN_MILLISECONDS_THREADS);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
						synchronized (isBusy_lock){
							isBusy=false;
						}
					}
				} 
			}//End of while
			if (VERBOSE){
				System.out.println("Thread: "+id+ " has concluded its work.");
			}
			synchronized (isBusy_lock){
				isBusy=false;
			}
		}
		
		/**
		 * Function in charge of indexing a page
		 * 
		 * IndexPage creates an index entry for a given URL. Each document added by this function contains the URL, the title, the content, the code,
		 * and a summary of the current web page (which acts as a back-up when no highlights are possible).
		 * 
		 * The code and title are boosted by 2.0 and 1.5 respectively. The code is only boosted if it has code.
		 * A programming language field was added and boosted by 2.0. It detects if the content contains the words java, c++ or others. 
		 * The programming language detection could be improved.
		 *
		 *  @param doc
		 *  @param pageLink
		 *  @param createIndex if true, the index must be created, if false, it must be loaded from pre-selected directory.
		 *  
		 */
		@SuppressWarnings("deprecation")
		private void indexPage (org.jsoup.nodes.Document doc, URL pageLink, boolean createIndex){			
			
				if(!isVisited(pageLink) && doc.toString().length()>MINIMUM_DOC_LENGTH_FOR_INDEXING){
				/**
			 	* We declare the lucene variables for indexing.
			 	*/
			
				Analyzer analyzer = new StandardAnalyzer(Version.LUCENE_4_10_0);
				
			
				String codeString="";
				String codeSeparator=" ... ";
				/**
			 	* Here, we extract just the part of the html code which contain programming code.
			 	*/	
				Elements codesAsElement = doc.select("code");
	    		for (Element code : codesAsElement) {
	    			codeString+=code.toString().replace("<code>", "").replace("</code>","")+codeSeparator;
	    		}
	    		Elements codesAsClass = doc.getAllElements();
	    		for (Element code : codesAsClass) {
	    			if (code.hasClass("code")){
	    				for (Element code2: code.getElementsByClass("code")){
	    						codeString+=code2.ownText().toString()+codeSeparator;
	    				}
	    			}
	    		}
	    		
				
			
				/**
			 	* A Lucene Document variable is declared.
				*/	
				Document luceneDoc= new Document();
			
				/**
			 	* Here, we start adding fields to the indexer. We also create the variable summariser, which gives us
				* a small summary (in this case two sentences) of the web page.
				*/	
				TextField field1=new TextField("title", doc.title(), Field.Store.YES);
				field1.setBoost((float)1.5);
				luceneDoc.add(field1);
				Field field2=new Field ("url", pageLink.toString(), Field.Store.YES, Field.Index.NOT_ANALYZED, Field.TermVector.NO);
				field2.setBoost((float)1.0);
				luceneDoc.add(field2);
				TextField field3= new TextField("code", codeString, Field.Store.YES);
				if(codeString.length()>2){
					field3.setBoost((float)2.0); //We boost the field.			
				}
				luceneDoc.add(field3);
				String contentString=doc.toString();
				TextField field4=new TextField("content", contentString, Field.Store.YES);
				field4.setBoost((float)1.0);
				luceneDoc.add(field4);
				
				String pl= "";
				contentString=contentString.toLowerCase();
				if (codeString.toLowerCase().contains("javascript")){//Since this is a common language used in html forms, we dont search for it in the content.
					pl="javascript";
				}else if (codeString.toLowerCase().contains("java")||contentString.contains("java")){
					pl="java";
				}
				else if (codeString.toLowerCase().contains("c++")||contentString.contains("c++")){
					pl="c++";
				}
				else if (codeString.toLowerCase().contains("c#")||contentString.contains("c#")){
					pl="c#";
				}
				else if (codeString.toLowerCase().contains("ruby")||contentString.contains("ruby")){
					pl="ruby";
				}
				else if (codeString.toLowerCase().contains(" scala ")||contentString.contains(" scala ")){
					pl="scala";
				}
				else if (codeString.toLowerCase().contains("python")||contentString.contains("python")){
					pl="python";
				}
				else if (codeString.toLowerCase().contains("sql")||contentString.contains("sql")){
					pl="sql";
				}
				else if (codeString.toLowerCase().contains("assembly")||contentString.contains("assembly")){
					pl="assembly";
				}
				else if (codeString.toLowerCase().contains("pascal")||contentString.contains("pascal")){
					pl="pascal";
				}
				else if (codeString.toLowerCase().contains("fortran")||contentString.contains("fortran")){
					pl="fortran";
				}
				else if (codeString.toLowerCase().contains("php")){//Since this is a common language used in html forms, we dont search for it in the content.
					pl="php";
				}
				else if (codeString.toLowerCase().contains("cuda")||contentString.contains("cuda")){
					pl="cuda";
				}
				else if (codeString.toLowerCase().contains("latex")||contentString.contains("latex")){
					pl="latex";
				}
				else if (codeString.toLowerCase().contains("matlab")||contentString.contains("matlab")){
					pl="matlab";
				}
				else if (codeString.toLowerCase().contains("opencl")||contentString.contains("opencl")){
					pl="opencl";
				}
				else if (codeString.toLowerCase().contains("octave")||contentString.contains("octave")){
					pl="octave";
				}
				TextField field5=new TextField("programming_language", pl, Field.Store.YES);
				if (pl.length()>2){
					field5.setBoost((float)1.5);
				}
				luceneDoc.add(field5);
				
			
				ISummariser summariser= new SimpleSummariser();
				String textForSummary="";
				Elements bodyAux  = doc.body().getAllElements();
		        for (int k=0; k<bodyAux.size(); k++){
    				textForSummary+=bodyAux.get(k).ownText();
		        }
		        if(textForSummary.length()<2){
		        	textForSummary=doc.body().text();
		        }
				String summary=summariser.summarise(textForSummary, 2);
				Field field6 = new Field("summary", summary, Field.Store.YES, Field.Index.NOT_ANALYZED, Field.TermVector.NO);
				field6.setBoost(0);//The summary is only a back-up and not to be used for searching.
				luceneDoc.add(field6);
			
				String indexFolder="";
				if (usingNonDefaultIndex){
					indexFolder=currentIndexFolder;
				}
				else{
					indexFolder=DEFAULT_INDEX_FOLDER;
				}

				
				synchronized (index_lock){ //We need the index lock to access the directory and index
									
					try {
						Directory indexDir=FSDirectory.open(new File(indexFolder));
						boolean timeOutException= true;
						int numAttempts=0;
						while (timeOutException){
							try {
								IndexWriterConfig iwc = new IndexWriterConfig(Version.LUCENE_4_10_0, analyzer);
								if (createIndex){
									iwc.setOpenMode(OpenMode.CREATE);	
								}
								else{ //Note: Here we assume that the Index exists. This has to be checked upon user input.					
									iwc.setOpenMode(OpenMode.CREATE_OR_APPEND);
								}
								IndexWriter writer = new IndexWriter(indexDir, iwc);
								if (createIndex){
									writer.addDocument(luceneDoc);
									writer.commit();
								}	
								else{
								//Here we can make a final check before adding the document: 
									writer.updateDocument(new Term("url", pageLink.toString()), luceneDoc);
									writer.commit();
								}
								writer.close();
								indexDir.close();
								timeOutException=false;
							} catch (org.apache.lucene.store.LockObtainFailedException e) {
								// TODO Auto-generated catch block
								//e.printStackTrace();
								numAttempts++;
								if (numAttempts>=2){
									System.out.println("Could not index page, due to problems with Lucene's implicit lock. Received "+numAttempts+" timeout messages from Lucene.");
									timeOutException=false;
								}
							} catch (IOException e2){
								e2.printStackTrace();
								timeOutException=false;
							}
							
						}

					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
			else {
				if (VERBOSE){
					System.out.println("Tried to index "+pageLink.toString()+" but was prevented at the last minute. Stackoverflow checks were used.");
				}	
			}
		}

		/**
		 * Function to crawl and index a webpage, returing a list of all links found
		 *  CrawlAndIndexPage extracts all the information for a given URL, then it calls the index function
		 *  and finally it returns a list of all the URLs that are found in the  web page.
		 *  
		 *  Note: Uses visitedUrls_lock
		 *  
		 * @param pageLink url
		 * @param createIndex Signals if an index has to be created or not.
		 * 
		 * @throws Exception
		 */
		private List<URL> crawlAndIndexPage(URL pageLink, boolean createIndex) throws Exception {
			Set<URL> urlsFound = new LinkedHashSet<URL>(); //List for URLs found in current page
		    org.jsoup.nodes.Document doc=null;
	    	String startUrl=pageLink.toString();
		    //First we retrieve the url passed as input
		    try {
				doc = Jsoup.connect(startUrl).userAgent(USER_AGENT).referrer(REFERRER).get();
				indexPage(doc, new URL (normalize(doc.location())), createIndex);//Here we index the page
			
				/**The following while loop tries to catch redirects and mark as visited all the intermediate URLs
				 * It checks if the location of the retrieved document is the same as the one from the 
				 * requested URL.*/


				while(!normalize(doc.location()).equals(startUrl)){
					String aux=startUrl;
					Response response=null;
					try {
						response = Jsoup.connect(startUrl).userAgent(USER_AGENT).referrer(REFERRER).followRedirects(false).execute();
						startUrl=response.header("location");
						if(aux==null || startUrl==null){//If the response has no header or fails, we stop the loop
							startUrl=normalize(doc.location());
						}
						else if(aux==startUrl){	//If the response traps the request in a single site, we stop the loop
							startUrl=normalize(doc.location());
						}
						else {
							startUrl=normalize(startUrl, aux);
							try {
								URL tentativeURL=new URL(startUrl);
								markAsVisited(tentativeURL); /**Note we mark all URLs as visited, except for the starting one, which is marked later.
								In this case, it happens outside of the crawlAndIndexPage function*/
							} catch (MalformedURLException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
						}
					} catch (Exception e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
						startUrl=normalize(doc.location());
					}
				}
			} catch (Exception e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			
		    //Explore outlinks	    
		    /**
			 * In this section, we just check and validate the URL's. 
			 */	
		    
		    Elements links = doc.select("a");
		    for (Element link : links) {
		    	String linkHref = link.attr("href");
		    	try {
	    			linkHref=normalize(linkHref, startUrl);
	    			urlsFound.add(new URL(linkHref));
	    		} catch (MalformedURLException e) {
	    			// TODO Auto-generated catch block
	    			e.printStackTrace();	        	 
	    		}
		    }
		    List<URL> results= new ArrayList<URL>();
		    results.addAll(urlsFound);
		    return results; 
		}
		
		/**
		 * Function that given a URL, tries to get the robots.txt file from its host, and 
		 * returns the excluded URLs as a URL list.
		 * 
		 * It implements a very simple parser for robots.txt
		 * 
		 * @param pageLink url from which the excluded list will be gathered
		 */
		 private List<URL> getExcludedList(URL pageLink){
			List<URL> results = new ArrayList<URL>();
			String baseLink=pageLink.getProtocol() + "://" + 
					 pageLink.getHost() + (pageLink.getPort() > -1 ? ":" + pageLink.getPort() : "");
			String robotsLink = baseLink +"/robots.txt";			 
			URL robotsURL=null;
			try {
				robotsURL = new URL (robotsLink);
			} catch (MalformedURLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			BufferedReader in=null;
			try {
				in = new BufferedReader(new InputStreamReader(robotsURL.openStream()));
			} catch (Exception e) {
				// TODO Auto-generated catch block
				System.out.println("Failed to load the excluded list: robots.txt from "+baseLink+", for page: "+pageLink.toString()+", will proceed without it.");
				return results;
			}
			if (in!=null){
				try {
					if (!in.ready()){
						return results;
					}
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			
				String line = null;
				try {
					boolean agentSeen=false;
					String tentativeURL;
					while((line = in.readLine()) != null) {
						if (line.contains("User-Agent: *")){
							agentSeen=true;
					 	}
					 	else if (agentSeen && line.contains("User-Agent:")){
					 		agentSeen=false;
					 		if(!results.isEmpty()){
								return removeRepeated(results);
							}
							return results;
					 	}
					 	else if (agentSeen && line.contains("Disallow: ")){
					 		line=line.replace("Disallow: ", "");
					 		if (!(line.contains("*")||line.contains("?")||line.contains("#"))){
					 		tentativeURL=normalize(baseLink+line.trim());
					 		if(!tentativeURL.equals(baseLink))
					 				results.add(new URL(tentativeURL));
					 		}
					 	}	
					}
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			if(!results.isEmpty()){
				return removeRepeated(results);
			}
			return results;
		 }
		
		/**
		 * Function to normalize a URL using its baseURL. 
		 * Can be called while crawling outlinks. It only checks for ?, $, #, javascript and blank spaces, 
		 * removing those characters as well as the substring that follows them. It also removes a trailing
		 * / and transforms the resulting string to URL, checking in this way for MalformedURLException.
		 * 
		 * @param taintedURL url to be corrected
		 * @param baseURL base url if known, should not be empty.
		 * 
		 * @author Rene
		 * @author Gabriel
		 * 
		 * @return normalized url
		 * 
		 * @throws MalformedURLException
		 * 
		 */
		private String normalize(String taintedURL, String baseURL) throws MalformedURLException  {
		    URL url = null;
		    if (taintedURL.contains("?")&& !taintedURL.startsWith("?")){
		    	taintedURL = taintedURL.split("\\?")[0];
		    }
			if(taintedURL.startsWith("?")){
				taintedURL="";
			}
		   
			if (taintedURL.contains("#") && !taintedURL.startsWith("#")){
			    	taintedURL = taintedURL.split("#")[0];
			}
			if(taintedURL.startsWith("#")){
				taintedURL="";
			}
			if (taintedURL.contains("javascript:") && !taintedURL.startsWith("javascript:")){
		    	taintedURL = taintedURL.split("javascript:")[0];
			}
			if(taintedURL.startsWith("javascript:")){
				taintedURL="";
			}
	        try { 
	        	URI uri=new URI(taintedURL);
	        	URI uribase = new URI(baseURL);
	        	if (uri.isAbsolute()){
	        		url = uri.normalize().toURL();	
	            }
	        	else{
	        		url= new URL(uribase.toURL(),taintedURL);
	        	}        
	        }
	        catch (URISyntaxException e) {
	            throw new MalformedURLException(e.getMessage());
	        }
	        
	        String path = url.getPath().replace("/$", "");
	             
	        String returnString = url.getProtocol() + "://" + url.getHost() + path ; 
	        
	        if (returnString.endsWith("/"))
	        	returnString=returnString.substring(0, returnString.length()-1);
	        if (returnString.contains("http://stackoverflow.com/questions/")){
	        	String trailingString= returnString.split("http://stackoverflow.com/questions/")[1];
	        	String chunks []= trailingString.split("/");
	        	if (chunks.length>=2){
	        		boolean isNumberPL=true;
					try {  
						@SuppressWarnings("unused")
						double d = Double.parseDouble(chunks[0]);  
					}  
					catch(NumberFormatException nfe){  
						isNumberPL=false;  
					}
					if(isNumberPL){
						returnString="http://stackoverflow.com/questions/";
	        			returnString+=chunks[0]+"/"+chunks[1];
					}
				}
	        }
	        return returnString;	
	    }
		
		/**
		 * Function to partially normalize a URL without using its baseURL
		 * Can be called while crawling outlinks. It only checks for ?, #, $, javascript and blank spaces, 
		 * removing those characters as well as the substring that follows them. It also removes a trailing
		 * / and transforms the resulting string to URL, checking in this way for MalformedURLException.
		 * <p>
		 * We add a site specific normalization for stackoverflow.com: URLS such as: http://stackoverflow.com/questions/number/description/answer_number
		 * are normalized to: http://stackoverflow.com/questions/number/description
		 * 
		 * @param input url as string
		 * 
		 * @author Rene
		 * @author Gabriel
		 * 
		 * @return normalized url
		 * 
		 * @throws MalformedURLException
		 *  */
		private String normalize(String input) {
		    String taintedURL=input;
		    if (taintedURL.contains("?")&& !taintedURL.startsWith("?")){
		    	taintedURL = taintedURL.split("\\?")[0];
		    }
		    if (taintedURL.contains(" ")){
		    	String parseArray[] = taintedURL.split(" ");
		    	for (int i=0; i<parseArray.length; i++){
		    		if(parseArray[i].length()>2){
		    			taintedURL=parseArray[i];
		    			i=parseArray.length;
		    		}
		    	}
		    }
			if(taintedURL.startsWith("?")){
				taintedURL="";
			}
		   
			if (taintedURL.contains("#") && !taintedURL.startsWith("#")){
			    	taintedURL = taintedURL.split("#")[0];
			}
			if(taintedURL.startsWith("#")){
				taintedURL="";
			}
			if (taintedURL.contains("javascript:") && !taintedURL.startsWith("javascript:")){
		    	taintedURL = taintedURL.split("javascript:")[0];
			}
			if(taintedURL.startsWith("javascript:")){
				taintedURL="";
			}
	        String returnString = taintedURL; 
	        if (returnString.endsWith("/"))
	        	returnString=returnString.substring(0, returnString.length()-1);
	        if (returnString.contains("http://stackoverflow.com/questions/")){
	        	String trailingString= returnString.split("http://stackoverflow.com/questions/")[1];
	        	String chunks []= trailingString.split("/");
	        	if (chunks.length>=2){
	        		boolean isNumberPL=true;
					try {  
						@SuppressWarnings("unused")
						double d = Double.parseDouble(chunks[0]);  
					}  
					catch(NumberFormatException nfe){  
						isNumberPL=false;  
					}
					if(isNumberPL){
						returnString="http://stackoverflow.com/questions/";
	        			returnString+=chunks[0]+"/"+chunks[1];
					}
	        	}
	        }
	        return returnString;	
	    }

		/**
		 * Function in charge of adding a host to the existing thread
		 * Note: It uses a lock for concurrent access to the hostnames list of the thread.
		 * 
		 * @param host
		 * 
		 * @author Gabriel 
		 */
		public void addHost(String host){
			synchronized (hostnames_lock){
				hostnames.add(host);
			}
		}
		
		
		/**
		 * Function to determine if a thread is busy. 
		 * Note: It uses locks for concurrent access to the isBusy field of the thread.
		 * @author Gabriel 
		 * 
		 */
		public boolean isBusy(){
			boolean result;
			synchronized (isBusy_lock){
				result=this.isBusy;
			}
			return result;
		}
	}
}
