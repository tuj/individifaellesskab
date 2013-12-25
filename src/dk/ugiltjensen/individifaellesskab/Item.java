package dk.ugiltjensen.individifaellesskab;

public class Item {
	private String title;
	private String link;
	private String date;

	public Item(String title, String link, String date) {
		this.title = title;
		this.link  = link;
		this.date  = date;
	}
	
	public String getTitle()  {return title;}
	public String getLink()   {return link;}
	public String getDate()   {return date;}
}
