package watercarrier;

import java.util.*;
import paddle.*;
import creek.*;

public class DiskData implements Comparable {

	private DuplicateDisk dd;
	private DiskData parent;
	
	private Set<DiskData> children;
	
	private String device;
	private String status;
	private String label;
	private String output;
	private double size;
	private String sizeUnit;
	private double used;
	private String usedUnit;
	private double avail;
	private String availUnit;
	private int percent;

	public DiskData ( String device ) {
		this.device = device;
		refreshStats( data() );
	}

	public DiskData ( Tree deviceData, DuplicateDisk dd, DiskData parent ) {
		device = "/dev/"+deviceData.get("name").value();
		this.dd = dd;
		this.parent = parent;
		refreshStats( deviceData );
		refreshProc( deviceData );
	}
	
	
	public void refreshStats ( Tree deviceData ) {
		size = 0.0;
		used = 0.0;
		percent = 0;
		
		if (deviceData==null) return;

		try {
			if (deviceData.keys().contains("size")) {
				String sizeStr = deviceData.get("size").value();
				if (!sizeStr.equals("") && !sizeStr.equals("null")) {
					size = Double.valueOf( sizeStr.substring(0, sizeStr.length()-1) ).doubleValue();
					sizeUnit = sizeStr.substring( sizeStr.length()-1, sizeStr.length() );
				}
			}
			
			if (deviceData.keys().contains("fsuse%")) {
				String percentStr = deviceData.get("fsuse%").value();
				if (!percentStr.equals("") && !percentStr.equals("null")) {
					percent = Integer.valueOf( percentStr.substring(0, percentStr.length()-1) ).intValue();
				}
			}
			
			if (deviceData.keys().contains("fsused")) {
				String usedStr = deviceData.get("fsused").value();
				if (!usedStr.equals("") && !usedStr.equals("null")) {
					used = Double.valueOf( usedStr.substring(0, usedStr.length()-1) ).doubleValue();
					usedUnit = usedStr.substring( usedStr.length()-1, usedStr.length() );
				}
			}
			
			if (deviceData.keys().contains("fsavail")) {
				String availStr = deviceData.get("fsavail").value();
				if (!availStr.equals("") && !availStr.equals("null")) {
					avail = Double.valueOf( availStr.substring(0, availStr.length()-1) ).doubleValue();
					availUnit = availStr.substring( availStr.length()-1, availStr.length() );
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public void refreshProc ( Tree deviceData ) {
		children = new TreeSet<>();
		status = "";
		label = "";
		output = "";
		
		if (deviceData==null) return;
		
		SystemCommand proc = dd.processes().get( device );
		
		if (proc!=null) {
			label = proc.name();
			String out = proc.stdout().text();
			String err = proc.stderr().text();
			output = ( !out.equals("") ? out : err );
			if (proc.running()) status = "Writing";
			else if (proc.destroyed()>0 || proc.destroyedForcibly()>0) status = "Canceled";
			else status = "Complete";
		}
		
		if (deviceData.keys().contains("children")) {
			for (Tree child : deviceData.get("children").branches()) {
				DiskData childOp = new DiskData( child, dd, this );
				children.add( childOp );
				if (childOp.status().equals("Writing")) status = "Writing";
			}
		}
	}
	
	public Tree data () {
		try {
			return new JSON( (new SystemCommand( "lsblk --json --output name,path,size,mountpoints,fsavail,fsused,fsuse% "+device )).output() )
				.get("blockdevices")
				.get("0")
			;
		} catch (Exception e) {
			//e.printStackTrace();
			return null;
		}
	}
	
	public DiskData parent () { return parent; }
	
	public boolean isChild () { return (parent!=null); }
	
	public Set<DiskData> children () { return children; }
	
	public String device () { return device; }
	
	public String status () { return status; }
	
	public String label () { return label; }
	
	public String output () { return output; }
	
	public double size () {
		return size;
	}
	
	public String sizeUnit () {
		return sizeUnit;
	}

	public double used () {
		return used;
	}
	
	public String usedGB () {
		if (usedUnit.equals("G")) return String.format("%.1f", used*1.074)+"GB";
		if (usedUnit.equals("M")) return String.format("%.3f", used*1.049e-3)+"GB";
		if (usedUnit.toLowerCase().equals("k")) return String.format("%.6f", used*1.024e-6)+"GB";
		else return String.valueOf( used )+"?iB";
	}
	
	public String usedUnit () {
		return usedUnit;
	}

	public double avail () {
		return avail;
	}
	
	public String availGB () {
		if (availUnit.equals("G")) return String.format("%.1f", avail*1.074)+"GB";
		if (availUnit.equals("M")) return String.format("%.3f", avail*1.049e-3)+"GB";
		if (availUnit.toLowerCase().equals("k")) return String.format("%.6f", avail*1.024e-6)+"GB";
		else return String.valueOf( avail )+"?iB";
	}
	
	public String availUnit () {
		return availUnit;
	}

	public int percent () {
		return percent;
	}
	
	public String sizeGiB () {
		if (sizeUnit.equals("G")) return String.format("%.1f", size )+"GiB";
		if (sizeUnit.equals("M")) return String.format("%.3f", size/1024 )+"GiB";
		if (sizeUnit.equals("k")) return String.format("%.6f", size/1.049e6 )+"GiB";
		else return String.valueOf( size )+"?iB";
	}
	
	public String sizeGB () {
		if (sizeUnit.equals("G")) return String.format("%.1f", size*1.074)+"GB";
		if (sizeUnit.equals("M")) return String.format("%.3f", size*1.049e-3)+"GB";
		if (sizeUnit.toLowerCase().equals("k")) return String.format("%.6f", size*1.024e-6)+"GB";
		else return String.valueOf( size )+"?iB";
	}
	
	public String sizeb () {
		return String.valueOf( size*Math.pow(1024,3) );
	}
	
	public String toString () {
		return "\n"+device()+", "+sizeGiB()+" GiB, "+status()+", "+label()+", "+output();
	}
	
	public int compareTo( Object op ) {
		if (op instanceof DiskData) return device().compareTo( ((DiskData)op).device() );
		else return device().compareTo( op.toString() );
	}

}
