package com.nus.edu.cs5248.pipeline;

import java.util.Set;

public interface ListVideoStreamletsCallback {

	public void videoStreamletsListRetrieved(final int result, final int videoId, final Set<String> streamlets);
	
}
