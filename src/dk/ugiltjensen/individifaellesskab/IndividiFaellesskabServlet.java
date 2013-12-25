package dk.ugiltjensen.individifaellesskab;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.List;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.input.SAXBuilder;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.FetchOptions;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.Query.SortDirection;
import com.google.appengine.api.datastore.Transaction;
import com.google.appengine.api.datastore.TransactionOptions;
import java.util.Date;
import java.util.logging.Logger;

@SuppressWarnings("serial")
public class IndividiFaellesskabServlet extends HttpServlet {
	private static final String toptext = "På denne side finder du to digte under udarbejdelse. Digtene bliver skrevet fortløbende af et kollektiv bestående af netjournalister hos dr.dk, tv2.dk, eb.dk, b.dk, bt.dk, jp.dk, information.dk og pol.dk. Digtene bliver redigeret af en robot. Robotten opsamler alle sidernes overskrifter, der starter med \"Vi\" og \"Jeg\". Disse overskrifter sætter den sammen til to digte. Digtene vil således blive længere, så længe de respektive sider bliver ved med at fodre robotten med overskrifter. Digtene opdateres hver time med de linjer, som de respektive nyhedskanaler har produceret i mellemtiden. Eneste redigeringsfrihed, robotten tager sig, er at fjerne alt hvad der måtte stå før et kolon, så vi'et og jeg'et bliver isoleret fra deres oprindelige afsendere og således kommer til at fremstå som fællessubjekter. Skulle man ønske at efterprøve linjernes autenticitet, så kan man holde musen over en linje for at se kilden. Vi takker den danske journaliststand for deres poetiske udladninger og glæder os til at følge deres fortsatte afsøgning af begreberne: individ og fællesskab.";	
	private static final String titeltext = "individ i fællesskab";
	private static final String bytext =  "- Christoffer Ugilt Jensen og Troels Ugilt Jensen";
    private static final Logger log = Logger.getLogger(IndividiFaellesskabServlet.class.getName());

	private List<Item> weItems;
	private List<Item> iItems;
	
	private Long lastUpdate;
	
	public IndividiFaellesskabServlet() throws IOException {
		weItems = new ArrayList<Item>();
		iItems = new ArrayList<Item>();

	    setupDatastore();

	    Date d = new Date();
		lastUpdate = d.getTime();
		updateLists();
	}
	
	public void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws IOException {
		String sReq = req.getRequestURI();
		if (sReq.endsWith("/recache")) {
			String cronHeader = req.getHeader("X-AppEngine-Cron");
			if (cronHeader == null || cronHeader != "true") {
				log.warning("recache invoked by non-cron");
				return;
			}
			getRecent();
			return;
		} else {
			Date d = new Date();
			if (d.getTime() - lastUpdate > 3600000) {
				lastUpdate = d.getTime();
				updateLists();
			}

			presentPage(resp);
		}
	}

	private void presentPage(HttpServletResponse resp) throws IOException {
		resp.setContentType("text/html");
		
		printLine(resp, "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Strict//EN\"");
        printLine(resp, "\"http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd\">");
		printLine(resp, "<html xmlns=\"http://www.w3.org/1999/xhtml\" lang=\"en\" xml:lang=\"en\">");
		printLine(resp, "<head>");
		printLine(resp, "<meta http-equiv=\"Content-Type\" content=\"Type=text/html; charset=ISO-8859-1\" />");
		printLine(resp, "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\" />");
		printLine(resp, "<meta name=\"robots\" content=\"index, follow, archive\" />");
		printLine(resp, "<meta name=\"description\" content=\"To digte. Avisernes overskrifter, der starter med 'Jeg' eller 'Vi', bliver samlet til to digte, der bliver længere og længere.\" />");
		printLine(resp, "<title>" + titeltext + "</title>");
		printLine(resp, "<link type=\"text/css\" rel=\"stylesheet\" href=\"/css/style.css\" />");
		printLine(resp, "<script type=\"text/javascript\">(function(i,s,o,g,r,a,m){i['GoogleAnalyticsObject']=r;i[r]=i[r]||function(){(i[r].q=i[r].q||[]).push(arguments)},i[r].l=1*new Date();a=s.createElement(o),m=s.getElementsByTagName(o)[0];a.async=1;a.src=g;m.parentNode.insertBefore(a,m)})(window,document,'script','//www.google-analytics.com/analytics.js','ga');ga('create', 'UA-45836934-1', 'ugiltjensen.appspot.com');ga('send', 'pageview');</script>");
		printLine(resp, "</head><body>");

		printLine(resp, "<div class=\"toptext\">");
		printLine(resp, toptext);
		printLine(resp, "<br/><br/><div class=\"bytext\">" + bytext + "</div><br/>");
		printLine(resp, "</div>");

		printLine(resp, "<div class=\"textfield\" >");
		for (Item it : weItems) {
			printEntry(resp, it);
		}
		printLine(resp, "</div>");

		printLine(resp, "<div class=\"textfield\" >");
		for (Item it : iItems) {
			printEntry(resp, it);
		}
		printLine(resp, "</div>");

		printLine(resp, "</body>");
		printLine(resp, "</html>");
	}
	
