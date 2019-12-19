package com.md.provider;

public class MemoreasyType {
	
	public MemoreasyType(int _id, String qfile, String afile, String baseId,
			int passes, int fails, double lastTesting, int points) {
		super();
		this._id = _id;
		this.qfile = qfile;
		this.afile = afile;
		this.baseId = baseId;
		this.passes = passes;
		this.fails = fails;
		this.lastTesting = lastTesting;
		this.points = points;
	}

	@Override
	public String toString() {
		return "MemoreasyType [_id=" + _id + ", qfile=" + qfile + ", afile="
				+ afile + ", baseId=" + baseId + ", passes=" + passes
				+ ", fails=" + fails + ", lastTesting=" + lastTesting
				+ ", points=" + points + "]";
	}

	public int get_id() {
		return _id;
	}

	public void set_id(int _id) {
		this._id = _id;
	}

	public String getQfile() {
		return qfile;
	}

	public void setQfile(String qfile) {
		this.qfile = qfile;
	}

	public String getAfile() {
		return afile;
	}

	public void setAfile(String afile) {
		this.afile = afile;
	}

	public String getBaseId() {
		return baseId;
	}

	public void setBaseId(String baseId) {
		this.baseId = baseId;
	}

	public int getPasses() {
		return passes;
	}

	public void setPasses(int passes) {
		this.passes = passes;
	}

	public int getFails() {
		return fails;
	}

	public void setFails(int fails) {
		this.fails = fails;
	}

	public double getLastTesting() {
		return lastTesting;
	}

	public void setLastTesting(double lastTesting) {
		this.lastTesting = lastTesting;
	}

	public int getPoints() {
		return points;
	}

	public void setPoints(int points) {
		this.points = points;
	}

	public int _id;
	
	public String qfile;
	public String afile;
	
	public String baseId;

	public int passes;
	
	public int fails;
	
	public double lastTesting;
	
	public int points;
	
	
	
	
	
	
	
	

}
