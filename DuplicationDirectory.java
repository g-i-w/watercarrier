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
	
	public String cancel ( String disk ) {
		duplicator.cancel( disk );
		return "Canceling writing to '"+disk+"'...";
	}
	
	public SenseDevice sensor () {
		return sensor;
	}
	
	public DuplicateDisk duplicator () {
		return duplicator;
	}
	
	// INPUT: query map
	
	public String processQuery ( Map<String,String> query ) {
		String statusMessage = "";
		if (query.containsKey("file") && query.containsKey("device") && query.containsKey("command")) {
			String file = query.get("file");
			String device = query.get("device");
			String command = query.get("command");
			if (command.equals("start")) {
				statusMessage = fileToDisk( file, device );
			} else if (command.equals("cancel")) {
				statusMessage = cancel(device);
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
	
	public String devicesCommandStatusHTML ( String baseURL, String fileName ) {
		StringBuilder html = new StringBuilder();
		for (Map.Entry<String,String> entry : duplicator.safeDevicesInfo().entrySet()) {
			String device = entry.getKey();
			String info = entry.getValue();
			String link = "<a class=\"device link\" href=\""+baseURL+"?file="+fileName+"&device="+device+"&command=start\">Start</a>";
			String status = "";
			if ( duplicator.processes().containsKey(device) ) {
				status = duplicator.processes().get(device).stderr().text();
				link = "<a class=\"deviceCommandLink\" href=\""+baseURL+"?file="+fileName+"&device="+device+"&command=cancel\">Cancel</a>";
			}
			html
				.append( "<div class=\"device\">" )
				.append( "<div class=\"device name\">"+device+"</div>" )
				.append( "<div class=\"device info\">"+info+"</div>" )
				.append( "<div class=\"device command\">"+link+"</div>" )
				.append( "<div class=\"device status\">"+status+"</div>" )
				.append( "</div>" )
			;
		}
		return html.toString();
	}
	
	public String statusHTML () {
		return Tables.html(duplicator.status( new SimpleTable() ));
	}
	
	
	// testing
	
	public static void main ( String[] args ) throws Exception {
		new DuplicationDirectory( args[0] );
	}

}
