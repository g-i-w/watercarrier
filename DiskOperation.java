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
	private double gib;

	public DiskOperation ( Tree deviceData, DuplicateDisk dd, DiskOperation parent ) {
		this.dd = dd;
		this.parent = parent;
		refresh( deviceData );
	}
	
	public DiskOperation refresh ( Tree deviceData ) {
		children = new TreeSet<>();
		device = deviceData.get("name").value();
		status = "";
		label = "";
		output = "";
		gib = 0.0;

		if (deviceData.keys().contains("size")) {
			String size = deviceData.get("size").value();
			if (!size.equals("")) gib = Double.valueOf( size.substring(0, size.length()-1) ).doubleValue();
		}
		
		SystemCommand proc = dd.processes().get( "/dev/"+device );
		
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
		
		return this;
	}
	
	public DiskOperation parent () { return parent; }
	
	public boolean isChild () { return (parent!=null); }
	
	public Set<DiskOperation> children () { return children; }
	
	public String device () { return device; }
	
	public String status () { return status; }
	
	public String label () { return label; }
	
	public String output () { return output; }
	
	public double gib () {
		return gib;
	}
	
	public String sizeGiB () {
		return String.valueOf( gib );
	}
	
	public String sizeGB () {
		return String.format("%.1f", (gib*1.074));
	}
	
	public String sizeb () {
		return String.valueOf( gib*Math.pow(1024,3) );
	}
	
	public String toString () {
		return "\n"+device()+", "+sizeGiB()+" GiB, "+status()+", "+label()+", "+output();
	}
	
	public int compareTo( Object op ) {
		if (op instanceof DiskOperation) return device().compareTo( ((DiskOperation)op).device() );
		else return device().compareTo( op.toString() );
	}

}
