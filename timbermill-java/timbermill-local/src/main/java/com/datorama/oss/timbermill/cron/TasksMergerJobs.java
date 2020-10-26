package com.datorama.oss.timbermill.cron;

import java.util.Random;
import java.util.UUID;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datorama.oss.timbermill.ElasticsearchClient;
import com.datorama.oss.timbermill.common.ElasticsearchUtil;
import com.datorama.oss.timbermill.common.KamonConstants;

import kamon.metric.Timer;
import static com.datorama.oss.timbermill.TaskIndexer.FLOW_ID_LOG;

@DisallowConcurrentExecution
public class TasksMergerJobs implements Job {

	private static final Logger LOG = LoggerFactory.getLogger(TasksMergerJobs.class);

	private final Random rand = new Random();

	@Override public void execute(JobExecutionContext context) {

		JobDataMap jobDataMap = context.getJobDetail().getJobDataMap();
		ElasticsearchClient client = (ElasticsearchClient) jobDataMap.get(ElasticsearchUtil.CLIENT);
		String flowId = "Partials Tasks Merger Job - " + UUID.randomUUID().toString();
		LOG.info(FLOW_ID_LOG + " Partials Tasks Merger Job started.", flowId);
		Timer.Started started = KamonConstants.PARTIALS_JOB_LATENCY.withoutTags().start();
		int secondsToWait = rand.nextInt(10);
		try {
			Thread.sleep(secondsToWait * 1000);
		} catch (InterruptedException ignored) {}
		client.migrateTasksToNewIndex(flowId);
		LOG.info(FLOW_ID_LOG + " Partials Tasks Merger Job ended.", flowId);
		started.stop();
	}

}
