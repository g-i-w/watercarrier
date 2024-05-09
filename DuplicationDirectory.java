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
		Table deviceInfo = sensor.deviceInfo();
		for (List<String> line : deviceInfo.data()) {
			if (line.size()>1) line.add(0, "<input type=\"radio\" name=\"device\" value=\""+line.get(0)+"\">");
		}
		deviceInfo.data().add( 0, Arrays.asList( new String[]{ "", "Device", "Size" } ) );
		return Tables.html( deviceInfo );
	}
	
	public String statusHTML () {
		return Tables.html(duplicator.status( new SimpleTable() ));
	}
	
	public String fileToDisk ( String file, String disk ) {
		disk = "/dev/"+disk;
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
		disk = "/dev/"+disk;
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
		duplicator.cancel( "/dev/"+disk );
		return "Canceling writing to '"+disk+"'...";
	}
	
	public SenseDevice sensor () {
		return sensor;
	}
	
	public DuplicateDisk duplicator () {
		return duplicator;
	}

}
