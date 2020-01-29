package com.datorama.timbermill.pipe;

import com.datorama.timbermill.common.Constants;

public class TimbermillServerOutputPipeBuilder {
	String timbermillServerUrl;
	int maxEventsBatchSize = Constants.TWO_MB;
	long maxSecondsBeforeBatchTimeout = 3;


	public TimbermillServerOutputPipeBuilder timbermillServerUrl(String timbermillServerUrl) {
		this.timbermillServerUrl = timbermillServerUrl;
		return this;
	}

	public TimbermillServerOutputPipeBuilder maxEventsBatchSize(int maxEventsBatchSize) {
		this.maxEventsBatchSize = maxEventsBatchSize;
		return this;
	}

	public TimbermillServerOutputPipeBuilder maxSecondsBeforeBatchTimeout(long maxSecondsBeforeBatchTimeout) {
		this.maxSecondsBeforeBatchTimeout = maxSecondsBeforeBatchTimeout;
		return this;
	}

	public TimbermillServerOutputPipe build() {
		return new TimbermillServerOutputPipe(this);
	}

}
