package ir.model;

import java.net.URL;

/**
 * 
 * A POJO to keep together an URL with its depth
 * 
 * @author Gabriel
 * 
 */

public class ItemUrl {
	
	URL url; 
	int depth;
	
	public ItemUrl(URL url, int depth){
		this.url=url;
		this.depth=depth;
	}
	
	public URL getUrl() {
		return url;
	}
	
	public void setUrl(URL url) {
		this.url = url;
	}
	
	public int getDepth(){
		return depth;
	}
	
	public void setDepth(int depth) {
		this.depth = depth;
	}
}
