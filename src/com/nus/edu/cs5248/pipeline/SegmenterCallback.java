package com.nus.edu.cs5248.pipeline;

public interface SegmenterCallback {

	public void onSegmentCreated(String segmentFilePath, boolean isFinalSegment);
	
}
