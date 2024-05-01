package watercarrier;

import java.util.*;
import java.io.*;
import java.util.concurrent.*;
import paddle.*;
import creek.*;

public class DuplicateDisk {

	private SenseDevice devices;
	private Map<String,SystemCommand> processes;

	private void checkOutput ( String device ) throws Exception {
		if ( devices.baseline().contains( device ) ) throw new Exception( device+" was attached before DuplicateDisk started!" );
		if ( processes.keySet().contains( device ) && !processes.get(device).done() ) throw new Exception( device+" is being written by another process!" );
	}

	private String checkPath ( String device ) throws Exception {
		File file = new File( device );
		if (!file.exists()) file.createNewFile();
		return file.getAbsolutePath();
	}
	
	
	public DuplicateDisk () {
		this( new SenseDevice() );
	}

	public DuplicateDisk ( SenseDevice sd ) {
		processes = new LinkedHashMap<>();
		devices = sd;
	}
	
	public void diskToFile ( String disk, String file ) throws Exception {
		dd( disk, file, "./watercarrier/diskToFile.sh" );
	}

	public void fileToDisk ( String file, String disk ) throws Exception {
		dd( file, disk, "./watercarrier/fileToDisk.sh" );
	}

	public void dd ( String in, String out ) throws Exception {
		dd( in, out, "./watercarrier/raw.sh" );
	}

	public void dd ( String in, String out, String script ) throws Exception {
		String input = checkPath( in );
		String output = checkPath( out );
		checkOutput( output );
		
		String name = "IN: "+input+", OUT:"+output;
		String command = script+" "+input+" "+output;
		
		System.out.println( name );
		System.out.println( command );
		
		SystemCommand ddProc = new SystemCommand(
			command,
			name,
			-1,     // no timeout
			false,  // not verbose
			true    // only output last line
		);
		
		processes.put( output, ddProc );
		new Thread( ddProc ).start();
	}
	
	public Map<String,SystemCommand> processes () {
		return processes;
	}
	
	public void cleanup () {
		for (Map.Entry<String,SystemCommand> entry : processes.entrySet()) {
			SystemCommand sc = entry.getValue();
			if (sc.done()) processes.remove( entry.getKey() );
		}
	}
	
	public void cancel () {
		for (String output : processes.keySet()) {
			cancel( output );
		}
	}

	public void cancel ( String output ) {
		if (processes.containsKey(output)) {
			processes.get(output).kill();
			System.out.println( "killed "+output );
		}
	}

	public Table status ( Table table ) {
		table.append( new String[]{ "Device", "Status", "Details" } );
		for (Map.Entry<String,SystemCommand> entry : processes.entrySet()) {
			SystemCommand sc = entry.getValue();
			String stderr = "";
			while (stderr.equals("")) {
				stderr = sc.stderr().text();
				try{Thread.sleep(1);} catch(Exception e) {e.printStackTrace();}
			}
			table.append( new String[]{ entry.getKey(), (!sc.done() ? "Writing..." : "Complete"), stderr } );
		}
		return table;
	}
	
	public static void main ( String[] args ) throws Exception {
		DuplicateDisk dd = new DuplicateDisk();
		dd.diskToFile( "/dev/zero", "zeros_0.img.gz" );
		Thread.sleep(1000);
		dd.diskToFile( "/dev/zero", "zeros_1.img.gz" );
		for (int i=0; i<4; i++) {
			Thread.sleep(1000);
			System.out.println( dd.status( new SimpleTable() ) );
		}
		dd.cancel( "/home/giw/zeros_0.img.gz" );
		dd.cancel( "/home/giw/zeros_1.img.gz" );
		
		System.out.println( "canceled first processes..." );
		Thread.sleep(500);
		
		dd.fileToDisk( "zeros_1.img.gz", "/dev/null" );
		dd.processes().get("/dev/null").timeout( 1000 ); // change the timeout to 1 sec
		for (int i=0; i<4; i++) {
			Thread.sleep(100);
			System.out.println( dd.status( new SimpleTable() ) );
		}
		dd.fileToDisk( "zeros_1.img.gz", "/dev/null" ); // should throw an exception
	}

}
