package watercarrier;

import java.util.*;
import java.io.*;
import java.nio.file.*;
import java.text.DecimalFormat;
import paddle.*;
import creek.*;

public class DuplicationDirectory {

	private File directory;
	private SenseDevice sensor;
	private DuplicateDisk duplicator;
	
	
	// from https://stackoverflow.com/questions/3263892/format-file-size-as-mb-gb-etc
	public static String readableFileSize(long size) {
	    if(size <= 0) return "0";
	    final String[] units = new String[] { "B", "kB", "MB", "GB", "TB", "PB", "EB" };
	    int digitGroups = (int) (Math.log10(size)/Math.log10(1024));
	    return new DecimalFormat("#,##0.#").format(size/Math.pow(1024, digitGroups)) + " " + units[digitGroups];
	}
	

	public DuplicationDirectory ( String dir ) throws Exception {
		directory = new File( dir );
		if (!directory.exists()) throw new Exception( dir+" not found!" );
		
		sensor = new SenseDevice();
		duplicator = new DuplicateDisk( sensor );
	}
	
	public String fileToDisk ( String file, String disk ) {
		file = new File( directory, file ).getAbsolutePath();
		try {
			duplicator.fileToDisk( file, disk );
			return "Writing file '"+file+"' to disk '"+disk+"'...";
		} catch (Exception e) {
			e.printStackTrace();
			return e.getMessage();
		}
	}
	
	public String fileToDiskCopyGz ( String file, String disk ) {
		file = new File( directory, file ).getAbsolutePath();
		try {
			duplicator.fileToDiskCopyGz( file, disk );
			return "Writing '"+file+"' to '"+disk+"', then will mount and copy to same directory...";
		} catch (Exception e) {
			e.printStackTrace();
			return e.getMessage();
		}
	}
	
	public String diskToFile ( String disk, String file ) {
		file = new File( directory, file ).getAbsolutePath();
		try {
			duplicator.diskToFile( disk, file );
			return "Writing disk '"+disk+"' to file '"+file+"'...";
		} catch (Exception e) {
			e.printStackTrace();
			return e.getMessage();
		}
	}
	
	public String cancelFile ( String file ) {
		duplicator.cancel( (new File( directory, file )).getAbsolutePath() );
		return "Canceling writing to '"+file+"'...";
	}
	
	public String cancelDisk ( String device ) {
		duplicator.cancel( "/dev/"+device );
		return "Canceling writing to '"+device+"'...";
	}
	
	public SenseDevice sensor () {
		return sensor;
	}
	
	public DuplicateDisk duplicator () {
		return duplicator;
	}
	
	// INPUT: query map
	
	public String processQuery ( Map<String,String> query ) {
		System.out.println( "**********\n"+query+"\n**********" );
	
		String statusMessage = "";
		if (query.containsKey("file") && query.containsKey("device") && query.containsKey("command")) {
			String file = query.get("file");
			String device = query.get("device");
			String command = query.get("command");
			if (command.equals("fileToDisk")) {
				statusMessage = fileToDisk( file, device );
			} else if (command.equals("fileToDiskCopyGz")) {
				statusMessage = fileToDiskCopyGz( file, device );
			} else if (command.equals("cancel")) {
				statusMessage = cancelDisk	(device);
				System.out.println( "************** CANCELING! **************" );
			}
		}
		return statusMessage;
	}
	
	// OUTPUT: HTML tables
	
	public String[] fileAttributes ( File file ) {
		String name = file.getName();
		long size = 0;
		try {
			size = Files.size(Paths.get(file.getAbsolutePath()));
		} catch (Exception e) {
			e.printStackTrace();
		}
		String radioButton = "<input type=\"radio\" name=\"file\" value=\""+name+"\">";
		return new String[]{ radioButton, name, readableFileSize(size) };
	}
	
	public String filesHTML () {
		Table table = new SimpleTable();
		table.append( new String[]{ "", "File", "Size" } );
		List<File> dirList = Arrays.asList( directory.listFiles() );
		Collections.sort( dirList );
		for (File file : dirList) {
			if (FileActions.extension(file).equals("gz")) table.append( fileAttributes( file ) );
		}
		return Tables.html(table);
	}
	
	public String devicesHTML () {
		Table deviceTable = new SimpleTable();
		deviceTable.append( new String[]{ "", "Device", "Size" } );
		for (Map.Entry<String,String> entry : duplicator.safeDevicesInfo().entrySet()) {
			String device = entry.getKey();
			String info = entry.getValue();
			deviceTable.append( new String[]{
				"<input type=\"radio\" name=\"device\" value=\""+device+"\">",
				device,
				info
			});
		}
		return Tables.html( deviceTable );
	}
	
	public String devicesCommandStatusHTML ( String baseURL, String fileName, String startCommand ) {
		StringBuilder html = new StringBuilder();
		for (Map.Entry<String,String> entry : duplicator.safeDevicesInfo().entrySet()) {
			String device = entry.getKey();
			String info = entry.getValue();
			String link = "<a href=\""+baseURL+"?file="+fileName+"&device="+device+"&command="+startCommand+"\">Start</a>";
			String output = duplicator.processOutput( device );
			int val = duplicator.processStatus( device );
			String status;
			switch (val) {
				case 1:
					status = "Writing...";
					link = "<a href=\""+baseURL+"?file="+fileName+"&device="+device+"&command=cancel\">Cancel</a>";
					break;
				case 2:
					status = "<span style=\"background-color:rgb(255,200,200);\">Canceled</span>";
					break;
				case 3:
					status = "<span style=\"background-color:rgb(200,255,200);\">Complete</span>";
					break;
				default:
					status = "";
			}
			html
				.append( "<div class=\"device\">" )
				.append( "<div class=\"device name\">"+device+"</div>" )
				.append( "<div class=\"device info\">"+info+"</div>" )
				.append( "<div class=\"device command\">"+link+"</div>" )
				.append( "<div class=\"device status\">"+status+"</div>" )
				.append( "<div class=\"device text\">"+output+"</div>" )
				.append( "</div>" )
			;
		}
		return html.toString();
	}
	
	public Tree devicesStatus () {
		Tree tree = new JSON();
		for (Map.Entry<String,String> entry : duplicator.safeDevicesInfo().entrySet()) {
			
			String device = entry.getKey();
			String info = entry.getValue();
			int code = duplicator.processStatus( device );
			String status;
			switch (code) {
				case 1:
					status = "Writing";
					break;
				case 2:
					status = "Canceled";
					break;
				case 3:
					status = "Complete";
					break;
				default:
					status = "";
			}

			tree.auto( device )
				.add( "info", info )
				.add( "output", duplicator.processOutput( device ) )
				.add( "code", String.valueOf(code) )
				.add( "status", status )
			;
		}
		return tree;
	}
	
	public String statusHTML () {
		return Tables.html(duplicator.status( new SimpleTable() ));
	}
	
	
	// testing
	
	public static void main ( String[] args ) throws Exception {
		new DuplicationDirectory( args[0] );
	}

}
