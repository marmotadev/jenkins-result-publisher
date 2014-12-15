package com.payex.utils.build;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.datacontract.schemas._2004._07.buildwatch.FinishedBuildInfo;
import org.datacontract.schemas._2004._07.buildwatch.ObjectFactory;
import org.datacontract.schemas._2004._07.buildwatch.PushFinishedBuildsRequest;
import org.datacontract.schemas._2004._07.buildwatch.QueuedBuildInfo;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;

import com.offbytwo.jenkins.JenkinsServer;
import com.offbytwo.jenkins.model.Build;
import com.offbytwo.jenkins.model.BuildResult;
import com.offbytwo.jenkins.model.BuildWithDetails;
import com.offbytwo.jenkins.model.Job;
import com.offbytwo.jenkins.model.JobWithDetails;

public class JenkinsReader {
	private static final int PUSH_INTERVAL_SECONDS = 5;
	private static final Logger log = LogManager.getLogger(JenkinsReader.class);
	private static final String WATCHER_STATUS_OK = "OK";
	private static final String WATCHER_STATUS_FAIL = "FAIL";
	private String jenkinsUrl;
	private String user;
	private String pass;
	private JenkinsServer jenkins;
	private BuildResultPusher publisher;
	private String publishServiceUrl;

	public JenkinsReader(String jenkinsUrl, String jenkinsUser,
			String jenkinsPass, String pushServiceUrl) {
		this.jenkinsUrl = jenkinsUrl;
		this.user = jenkinsUser;
		this.pass = jenkinsPass;
		publishServiceUrl = pushServiceUrl;
	}

	public static void main(String[] args) {
		CmdOptions opts = new CmdOptions();
		opts.jenkinsUrl = "http://localhost:8080/";
		opts.publisherUrl = "http://192.168.3.83/BuildWatchVAS/DataService.svc";
		opts.jenkinsUser = "";
		opts.jenkinsPass = "";

		CmdLineParser parser = new CmdLineParser(opts);
		try {
			parser.parseArgument(args);

			log.info("Using:");
			log.info("jenkins URL:" + opts.jenkinsUrl);
			log.info("jenkins user:" + opts.jenkinsUser);
			log.info("jenkins pass:" + opts.jenkinsPass);
			log.info("publisher endpoint URL:" + opts.publisherUrl);
			String jenkinsUrl = opts.jenkinsUrl;
			final JenkinsReader jr = new JenkinsReader(jenkinsUrl,
					opts.jenkinsUser, opts.jenkinsPass, opts.publisherUrl);
			jr.init();
			Timer tm = new Timer();
			log.info("Pushing info every {} seconds", PUSH_INTERVAL_SECONDS);
			tm.schedule(new TimerTask() {
				@Override
				public void run() {
					log.trace(new Date() + ": Refreshing build statuses...");
					jr.getAndPublish();
				}
			}, new Date(), TimeUnit.SECONDS.toMillis(PUSH_INTERVAL_SECONDS));
		} catch (CmdLineException e) {
			System.err.println(e.getMessage());
			parser.printUsage(System.err);
		}

	}

	private static XMLGregorianCalendar buildCal(long ts) {
		XMLGregorianCalendar cal;
		try {
			Calendar ci = Calendar.getInstance();
			ci.setTimeInMillis(ts);
			cal = DatatypeFactory.newInstance().newXMLGregorianCalendar(
					(GregorianCalendar) ci);
		} catch (DatatypeConfigurationException e1) {
			throw new RuntimeException(e1);
		}
		return cal;
	}

