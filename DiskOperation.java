package watercarrier;

import java.util.*;
import paddle.*;
import creek.*;

public class DiskOperation implements Comparable {

	private DuplicateDisk dd;
	private DiskOperation parent;
	
	private Set<DiskOperation> children;
	
	private String device;
	private String status;
	private String label;
	private String output;
	private double size;
	private String sizeUnit;
	private double used;
	private String usedUnit;
	private int percent;

	public DiskOperation ( String device ) {
		this.device = device;
		refreshStats( data() );
	}

	public DiskOperation ( Tree deviceData, DuplicateDisk dd, DiskOperation parent ) {
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
				//System.out.println( "DiskOperation: "+device+" "+usedStr );
				if (!usedStr.equals("") && !usedStr.equals("null")) {
					used = Double.valueOf( usedStr.substring(0, usedStr.length()-1) ).doubleValue();
					usedUnit = usedStr.substring( usedStr.length()-1, usedStr.length() );
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
				DiskOperation childOp = new DiskOperation( child, dd, this );
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
	
	public DiskOperation parent () { return parent; }
	
	public boolean isChild () { return (parent!=null); }
	
	public Set<DiskOperation> children () { return children; }
	
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
	
	public String usedUnit () {
		return usedUnit;
	}

	public int percent () {
		return percent;
	}
	
	public String sizeGiB () {
		return String.valueOf( size );
	}
	
	public String sizeGB () {
		return String.format("%.1f", (size*1.074));
	}
	
	public String sizeb () {
		return String.valueOf( size*Math.pow(1024,3) );
	}
	
	public String toString () {
		return "\n"+device()+", "+sizeGiB()+" GiB, "+status()+", "+label()+", "+output();
	}
	
	public int compareTo( Object op ) {
		if (op instanceof DiskOperation) return device().compareTo( ((DiskOperation)op).device() );
		else return device().compareTo( op.toString() );
	}

}
