package watercarrier;

import java.util.*;
import creek.*;
import paddle.*;


public class DuplicationStation extends ServerState {

	String biblesdPath;
	DuplicateDisk duplicator;
	TemplateFile biblelocalTemplate;
	
	private String val ( Tree unknown ) {
		if (unknown==null) return "";
		else return unknown.value();
	}
	
	private String nonNull ( Object obj ) {
		return ( obj!=null ? obj.toString() : "" );
	}

	public DuplicationStation ( String bootDisk, String biblesdPath, int port ) throws Exception {
		this.biblesdPath = biblesdPath;
		duplicator = new DuplicateDisk( bootDisk );
		biblelocalTemplate = new TemplateFile( "watercarrier/biblelocal-duplication.html", "////" );
		ServerHTTP server = new ServerHTTP (
			this,
			port,
			"Bible.Local duplication server",
			1024,
			4000
		);
		while( server.starting() ) Thread.sleep(1);
	}
	
	public String processQuery ( Map<String,String> query ) {
		//System.out.println( "**********\n"+query+"\n**********" );
	
		String statusMessage = "";
		
		String input = query.get("input");
		String output = query.get("output");
		String command = query.get("command");
		
		if ( output!=null && command!=null ) {
			if (command.equals("createBibleSD")) {
				statusMessage = duplicator.fileToDisk( biblesdPath, output, "BibleSD media" );
			} else if (command.equals("createBibleLocal")) {
				statusMessage = duplicator.diskToDisk( "/dev/mmcblk0", output, "Bible.Local boot media" );
			} else if (command.equals("cancel")) {
				duplicator.cancel( output );
				System.out.println( "************** CANCELING "+output+" **************" );
			}
		}
		
		return statusMessage;
	}

	public String devicesHTML () {
		StringBuilder html = new StringBuilder();
		Tree statusTree = duplicator.statusTree();
		for (String device : statusTree.keys()) {
			Tree branch = statusTree.get(device);

			String size = val(branch.get("size"));
			Double gibMedia = 0.0;
			if (!size.equals("")) gibMedia = Double.valueOf( size.substring(0, size.length()-1) );
			String gbMediaStr = String.format("%.1f", (gibMedia*1.074))+" GB";

			String status = val(branch.get("status"));
			String link = "";
			String label = val(branch.get("label"));
			String output = val(branch.get("output"));
			String progressBar = "";
			String statusStr = "";
			
			if (status.equals("Writing")) {
				Double bMedia = gibMedia*Math.pow(1024,3);
				String progress = Regex.first( output, "(\\d+)\\s+bytes" );
				if (progress!=null) {
					progressBar = "<progress max=\""+bMedia+"\" value=\""+progress+"\">"+progress+" bytes</progress>";
				}
				link =
					"<div class=\"device cancel\"><a href=\"?output="+device+"&command=cancel\">Cancel</a></div>";
			} else {
				if (gibMedia > 0.0) {
					if (gibMedia > 36.7) { // minimum capacity for possible copy of Bible.Local
						link += "<div class=\"device biblelocalsd\"><a href=\"?output="+device+"&command=createBibleLocal\">Bible.Local</a></div>";
					}
					link += "<div class=\"device biblesd\"><a href=\"?output="+device+"&command=createBibleSD\">BibleSD</a></div>";
				}
			}
			
			if (!status.equals("")) {
				statusStr = status;
				if (status.equals("Complete")) statusStr = "<span style=\"background-color:lightgreen;\">Complete</span>";
				if (status.equals("Canceled")) statusStr = "<span style=\"background-color:rgb(255,200,200);\">Canceled</span>";
				html
					.append( "<div class=\"device\">" )
					.append( "<div class=\"device name\">"+val(branch.get("name"))+"</div>" )
					.append( "<div class=\"device size\">"+gbMediaStr+"</div>" )
					.append( link )
					.append( "<div class=\"device label\">"+statusStr+": "+label+"</div>" )
					.append( "<div>"+progressBar+"</div>" )
					.append( "<div class=\"device text\">"+output+"</div>" )
					.append( "</div>" )
				;
			} else {
				html
					.append( "<div class=\"device\">" )
					.append( "<div class=\"device name\">"+branch.get("name")+"</div>" )
					.append( "<div class=\"device size\">"+gbMediaStr+"</div>" )
					.append( link )
					.append( "</div>" )
				;
			}
			html.append( "<br>" );
		}
		return html.toString();
	}
	
	public void received ( Connection c ) {
		super.received( c );
		if (c instanceof InboundHTTP) {
			// convert type to InboundHTTP
			InboundHTTP session = (InboundHTTP)c;
			System.out.println( session.request().path()+" "+session.request().query() );
			
			// check path
			if (session.request().path().equals("/")) {
			
				// fill in blanks in the TemplateFile
				biblelocalTemplate.replace( "statusMessage", processQuery( session.request().query() ) );
				biblelocalTemplate.replace( "deviceDivs", devicesHTML() );
			
				// HTTP response
				session.response(
					new ResponseHTTP(
						new String[]{ "Content-Type", "text/html" },
						biblelocalTemplate.toString()
					)
				);
				
			} else {
				session.response(
					new ResponseHTTP( "not found" )
				);
			}
		}
	}
	
	public static void main ( String[] args ) throws Exception {
		DuplicationStation ds = new DuplicationStation( args[0], args[1], Integer.parseInt(args[2]) );
	}

}
