package com.md;


public class GlobalStats {
	private static GlobalStats instance = null;

	protected GlobalStats() {
			
	}
	
	public static GlobalStats getInstance() {
		if (instance == null) {
			instance = new GlobalStats();
		}
		return instance;
	}
	
	// TODO calculate this.
	private float averageEasiness = 2.5f;

	public float getAverageEasiness() {
		return averageEasiness;
	}

	public void setAverageEasiness(float averageEasiness) {
		this.averageEasiness = averageEasiness;
	}
	
}
