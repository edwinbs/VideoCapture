package com.nus.edu.cs5248.pipeline;

public interface VideoUploaderCallback {
	
	public void createVideoDidFinish(int result, int videoId);
	
	public void createVideoStreamletDidFinish(int result, int videoId, int streamletId, boolean isFinalStreamlet);
	
}
