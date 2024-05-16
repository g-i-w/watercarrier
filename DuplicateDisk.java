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
	
	public void diskToFile ( String device, String file ) throws Exception {
		if (! devices.deviceList().contains(device)) throw new Exception( device+" not found" );
		dd( "/dev/"+device, file, "./watercarrier/diskToFile.sh" );
	}
	
	public void fileToDisk ( String file, String device ) throws Exception {
		beforeDiskWrite( device );
		dd( file, "/dev/"+device, "./watercarrier/fileToDisk.sh" );
	}
	
	public void fileToDiskCopyGz ( String file, String device ) throws Exception {
		beforeDiskWrite( device );
		dd( file, "/dev/"+device, "./watercarrier/fileToDiskCopyGz.sh" );
	}
	
	public void beforeDiskWrite ( String device ) throws Exception {
		Tree safeDevicesTree = safeDevicesTree();
		if (! safeDevicesTree.keys().contains(device)) throw new Exception( device+" is not a safe device" );
		// unmount if mounted
		Tree mountpoints = safeDevicesTree.get(device).get("mountpoints");
		if (mountpoints.branches().size()>0) {
			for (String mount : mountpoints.values()) {
				if (! mount.equals("null")) {
					System.out.println( "Unmounting "+mount+"..." );
					System.out.println(
						new SystemCommand( "umount "+mount ).output()
					);
				}
			}
		}
	}
	
	public void dd ( String in, String out ) throws Exception {
		dd( in, out, "./watercarrier/raw.sh" );
	}

	public void dd ( String in, String out, String script ) throws Exception {
		if (processes.keySet().contains(out) && !processes.get(out).done()) throw new Exception( out+" is busy" );
		
		String input = checkPath( in );
		String output = checkPath( out );
		
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
			if (safe.contains(name)) safeDevices.add( name, device );
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
	
	public int processStatus ( String device ) {
		SystemCommand proc = processes.get( "/dev/"+device );
		if (proc!=null) {
			if (proc.running()) {
				return 1;
			} else {
				if (proc.destroyed()>0 || proc.destroyedForcibly()>0) return 2;
				else return 3;
			}
		} else {
			return 0;
		}
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
	
	public static void testing ( String[] args ) throws Exception {
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
	
	public static void main ( String[] args ) throws Exception {
		//testing(args);
		
		DuplicateDisk dd = new DuplicateDisk();
		String gzipFile = args[0];
		String output = "";
		
		
		Scanner scanner = new Scanner( System.in );
		
		while (true) {
			if (dd.changed()) {
				System.out.println( dd.safeDevicesTree().serialize() );
				System.out.println( "Devices: "+dd.safeDevicesInfo()+"\nSelect device > " );
				String input = scanner.nextLine().trim();
				if (input.equals("q")) break;
				if (!input.equals("")) {
					try {
						dd.fileToDisk( gzipFile, input );
						System.out.println( "Cloning "+gzipFile+" --> "+input+"..." );
					} catch (Exception e) {
						System.err.println( e );
					}
				} else {
					System.out.println( "Canceled" );
				}
			}
			Thread.sleep(500);
			String nextOutput = dd.status( new SimpleTable() ).toString();
			if (!output.equals(nextOutput)) {
				output = nextOutput;
				System.out.println( output );
			}
		}
	}

}
