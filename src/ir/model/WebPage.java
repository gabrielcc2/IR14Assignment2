package ir.model;

/**
 * 
 * A POJO to represent a webpage hit, according to what must be printed.
 * 
 * Additionally, each page contains the number of results of the query.
 * 
 * @author Dominik
 * @author Gabriel
 * 
 */

public class WebPage {

	Integer rank;
	String title;
	String url;
	String summary;
	String snippets;
	Float relevanceScore;
	Integer numOfResults;

	public WebPage(Integer rank, String title, String url, String summary, String snippets, Float relevanceScore, Integer numOfResults) {
		this.rank=rank;
		this.title=title;
		this.url = url;
		this.summary=summary;
		this.relevanceScore=relevanceScore;
		this.numOfResults=numOfResults;
		this.snippets=snippets;
	}

	public Integer getRank() {
		return rank;
	}

	public void setRank(Integer rank) {
		this.rank = rank;
	}
	
	public Integer getNumOfResults() {
		return numOfResults;
	}

	public void setNumOfResults(Integer numOfResults) {
		this.numOfResults = numOfResults;
	}
	
	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}
	
	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}
	
	public String getSummary() {
		return summary;
	}
	
	public void setSnippets(String snippets) {
		this.snippets = snippets;
	}
	
	public String getSnippets() {
		return snippets;
	}

	public void setSummary(String summary) {
		this.summary = summary;
	}
	
	public float getRelevanceScore() {
		return relevanceScore;
	}

	public void setRelevanceScore(float relevanceScore) {
		this.relevanceScore = relevanceScore;
	}
}
