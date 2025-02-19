package watercarrier;

import java.util.*;
import creek.*;
import paddle.*;


public class DuplicationStation extends ServerState {
	
	Tree conf;
	Tree rsyncInfo;

	DuplicateDisk duplicator;
	TemplateFile biblelocalTemplate;
	
	String bootDisk;
	String bootDiskLabel;
	String bootDiskCommand;
	String bootDiskMessage;
	String bootDiskStyle;
	double bootDiskSizeGiB;
	
	String statusMessage = "";
	
	private String val ( Tree unknown ) {
		if (unknown==null) return "";
		else return unknown.value();
	}
	
	private String nonNull ( Object obj ) {
		return ( obj!=null ? obj.toString() : "" );
	}
	
	private double tryDouble( String d ) {
		try {
			return Double.parseDouble( d );
		} catch (Exception e) { e.printStackTrace(); }
		return -1.0;
	}

	public DuplicationStation ( Tree conf ) throws Exception {
		this.conf = conf;
		rsyncInfo = conf.get( "rsyncDirectories" );
		System.out.println( conf.serialize() );
		
		bootDisk = SenseDevice.deviceFromUUID( conf.get( "bootDiskUUID" ).value() );
		System.out.println( "Boot Disk: "+bootDisk );
		
		bootDiskLabel = conf.get( "bootDiskLabel" ).value();
		bootDiskCommand = conf.get( "bootDiskCommand" ).value();
		bootDiskMessage = conf.get( "bootDiskMessage" ).value();
		bootDiskStyle = conf.get( "bootDiskStyle" ).value();
		bootDiskSizeGiB = tryDouble( conf.get( "bootDiskSizeGiB" ).value() );
		
		duplicator = new DuplicateDisk();
		
		biblelocalTemplate = new TemplateFile(
			conf.get("htmlTemplatePath").value(),
			conf.get("htmlTemplateDelim").value()
		);
		
		ServerHTTP server = new ServerHTTP (
			this,
			Integer.parseInt( conf.get("serverPort").value() ),
			"Bible.Local duplication server",
			1024,
			4000
		);
		
		while( server.starting() ) Thread.sleep(1);
	}
	
	public String processQuery ( Map<String,String> query ) {
		//System.out.println( "**********\n"+query+"\n**********" );
		String prefix = "<br><div><div style=\"background-color:gray;color:white;font-family:serif;\"><i><b>&nbsp;i&nbsp;</b></i></div>";		
		String suffix = "</div>";		
		
		String input = query.get("input");
		String output = query.get("output");
		String command = query.get("command");
		
		if ( output!=null && command!=null ) {
			// CANCEL
			if (command.equals("cancel")) {
				System.out.println( "************** CANCELING writing to "+output+" **************" );
				duplicator.cancel( output );
				statusMessage = prefix + "Canceled writing to " + output + suffix;
			// any directory to rsync
			} else if (rsyncInfo.keys().contains( command )) { // query 'command' is simply the obj key
				// get directory & info from conf Tree
				Tree rsyncDirectory = conf.get("rsyncDirectories").get( command );
				String inputPath = rsyncDirectory.get( "path" ).value();
				String linkLabel = rsyncDirectory.get( "label" ).value();
				System.out.println( "************** COPYING "+inputPath+" to "+output+" ("+command+") **************" );
				statusMessage = prefix + duplicator.directoryToDisk( inputPath, output, command ) + suffix; // query 'command' becomes the label for the SystemCommand process
			// clone boot disk
			} else if (bootDiskCommand.equals( command )) {
				System.out.println( "************** CLONING "+bootDisk+" to "+output+" ("+command+") **************" );
				statusMessage = prefix + duplicator.diskToDisk( bootDisk, output, command ) + suffix; // query 'command' becomes the label for the SystemCommand process
			}
		}
		
		return statusMessage;
	}
	
