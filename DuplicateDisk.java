package watercarrier;

import java.util.*;
import java.io.*;
import java.util.concurrent.*;
import paddle.*;
import creek.*;

public class DuplicateDisk {

	private SenseDevice devices;
	private Map<String,SystemCommand> processes;

	private String checkPath ( String path ) throws Exception {
		File file = new File( path );
		if (!file.exists()) {
			System.err.println( "Creating new file at '"+path+"' ..." );
			file.createNewFile();
		}
		return file.getAbsolutePath();
	}
	
	
	public DuplicateDisk () {
		this( new SenseDevice() );
	}

	public DuplicateDisk ( SenseDevice sd ) {
		processes = new LinkedHashMap<>();
		devices = sd;
	}
	
	public String diskToFile ( String device, String file, String label ) {
		try {
			if (!devices.deviceList().contains(device)) throw new Exception( device+" not found" );
			dd( device, file, "./watercarrier/diskToFile.sh", label );
			return "Writing disk '"+device+"' to file '"+file+"'...";
		} catch (Exception e) {
			e.printStackTrace();
			return e.getMessage();
		}
	}
	
	public String fileToDisk ( String file, String device, String label ) {
		try {
			beforeDiskWrite( device );
			dd( file, device, "./watercarrier/fileToDisk.sh", label );
			return "Writing file '"+file+"' to disk '"+device+"'...";
		} catch (Exception e) {
			e.printStackTrace();
			return e.getMessage();
		}
	}
	
	public String diskToDisk ( String input, String output, String label ) {
		try {
			beforeDiskWrite( output );
			dd( input, output, "./watercarrier/raw.sh", label );
			return "Copying '"+input+" to '"+output+"'...";
		} catch (Exception e) {
			e.printStackTrace();
			return e.getMessage();
		}
	}
	
	public void umount ( Tree device ) {
		for (String mount : device.get("mountpoints").values()) {
			if (! mount.equals("null")) {
				System.out.println( "Unmounting "+mount+"..." );
				System.out.println(
					new SystemCommand( "umount "+mount ).output()
				);
			}
		}
	}

	public void beforeDiskWrite ( String device ) throws Exception {
		if (device.equals("null")) return; // allow "/dev/null" for testing
		Tree safeDevicesTree = safeDevicesTree();
		if (!safeDevicesTree.keys().contains(device)) throw new Exception( device+" is not a safe device" );
		
		System.out.println( "Safe Device Tree:\n"+safeDevicesTree.serialize() );
		
		umount( safeDevicesTree.get(device) );
		
		if (safeDevicesTree.get(device).keys().contains("children")) {
			for (Tree child : safeDevicesTree.get(device).get("children").branches()) {
				umount( child );
			}
		}
	}
	
	public void dd ( String in, String out ) throws Exception {
		dd( in, out, "./watercarrier/raw.sh", "IN: "+in+", OUT: "+out );
	}

	public void dd ( String in, String out, String script, String label ) throws Exception {
		if (processes.keySet().contains(out) && !processes.get(out).finished()) throw new Exception( out+" is busy" );
		
		String input = checkPath( in );
		String output = checkPath( out );
		
		String command = script+" "+input+" "+output;
		
		System.out.println( command );
		System.out.println( label );
		
		SystemCommand ddProc = new SystemCommand(
			command,
			label,
			-1,     // no timeout
			false,  // not verbose
			true    // only output last line
		);
		
		processes.put( output, ddProc );
		new Thread( ddProc ).start();
	}
	
	public Set<String> addedDevices () {
		return devices.addedDevices();
	}
	
	public Set<String> safeDevices () {
		Set<String> safe = new TreeSet<>();
		for (String device : addedDevices()) {
			if (
				Regex.exists( device, "^sd[a-z]$" ) ||
				Regex.exists( device, "^mmcblk[0-9]$" )
			) safe.add( device );
		}
		return safe;
	}
	
	public Map<String,String> safeDevicesInfo () {
		Map<String,String> info = new TreeMap<>();
		Table deviceInfo = devices.deviceInfo();
		Set<String> safe = safeDevices();
		for (List<String> row : deviceInfo.data()) {
			if (row.size()>1) {
				String device = row.get(0);
				if (safe.contains(device)) info.put( device, row.get(1) );
			}
		}
		return info;
	}
	
