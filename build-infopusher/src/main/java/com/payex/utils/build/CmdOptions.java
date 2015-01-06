package com.payex.utils.build;

import org.kohsuke.args4j.Option;

public class CmdOptions {
	@Option(name="-je",usage="Jenkins URL")
	String jenkinsUrl;
	@Option(name="-ju",usage="Jenkins username")
	String jenkinsUser;
	@Option(name="-jp",usage="Jenkins password")
	String jenkinsPass;
	@Option(name="-pe",usage="Publisher URL")
	String publisherUrl;
	@Option(name="-i",usage="Refresh interval")
	Long refreshInterval;
	@Option(name="-f",usage="Filter path location")
	String filterFilePath;
	/**
	 * This is to work-around a bug in jenkins client, when re-using causes to connection overload
	 */
	@Option(name="-r",usage="Re-use jenkins client")
	Boolean reuseJenkinsClient;
}