	private String rsyncProgress ( DiskData op ) {
		try {			
			//String progress = Regex.first( op.output(), "([\\d]+)%" );
			List<String> progress = Regex.groups( op.output(), "([\\d\\.]+)(\\w)" );
			//if (progress != null) progressBar = "<progress max=\"100\" value=\""+progress+"\">"+progress+"%</progress>";
			if (progress.size() >= 2) {
				double total = Double.parseDouble(
					rsyncInfo.get( op.label() ).get("sizeGiB").value()
				);
				double transferred = DiskData.toGB(
					Double.parseDouble( progress.get(0) ), // size
					progress.get(1) // unit
				);
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

		for (DiskData op : duplicator.status()) {

			// status variables
			String diskUsage = "";
			String link = "";
			String progressBar = "";
			
			// disk usage (only applicable to disk partitions, e.g. 'children')
			if (op.isChild() && op.usedUnit()!=null) {
				diskUsage = "<div><span style=\"font-size:0.7em;\">Free: "+op.availGB()+"</span><br><meter max=\"100\" value=\""+op.percent()+"\" low=\"80\">"+op.percent()+"%</meter></div>";
			}
			
			// if 'Writing' operation then progress bar & cancel button
			if (op.status().equals("Writing")) {
				if ( op.isChild() ) {
					// children (i.e. partitions) are rsync processes
					progressBar = rsyncProgress( op );
				} else {
					// parents (i.e. root devices) are dd processes
					String progress = Regex.first( op.output(), "([\\d,]+)\\s+bytes" );
					if (progress != null) progressBar = "<progress max=\""+op.sizeb()+"\" value=\""+progress+"\">"+progress+" bytes</progress>";
				}
				link = "<div class=\"cancel\"><a href=\"?output="+op.device()+"&command=cancel\">Cancel</a></div>";
				
			// otherwise link button(s) where applicable
			} else {
				if (op.isChild() && !op.parent().status().equals("Writing")) {
					for (String queryCommand : rsyncInfo.keys()) {
						Tree info = rsyncInfo.get( queryCommand );
						String label = info.get("label").value(); // label for the link, not to be confused with the SystemCommand.label()
						String style = info.get("style").value(); // extra style information, such as background color
						double spaceNeeded = tryDouble( info.get("sizeGiB").value() );
						if (op.size() >= spaceNeeded) {
							link += "<div style=\""+style+"\"><a href=\"?output="+op.device()+"&command="+queryCommand+"\">"+label+"</a></div>";
						}
					}
				} else if (!op.isChild() && op.size() >= bootDiskSizeGiB) { // current minimum capacity for Bible.Local
					link += "<div style=\""+bootDiskStyle+"\"><a href=\"?output="+op.device()+"&command="+bootDiskCommand+"\">"+bootDiskLabel+"</a></div>";
				}
			}
			
			// build the HTML
			if (!op.status().equals("")) {
				String statusStr = op.status();
				if (op.status().equals("Complete")) {
					statusStr =
						( op.exitValue() == 0 ? "<span style=\"background-color:lightgreen;\">Complete</span>" : "<span style=\"background-color:rgb(255,200,200);\">Complete (error)</span>" )+
						"<div><progress max=\"100\" value=\"100\">100%</progress></div>"+
						( !op.output().equals("") ? "<div class=\"status\">"+op.output()+"</div>" : "" );
				}
				if (op.status().equals("Canceled")) {
					statusStr =
						"<span style=\"background-color:rgb(255,200,200);\">Canceled</span>";
				}
				if (op.status().equals("Writing")) {
					// check for label (HTML command) in rsyncInfo
					if ( rsyncInfo.keys().contains( op.label() ) ) {
						statusStr =
							rsyncInfo.get( op.label() ).get( "message" ).value()+
							"<div>"+progressBar+"</div>"+
							( !op.output().equals("") ? "<div class=\"status\">"+op.output()+"</div>" : "" );
					// otherwise check for label as bootDiskCommand
					} else if ( bootDiskCommand.equals( op.label() ) ) {
						statusStr =
							bootDiskMessage+
							"<div>"+progressBar+"</div>"+
							( !op.output().equals("") ? "<div class=\"status\">"+op.output()+"</div>" : "" );
					}
				}
				//System.out.println( op.device()+" "+op.status()+" "+op.label() );
				html
					.append( "<div class=\"device\">" )
					.append( "<div class=\"icon\">&#x1F4BE;</div><div><div class=\"name\">"+op.device()+"<br><span class=\"size\">"+op.sizeGB()+"</span></div>"+diskUsage+"</div>" )
					.append( link )
					.append( "<div class=\"info\">"+statusStr+"</div>" )
					.append( "</div>" )
				;
			} else {
				html
					.append( "<div class=\"device\">" )
					.append( "<div class=\"icon\">&#x1F4BE;</div><div><div class=\"name\">"+op.device()+"<br><span class=\"size\">"+op.sizeGB()+"</span></div>"+diskUsage+"</div>" )
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
				biblelocalTemplate.replace( "reloadPath", conf.get("htmlRefreshPath").value() );
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
				System.out.println( session.request().path() );
				httpRespondFile ( session, "watercarrier/pics" );
			}
		}
	}
	
	public static void main ( String[] args ) throws Exception {
		DuplicationStation ds = new DuplicationStation(
			new JSON( JSON.RETAIN_ORDER ).deserialize( FileActions.read( args[0] ) ) // read in config file
		);
	}

}