	public Tree safeDevicesTree () {
		Tree deviceTree = devices.deviceTree();
		if (deviceTree == null) return null;
		Set<String> safe = safeDevices();
		Tree safeDevices = new JSON();
		for (Tree device : deviceTree.get("blockdevices").branches()) {
			String name = device.get("name").value();
			if (safe.contains(name)) safeDevices.add( "/dev/"+name, device );
		}
		return safeDevices;
	}
	
	public boolean changed () {
		return devices.changed();
	}
	
	public Map<String,SystemCommand> processes () {
		return processes;
	}
	
	public String processOutput ( String device ) {
		SystemCommand proc = processes.get( "/dev/"+device );
		if (proc!=null) return proc.stderr().text();
		else return "";
	}
	
	public String processStatus ( String device ) {
		SystemCommand proc = processes.get( device );
		if (proc!=null) {
			if (proc.running()) {
				return "Writing";
			} else {
				if (proc.destroyed()>0 || proc.destroyedForcibly()>0) return "Canceled";
				else return "Complete";
			}
		} else {
			return "";
		}
	}
	
	public void cleanup () {
		for (Map.Entry<String,SystemCommand> entry : processes.entrySet()) {
			SystemCommand sc = entry.getValue();
			if (sc.finished()) processes.remove( entry.getKey() );
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
	
	public Table statusTable () {
		Table table = new SimpleTable();
		table.append( new String[]{ "Device", "Status", "Details" } );
		for (Map.Entry<String,SystemCommand> entry : processes.entrySet()) {
			SystemCommand sc = entry.getValue();
			String stderr = "";
			while (stderr.equals("")) {
				stderr = sc.stderr().text();
				try{Thread.sleep(1);} catch(Exception e) {e.printStackTrace();}
			}
			table.append( new String[]{ entry.getKey(), (!sc.finished() ? "Writing..." : "Complete"), stderr } );
		}
		return table;
	}
	
	public Tree statusTree () {
		Tree tree = safeDevicesTree();
		for (Map.Entry<String,SystemCommand> entry : processes.entrySet()) {
			String device = entry.getKey();
			SystemCommand process = entry.getValue();
			tree.auto( device )
				.add( "label", process.name() )
				.add( "status", processStatus( device ) )
				.add( "output", process.stderr().text() )
			;
		}
		return tree;
	}
	
	public static void testA ( String[] args ) throws Exception {
		DuplicateDisk dd = new DuplicateDisk();
		dd.diskToFile( "/dev/zero", "zeros_0.img.gz", "zeros 0" );
		Thread.sleep(1000);
		dd.diskToFile( "/dev/zero", "zeros_1.img.gz", "zeros 1" );
		for (int i=0; i<4; i++) {
			Thread.sleep(1000);
			System.out.println( dd.statusTable() );
		}
		dd.cancel( "/home/giw/zeros_0.img.gz" );
		dd.cancel( "/home/giw/zeros_1.img.gz" );
		
		System.out.println( "canceled first processes..." );
		Thread.sleep(500);
		
		dd.fileToDisk( "zeros_1.img.gz", "/dev/null", "null A" );
		dd.processes().get("/dev/null").timeout( 1000 ); // change the timeout to 1 sec
		for (int i=0; i<4; i++) {
			Thread.sleep(100);
			System.out.println( dd.statusTable() );
		}
		dd.fileToDisk( "zeros_1.img.gz", "/dev/null", "null B" ); // should throw an exception
	}
	
	public static void testB ( String[] args ) throws Exception {
		DuplicateDisk dd = new DuplicateDisk();
		String gzipFile = args[0];
		String output = "";
		
		Scanner scanner = new Scanner( System.in );
		
		while (true) {
			if (dd.changed()) {
				//System.out.println( dd.safeDevicesTree().serialize() );
				System.out.println( "Devices: "+dd.safeDevicesInfo()+"\nSelect device > " );
				String input = scanner.nextLine().trim();
				if (input.equals("q")) break;
				if (!input.equals("")) {
					try {
						dd.fileToDisk( gzipFile, input, input );
						System.out.println( "Cloning "+gzipFile+" --> "+input+"..." );
					} catch (Exception e) {
						System.err.println( e );
					}
				} else {
					System.out.println( "Canceled" );
				}
			}
			Thread.sleep(500);
			String nextOutput = dd.statusTree().serialize();
			//System.out.println(nextOutput);
			if (!output.equals(nextOutput)) {
				output = nextOutput;
				System.out.println( output );
			}
		}
	}
	
	public static void main ( String[] args ) throws Exception {
		//testA(args);
		testB(args);		
	}

}