	private void updateLists() {
		weItems = new ArrayList<Item>();
		iItems = new ArrayList<Item>();
		List<String> weList = new ArrayList<String>();
		List<String> iList = new ArrayList<String>();
		
		DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
		
		Query query = new Query("We").addSort("entry", SortDirection.DESCENDING).addSort("title");
	    List<Entity> pq = datastore.prepare(query).asList(FetchOptions.Builder.withDefaults());
	    for (Entity e : pq) {
	    	String s = (String)e.getProperty("title");
	    	if (!weList.contains(s)) {
	    		weList.add(s);
	    		weItems.add(new Item(s, (String)e.getProperty("link"), (String)e.getProperty("date")));
	    	}
	    }
	    
		query = new Query("I").addSort("entry", SortDirection.DESCENDING).addSort("title");
	    pq = datastore.prepare(query).asList(FetchOptions.Builder.withDefaults());
	    for (Entity e : pq) {
	    	String s = (String)e.getProperty("title");
	    	if (!iList.contains(s)) {
	    		iList.add(s);
		    	iItems.add(new Item(s, (String)e.getProperty("link"), (String)e.getProperty("date")));
	    	}
	    }		
	}
	
	private void printLine(HttpServletResponse resp, String line) throws IOException {
		resp.getWriter().println(line);
	}
	
	private void printEntry(HttpServletResponse resp, Item item) throws IOException {
		resp.getWriter().println("<span class=\"line\" title=\"Kilde: "+ item.getLink()+"\">");
		resp.getWriter().println(item.getTitle());
		resp.getWriter().println("</span><br/>");
	}

