package watercarrier;

import java.util.*;
import creek.*;
import paddle.*;


public class DuplicationStation extends ServerState {

	String biblesdPath;
	String raptureKitPath;
	String reloadPath;
	DuplicateDisk duplicator;
	TemplateFile biblelocalTemplate;
	
	String bootDisk;
	
	private String val ( Tree unknown ) {
		if (unknown==null) return "";
		else return unknown.value();
	}
	
	private String nonNull ( Object obj ) {
		return ( obj!=null ? obj.toString() : "" );
	}

	public DuplicationStation ( String bootUUID, String biblesdPath, String raptureKitPath, String reloadPath, int port ) throws Exception {
		this.biblesdPath = biblesdPath;
		this.raptureKitPath = raptureKitPath;
		this.reloadPath = reloadPath;
		this.bootDisk = SenseDevice.deviceFromUUID( bootUUID );
		System.out.println( "Boot Disk: "+bootDisk );
		this.duplicator = new DuplicateDisk();
		this.biblelocalTemplate = new TemplateFile( "watercarrier/biblelocal-duplication.html", "---" );
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
				//statusMessage = duplicator.fileToDisk( biblesdPath, output, "BibleSD media -> "+output );
				statusMessage = duplicator.directoryToDisk( biblesdPath, output, "BibleSD content to "+output );
			} else if (command.equals("createBibleLocal")) {
				statusMessage = duplicator.diskToDisk( bootDisk, output, "Bible.Local boot media to "+output );
			} else if (command.equals("createRaptureKit")) {
				statusMessage = duplicator.directoryToDisk( raptureKitPath, output, "RaptureKit content to "+output );
			} else if (command.equals("cancel")) {
				duplicator.cancel( output );
				try {
					Thread.sleep(1000); // 1 sec
					duplicator.safeUnmount( output );
				} catch (Exception e) {
					e.printStackTrace();
				}
				System.out.println( "************** CANCELING "+output+" **************" );
			}
		}
		
		return statusMessage;
	}

	public String devicesHTML () {
		StringBuilder html = new StringBuilder();
		//Tree statusTree = duplicator.statusTree();
		for (DiskOperation op : duplicator.status()) {
			//Tree branch = statusTree.get(device);

			//String size = val(branch.get("size"));
			//Double gibMedia = 0.0;
			//if (!size.equals("")) gibMedia = Double.valueOf( size.substring(0, size.length()-1) );
			//String gbMediaStr = String.format("%.1f", (gibMedia*1.074))+" GB";

			//String status = val(branch.get("status"));
			String link = "";
			//String label = val(branch.get("label"));
			//String output = val(branch.get("output"));
			String progressBar = "";
			//String statusStr = "";
			
			if (op.status().equals("Writing")) {
				//Double bMedia = gibMedia*Math.pow(1024,3);
				String progress;
				if ( (progress = Regex.first( op.output(), "([\\d,]+)\\s+bytes" )) != null) {
					progressBar = "<progress max=\""+op.sizeb()+"\" value=\""+progress+"\">"+progress+" bytes</progress>";
				} else if ( (progress = Regex.first( op.output(), "([\\d]+)%" )) != null) {
					progressBar = "<progress max=\"100\" value=\""+progress+"\">"+progress+"%</progress>";
				}
				link =
					"<div class=\"device cancel\"><a href=\"?output=/dev/"+op.device()+"&command=cancel\">Cancel</a></div>";
			} else {
				if (op.gib() > 0.0) {
					if (op.isChild()) {
						link += "<div class=\"device rapturekit\"><a href=\"?output=/dev/"+op.device()+"&command=createRaptureKit\">RaptureKit</a></div>";
						link += "<div class=\"device biblesd\"><a href=\"?output=/dev/"+op.device()+"&command=createBibleSD\">Bibles</a></div>";
					} else if (op.gib() > 53.5) { // current minimum capacity for Bible.Local
						link += "<div class=\"device biblelocalsd\"><a href=\"?output=/dev/"+op.device()+"&command=createBibleLocal\">Bible.Local Server</a></div>";
					}
				}
			}
			
			if (!op.status().equals("")) {
				String statusStr = op.status();
				if (op.status().equals("Complete")) statusStr = "<span style=\"background-color:lightgreen;\">Complete</span>";
				if (op.status().equals("Canceled")) statusStr = "<span style=\"background-color:rgb(255,200,200);\">Canceled</span>";
				html
					.append( "<div class=\"device\">" )
					.append( "<div class=\"device name\">"+op.device()+"</div>" )
					.append( "<div class=\"device size\">"+op.sizeGB()+"</div>" )
					.append( link )
					.append( "<div class=\"device label\">"+statusStr+": "+op.label()+"</div>" )
					.append( "<div>"+progressBar+"</div>" )
					.append( "<div class=\"device text\">"+op.output()+"</div>" )
					.append( "</div>" )
				;
			} else {
				html
					.append( "<div class=\"device\">" )
					.append( "<div class=\"device name\">"+op.device()+"</div>" )
					.append( "<div class=\"device size\">"+op.sizeGB()+"</div>" )
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
				biblelocalTemplate.replace( "reloadPath", reloadPath );
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
		DuplicationStation ds = new DuplicationStation( args[0], args[1], args[2], args[3], Integer.parseInt(args[4]) );
	}

}
