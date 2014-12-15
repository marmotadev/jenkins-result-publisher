package com.payex.utils.build;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;
import java.util.regex.PatternSyntaxException;

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

import com.google.common.base.Predicate;
import com.google.common.collect.Maps;
import com.offbytwo.jenkins.JenkinsServer;
import com.offbytwo.jenkins.model.Build;
import com.offbytwo.jenkins.model.BuildResult;
import com.offbytwo.jenkins.model.BuildWithDetails;
import com.offbytwo.jenkins.model.Job;
import com.offbytwo.jenkins.model.JobWithDetails;
import com.payex.utils.build.filters.FilterReader;
import com.payex.utils.build.filters.jaxb.Action;
import com.payex.utils.build.filters.jaxb.Filter;
import com.payex.utils.build.filters.jaxb.FilterSet;
import com.payex.utils.build.filters.jaxb.Test;

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
	private String filterFile;

	public JenkinsReader(String jenkinsUrl, String jenkinsUser,
			String jenkinsPass, String pushServiceUrl) {
		this.jenkinsUrl = jenkinsUrl;
		this.user = jenkinsUser;
		this.pass = jenkinsPass;
		publishServiceUrl = pushServiceUrl;
	}

	public JenkinsReader(String jenkinsUrl, String jenkinsUser,
			String jenkinsPass, String pushServiceUrl, String filterFile) {
		this.jenkinsUrl = jenkinsUrl;
		this.user = jenkinsUser;
		this.pass = jenkinsPass;
		this.publishServiceUrl = pushServiceUrl;
		this.filterFile = filterFile;
	}

	public static void main(String[] args) {
		CmdOptions opts = new CmdOptions();
		opts.jenkinsUrl = "http://localhost:8080/";
		opts.publisherUrl = "http://192.168.3.83/BuildWatchVAS/DataService.svc";
		opts.jenkinsUser = "";
		opts.jenkinsPass = "";
		opts.filterFilePath = "filters.xml";

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
					opts.jenkinsUser, opts.jenkinsPass, opts.publisherUrl,
					opts.filterFilePath);
			jr.init();
			Timer tm = new Timer();
			log.info("Pushing info every {} seconds", PUSH_INTERVAL_SECONDS);
			tm.schedule(new TimerTask() {
				@Override
				public void run() {
					try {
						log.trace(new Date() + ": Refreshing build statuses...");
						jr.getAndPublish();
					}
					catch (Exception e) {
						e.printStackTrace();
					}
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
			PushFinishedBuildsRequest r = initRequestObject(of);

			Map<String, Job> fjobs = filterJobs(jobs);
			for (Entry<String, Job> es : fjobs.entrySet()) {
				Job job = es.getValue();
				JobWithDetails jd = job.details();

				if (!jd.getBuilds().isEmpty()) {
					addRunningJobsToQueue(of, r, jd);
					addFinishedJobIfAnyExist(of, r, jd); 

				} else {
					if (filterOutNewBuilds)
						continue;
					addNewJobToList(of, r, jd);
				}
			}

			return r;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private PushFinishedBuildsRequest initRequestObject(ObjectFactory of) {
		PushFinishedBuildsRequest r = of.createPushFinishedBuildsRequest();
		r.setBuildInfo(of.createArrayOfFinishedBuildInfo());
		r.setDataSourceId(1);
		r.setQueuedBuilds(of.createArrayOfQueuedBuildInfo());
		return r;
	}

	private String addNewJobToList(ObjectFactory of,
			PushFinishedBuildsRequest r, JobWithDetails jd) throws IOException {
		String buildName = jd.getDisplayName();
		addQueuedBuild(of, r, buildName, null);
		return buildName;
	}

	private void addRunningJobsToQueue(ObjectFactory of,
			PushFinishedBuildsRequest r, JobWithDetails jd) throws IOException {
		List<Build> blist = jd.getBuilds();
		for (Build bip: blist) {
			if (bip.details().isBuilding()) {
				String buildName = formatBuildName(jd, bip);
				addQueuedBuild(of, r, buildName, bip);
			}
		}
	}
	
	private Build findLastFinishedJob(JobWithDetails jd) throws IOException {
		List<Build> blist = jd.getBuilds();
		System.out.println("---");
		for (Build bip: blist) { // they are ordered in descendant order
			if (!bip.details().isBuilding()) {
				// this is finished build, but I'm not interested in aborted builds, since they don't tell anything usefull
				if (bip.details().getResult().equals(BuildResult.ABORTED) || bip.details().getResult().equals(BuildResult.UNKNOWN))
					continue;
				return bip; // then next one is the one we need
			}
		}
		return null;
	}

	private void addFinishedJobIfAnyExist(ObjectFactory of,
			PushFinishedBuildsRequest r, JobWithDetails jd) throws IOException {
		Build lastBuild = null;
		BuildResult jenkinsLastBuildStatus = null;

		String buildName = null;

		lastBuild = findLastFinishedJob(jd);
		if (lastBuild == null)
			return;

		jenkinsLastBuildStatus = lastBuild.details().getResult();
		buildName = formatBuildName(jd, lastBuild);

		addFinishedBuild(of, r, buildName, jenkinsLastBuildStatus,
				lastBuild.details());
	}

	private Map<String, Job> filterJobs(Map<String, Job> jobs) {
		FilterReader fr = new FilterReader();
		final FilterSet filters = fr.readFilters(this.filterFile);
		Predicate<Map.Entry<String, Job>> jobFilter = new Predicate<Map.Entry<String, Job>>() {
			public boolean apply(Entry<String, Job> e) {
				String jobName;
				try {
					jobName = e.getValue().details().getDisplayName();
				} catch (IOException e1) {
					log.error(e1.getMessage(), e1);
					log.error("WIll substitute job name");
					jobName = e.getValue().getName();
				}
				log.trace("-> Checking if '" + jobName + "' should be filtered out");
				for (Filter f : filters.getFilters()) {
					if (f == null) {
						log.info("Filter is null, skipping");
						continue;
					}
					log.trace("-->Will check filter " + f.getName());
					if (f.getTests() != null) {
						for (Test t : f.getTests()) {
							try {
								log.trace("--->Rule: '" + t.getRegex()
										+ "', action: " + t.getAction());
								if (t.getAction().equals(Action.hide)) {
									log.trace("Action is hide, checking for match");
									if (t.getAction().equals(Action.hide)
											&& jobName.matches(t.getRegex())) {
										log.debug("Hiding job: " + jobName
												+ "(" + t + " applies)");
										return false;
									}
									else
										log.trace("Did not match a '" + jobName + "'");
								}
							} catch (PatternSyntaxException ex) {
								log.error("Invalid regexp in config: "
										+ t.getRegex());
							}
						}
					}
				}
				return true;
			}

		};

		Map<String, Job> filtered = Maps.filterEntries(jobs, jobFilter);

		return filtered;
	}

	private String formatBuildName(JobWithDetails jd, Build specificJob) throws IOException {
		String buildName = String.format("%s#%d (%s)", jd.getDisplayName(),
				specificJob.details().getNumber(), specificJob.details()
						.getResult());
		return buildName;
	}

	private void addQueuedBuild(ObjectFactory of, PushFinishedBuildsRequest r,
			String buildName, Build lastBuild) throws IOException {
		QueuedBuildInfo e = of.createQueuedBuildInfo();
		e.setBuildName(buildName);
		long ts = 0;
		if (lastBuild != null)
			ts = lastBuild.details().getTimestamp();
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