	private void getRecent() throws IOException {
		// Build feed list
		List<String> feeds = new ArrayList<String>();
		BufferedReader br = new BufferedReader(new FileReader("feeds/feeds.txt"));
	    try {
	        String line = br.readLine();
	        while (line != null) {
	        	if (line.startsWith("http")) {
	        		feeds.add(line);
	        	}
	            line = br.readLine();
	        }
	    } catch (Exception e) {
	    	
	    } finally {
	        br.close();
	    }

	    List<Item> weAdds = new ArrayList<Item>();
	    List<Item> iAdds  = new ArrayList<Item>();
	    List<String> sWeAdds = new ArrayList<String>();
	    List<String> sIAdds = new ArrayList<String>();
	    
	    // Add new titles to lists and datastore
		for (String s : feeds) {
			Document d = getWebsiteXML(s);
			if (d == null)
				continue;
			Element root = d.getRootElement();
			Element channel = root.getChild("channel");
			List<Element> items = channel.getChildren("item");
		    
			for (Element e : items) {
				Element title = e.getChild("title");
				Element link = e.getChild("link");
				Element date = e.getChild("pubDate");
				String sTitle = "";
				if (title == null)
					continue;
				sTitle = title.getValue().trim();
				String sLink = "";
				if (link != null)
					sLink = link.getValue().trim();
				String sDate = "";
				if (sDate != null)
					sDate = date.getValue().trim();
				
				// Use only text after :
				if (sTitle.contains(":")) {
					String[] split = sTitle.split(":");
					if (split.length < 2)
						continue;
					sTitle = split[1];
				}

				sTitle = sTitle.trim();
				
				if (sTitle.startsWith("Vi ")) {
					if (!sWeAdds.contains(sTitle)) {
						sWeAdds.add(sTitle);
						weAdds.add(new Item(sTitle, sLink, sDate));
					}
				}
				else if (sTitle.startsWith("Jeg ")) {
					if (!sIAdds.contains(sTitle)) {
						sIAdds.add(sTitle);
						iAdds.add(new Item(sTitle, sLink, sDate));	
					}			
				}
			}
		}
		
	    DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

		// Get lists
		List<String> weStrings = new ArrayList<String>();
		List<String> iStrings = new ArrayList<String>();
		Query query = new Query("We");
	    List<Entity> pq = datastore.prepare(query).asList(FetchOptions.Builder.withDefaults());
	    for (Entity e : pq) {
	    	weStrings.add((String)e.getProperty("title"));
	    }
		query = new Query("I");
	    pq = datastore.prepare(query).asList(FetchOptions.Builder.withDefaults());
	    for (Entity e : pq) {
	    	iStrings.add((String)e.getProperty("title"));
	    }
	    
	    // Get counters
	    query = new Query("ICounter");
		Entity iCounterEnt = datastore.prepare(query).asSingleEntity();
	    Long iCounter = (Long)iCounterEnt.getProperty("count");

	    query = new Query("WeCounter");
		Entity weCounterEnt = datastore.prepare(query).asSingleEntity();
	    Long weCounter = (Long)weCounterEnt.getProperty("count");

	    // Commit New words
	    for (Item i : weAdds) {
		    if (!weStrings.contains(i.getTitle())) {
				Entity ent = new Entity("We");
				ent.setProperty("entry", weCounter);
				ent.setProperty("title", i.getTitle());
				ent.setProperty("link", i.getLink());
				ent.setProperty("date", i.getDate());
				weStrings.add(i.getTitle());
				weCounter++;
				weCounterEnt.setProperty("count", weCounter);

		    	int retries = 3;
				TransactionOptions options = TransactionOptions.Builder.withXG(true);
				Transaction txn = datastore.beginTransaction(options);
			    try {
					datastore.put(ent);
					datastore.put(weCounterEnt);
			    	txn.commit();
				}
				catch (ConcurrentModificationException ex) {
			        if (retries == 0) {
			        	return;
			        }
			        --retries;
				} finally {
			        if (txn.isActive()) {
			            txn.rollback();
			        }
			    }
			}		    	
	    }
	    
	    for (Item i : iAdds) {
		    if (!iStrings.contains(i.getTitle())) {
				Entity ent = new Entity("I");
				ent.setProperty("entry", iCounter);
				ent.setProperty("title", i.getTitle());
				ent.setProperty("link", i.getLink());
				ent.setProperty("date", i.getDate());
				iStrings.add(i.getTitle());
				iCounter++;
				iCounterEnt.setProperty("count", iCounter);

				int retries = 3;
				TransactionOptions options = TransactionOptions.Builder.withXG(true);
				Transaction txn = datastore.beginTransaction(options);
			    try {
					datastore.put(ent);
					datastore.put(iCounterEnt);
			    	txn.commit();
				}
				catch (ConcurrentModificationException ex) {
			        if (retries == 0) {
			        	return;
			        }
			        --retries;
				} finally {
			        if (txn.isActive()) {
			            txn.rollback();
			        }
			    }
			}		    	
	    }
	    try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {
		}
	}

	private void setupDatastore() {
	    DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
		
	    Boolean hasCommitted = false;
	    
		TransactionOptions options = TransactionOptions.Builder.withXG(true);
	    Transaction txn = datastore.beginTransaction(options);

		int retries = 3;
		try {
			Query existanceQuery;
			existanceQuery = new Query("ICounter");
			Entity iCounter = datastore.prepare(existanceQuery).asSingleEntity();
			if (iCounter == null) {
				hasCommitted = true;
				iCounter = new Entity("ICounter");
				iCounter.setProperty("count", 0);
				datastore.put(iCounter);
			}
			existanceQuery = new Query("WeCounter");
			Entity weCounter = datastore.prepare(existanceQuery).asSingleEntity();
			if (weCounter == null) {
				hasCommitted = true;
				weCounter = new Entity("WeCounter");
				weCounter.setProperty("count", 0);
				datastore.put(weCounter);
			}

			
			txn.commit();
		}
		catch (ConcurrentModificationException ex) {
	        if (retries == 0) {
	        	return;
	        }
	        --retries;
		} finally {
	        if (txn.isActive()) {
	            txn.rollback();
	        }
	    }
		if (hasCommitted) {
			try {
				Thread.sleep(2000);
			} catch (InterruptedException e) {
			}			
		}
	}
	
	private Document getWebsiteXML(String path) {
		URL url;
		HttpURLConnection conn;
		SAXBuilder builder = new SAXBuilder();
		try {
			url = new URL(path);
			conn = (HttpURLConnection) url.openConnection();
			conn.setRequestMethod("GET");
			Document d = builder.build(conn.getInputStream());

			return d;
		} catch (Exception e) {
			return null;
		}
	}
}