	private PushFinishedBuildsRequest buildResultsRequestObject(
			Map<String, Job> jobs) {
		try {
 
			boolean filterOutNewBuilds = true;

			ObjectFactory of = new ObjectFactory();
			PushFinishedBuildsRequest r = of.createPushFinishedBuildsRequest();
			r.setBuildInfo(of.createArrayOfFinishedBuildInfo());
			r.setDataSourceId(1);
			r.setQueuedBuilds(of.createArrayOfQueuedBuildInfo());

			for (Entry<String, Job> es : jobs.entrySet()) {
				Job job = es.getValue();
				JobWithDetails jd = job.details();

				String buildName = jd.getDisplayName();

				if (!jd.getBuilds().isEmpty()) {

					Build lastBuild = jd.getLastBuild();
					buildName = formatBuildName(jd);
					BuildResult jenkinsLastBuildStatus = lastBuild.details()
							.getResult();

					if (isFinishedStatus(jenkinsLastBuildStatus)) {
						addFinishedBuild(of, r, buildName,
								jenkinsLastBuildStatus, lastBuild.details());
					} else { // in progress
						addQueuedBuild(of, r, buildName, lastBuild);
					}
				} else {
					if (filterOutNewBuilds)
						continue;
				}
			}

			return r;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private String formatBuildName(JobWithDetails jd) throws IOException {
		Build lastBuild = jd.getLastBuild();
		String buildName = String.format("%s#%d (%s)", jd
				.getDisplayName(), lastBuild.details().getNumber(), lastBuild
				.details().getResult()); 
		return buildName;
	}

	private void addQueuedBuild(ObjectFactory of, PushFinishedBuildsRequest r,
			String buildName, Build lastBuild) throws IOException {
		QueuedBuildInfo e = of.createQueuedBuildInfo();
		e.setBuildName(buildName);
		long ts = lastBuild.details().getTimestamp();
		XMLGregorianCalendar cal = buildCal(ts);
		e.setQueueTime(cal);
		r.getQueuedBuilds().getQueuedBuildInfo().add(e);
	}

	private void addFinishedBuild(ObjectFactory of,
			PushFinishedBuildsRequest r, String buildName,
			BuildResult jenkinsLastBuildStatus, BuildWithDetails lastBuild) {
		FinishedBuildInfo e = of.createFinishedBuildInfo();

		String result = convertFinishedJenkinsStatusToWatcherStatus(jenkinsLastBuildStatus);
		long ts = 0;
		try {
			ts = lastBuild.details().getTimestamp();
		} catch (IOException e1) {
			throw new RuntimeException(e1);
		}
		//
		e.setBuildName(buildName);
		e.setResult(result);
		// e.setUserAction("Green/White");
		e.setUserName("[unknown]");

		XMLGregorianCalendar cal = buildCal(ts);

		e.setTimeStamp(cal);

		r.getBuildInfo().getFinishedBuildInfo().add(e);
	}

	private boolean isFinishedStatus(BuildResult jenkinsLastBuildStatus) {

		if (jenkinsLastBuildStatus == null)
			jenkinsLastBuildStatus = BuildResult.UNKNOWN;
		switch (jenkinsLastBuildStatus) {

		case FAILURE:
		case SUCCESS:
		case UNSTABLE:
		case ABORTED:
			return true;

		case BUILDING:
		case REBUILDING:
		case UNKNOWN:
		default:
			return false;
		}

	}

	private String convertFinishedJenkinsStatusToWatcherStatus(
			BuildResult jenkinsLastBuildStatus) {
		if (!isFinishedStatus(jenkinsLastBuildStatus))
			throw new IllegalArgumentException(
					"Illegal status. Must be finished, but is not:"
							+ jenkinsLastBuildStatus);
		switch (jenkinsLastBuildStatus) {
		case ABORTED:
		case FAILURE:
		case UNSTABLE:
			return WATCHER_STATUS_FAIL;

		case SUCCESS:
			return WATCHER_STATUS_OK;
		default:
			throw new RuntimeException("Unexpected status:"
					+ jenkinsLastBuildStatus);
		}
	}

	// private PushFinishedBuildsRequest genResultsObject2(List<Job> jobs) {
	// ObjectFactory of = new ObjectFactory();
	// PushFinishedBuildsRequest r = of.createPushFinishedBuildsRequest();
	//
	// r.setDataSourceId(1);
	// r.setQueuedBuilds(of.createArrayOfQueuedBuildInfo());
	// FinishedBuildInfo e = of.createFinishedBuildInfo();
	//
	// e.setBuildInstance("ABR");
	// e.setBuildName("BuildName");
	// e.setResult(WATCHER_STATUS_OK);
	// e.setUserAction("Green/White");
	// e.setUserName("cs_bq_kazkas");
	// XMLGregorianCalendar cal = buildCal(System.currentTimeMillis());
	//
	// e.setTimeStamp(cal);
	//
	// r.setBuildInfo(of.createArrayOfFinishedBuildInfo());
	// r.getBuildInfo().getFinishedBuildInfo().add(e);
	// return r;
	// }

	private void getAndPublish() {
		try {
			log.trace("Retrieving info from jenkins server");

			Map<String, Job> jobs = jenkins.getJobs();

			PushFinishedBuildsRequest req = buildResultsRequestObject(jobs);
			log.trace("Pushing results to monitor");

			publisher.pushResults(req);

		} catch (IOException e) {
			throw new RuntimeException(e);
		}

	}

	public void init() {
		try {
			jenkins = new JenkinsServer(new URI(jenkinsUrl), user, pass);
			publisher = new BuildResultPusher(publishServiceUrl);
			publisher.init();
			log.info("Initialized jenkins sever");
		} catch (URISyntaxException e) {
			throw new RuntimeException(e);
		}
	}

}
