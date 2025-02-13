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
	
	double bibleLocalSizeGiB;
	double biblesdSizeGiB;
	double rapturekitSizeGiB;
	
	String statusMessage = "";

	private String val ( Tree unknown ) {
		if (unknown==null) return "";
		else return unknown.value();
	}
	
	private String nonNull ( Object obj ) {
		return ( obj!=null ? obj.toString() : "" );
	}

	public DuplicationStation (
		String bootUUID,
		String bibleLocalSize,
		String biblesdPath,
		String biblesdSize,
		String raptureKitPath,
		String rapturekitSize,
		String reloadPath,
		int port
	) throws Exception {
		bibleLocalSizeGiB = Double.parseDouble(bibleLocalSize);
		this.biblesdPath = biblesdPath;
		biblesdSizeGiB = Double.parseDouble(biblesdSize);
		this.raptureKitPath = raptureKitPath;
		rapturekitSizeGiB = Double.parseDouble(rapturekitSize);
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
		
		String input = query.get("input");
		String output = query.get("output");
		String command = query.get("command");
		
		if ( output!=null && command!=null ) {
			if (command.equals("createBibleSD")) {
				//statusMessage = duplicator.fileToDisk( biblesdPath, output, "BibleSD media -> "+output );
				statusMessage = duplicator.directoryToDisk( biblesdPath, output, "Bibles content to "+output );
			} else if (command.equals("createBibleLocal")) {
				statusMessage = duplicator.diskToDisk( bootDisk, output, "Bible.Local boot media to "+output );
			} else if (command.equals("createRaptureKit")) {
				statusMessage = duplicator.directoryToDisk( raptureKitPath, output, "RaptureKit content to "+output );
			} else if (command.equals("cancel")) {
				duplicator.cancel( output );
				statusMessage = "Canceled writing to "+output;
				System.out.println( "************** CANCELING "+output+" **************" );
			}
		}
		
		return statusMessage;
	}
	
	private String rsyncProgress ( DiskOperation op ) {
		try {
			//String progress = Regex.first( op.output(), "([\\d]+)%" );
			List<String> progress = Regex.groups( op.output(), "([\\d\\.]+)(\\w)" );
			//if (progress != null) progressBar = "<progress max=\"100\" value=\""+progress+"\">"+progress+"%</progress>";
			if (progress.size() >= 2) {
				double total = op.size(); // default
				if (op.label().indexOf("Bibles")==0) total = biblesdSizeGiB;
				if (op.label().indexOf("RaptureKit")==0) total = rapturekitSizeGiB;
				double transferred = Double.parseDouble( progress.get(0) );
				if (progress.get(1).equals("M")) transferred = transferred/1e3;
				if (progress.get(1).equals("K")) transferred = transferred/1e6;
				double percent = transferred/total*100;
				return "<progress max=\"100\" value=\""+percent+"\">"+percent+"%</progress>";
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return "";
	}
	
	public String devicesHTML () {
		StringBuilder html = new StringBuilder();

		for (DiskOperation op : duplicator.status()) {
			String link = "";
			String progressBar = "";
			
			// link or cancel & progress bar
			if (op.status().equals("Writing")) {
				if ( op.isChild() ) {
					progressBar = rsyncProgress( op );
				} else {
					String progress = Regex.first( op.output(), "([\\d,]+)\\s+bytes" );
					if (progress != null) progressBar = "<progress max=\""+op.sizeb()+"\" value=\""+progress+"\">"+progress+" bytes</progress>";
				}
				link =
					"<div class=\"device cancel\"><a href=\"?output="+op.device()+"&command=cancel\">Cancel</a></div>";
			} else {
				if (op.size() > 0.0) {
					if (op.isChild() && !op.parent().status().equals("Writing") && (op.size() >= rapturekitSizeGiB || op.size() >= biblesdSizeGiB)) {
						link += "<div class=\"device rapturekit\"><a href=\"?output="+op.device()+"&command=createRaptureKit\">RaptureKit "+rapturekitSizeGiB+"GiB</a></div>";
						link += "<div class=\"device biblesd\"><a href=\"?output="+op.device()+"&command=createBibleSD\">Bibles "+biblesdSizeGiB+"GiB</a></div>";
					} else if (!op.isChild() && op.size() >= bibleLocalSizeGiB) { // current minimum capacity for Bible.Local
						link += "<div class=\"device biblelocalsd\"><a href=\"?output="+op.device()+"&command=createBibleLocal\">Bible.Local Server "+bibleLocalSizeGiB+"GiB</a></div>";
					}
				}
			}
			
			// disk usage
			String diskUsage = "";
			if (op.isChild() && op.usedUnit()!=null) {
				diskUsage = "<div><span style=\"font-size:0.7em;\">Used: "+op.used()+op.usedUnit()+"iB</span><br><meter max=\"100\" value=\""+op.percent()+"\" low=\"80\">"+op.percent()+"%</meter></div>";
			}
			
			// operational info
			if (!op.status().equals("")) {
				String statusStr = op.status();
				if (op.status().equals("Complete")) statusStr = "<span style=\"background-color:lightgreen;\">Complete</span>";
				if (op.status().equals("Canceled")) statusStr = "<span style=\"background-color:rgb(255,200,200);\">Canceled</span>";
				html
					.append( "<div class=\"device\">" )
					.append( "<div class=\"device name\">"+op.device()+"</div>" )
					.append( "<div class=\"device size\">"+op.sizeGiB()+"GiB</div>" )
					.append( diskUsage )
					.append( link )
					.append( "<div class=\"device info\">"+statusStr+": "+op.label()+"</div>" )
					.append( "<div>"+progressBar+"</div>" )
					.append( !op.output().equals("") && op.status().equals("Writing") ? "<div class=\"device status\">"+op.output()+"</div>" : "" )
					.append( "</div>" )
				;
			} else {
				html
					.append( "<div class=\"device\">" )
					.append( "<div class=\"device name\">"+op.device()+"</div>" )
					.append( "<div class=\"device size\">"+op.sizeGiB()+"GiB</div>" )
					.append( diskUsage )
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
		DuplicationStation ds = new DuplicationStation( args[0], args[1], args[2], args[3], args[4], args[5], args[6], Integer.parseInt(args[7]) );
	}

}
